package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.form.FormSystemAPI;
import at.koopro.wizardsandbeasts.heritage.ConditionOrigin;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.heritage.werewolf.WerewolfRules;
import at.koopro.wizardsandbeasts.network.SpellNetworkGuards;
import at.koopro.wizardsandbeasts.network.heritage.HeritageDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.skill.SkillTreeId;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.stats.PlayerStat;
import at.koopro.wizardsandbeasts.stats.PlayerStatsAPI;
import at.koopro.wizardsandbeasts.stats.PlayerStatsData;
import at.koopro.wizardsandbeasts.stats.PowerBandTable;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * What committing a heritage does to a real player on a real server.
 *
 * <p>These are game tests rather than unit tests because every failure mode being covered lives in state
 * only a live {@code ServerPlayer} has: an attachment, an attribute modifier and a form id, written by four
 * subsystems that only meet inside {@code HeritageAPI.commit}. The unit suites next door
 * ({@code HeritageCommitReachabilityTest}, {@code HeritageRerollTest}) pin the data and the arithmetic; a
 * pure test cannot see whether the arithmetic was ever actually run against a player.
 *
 * <p>The bug this class exists to keep fixed: the gate, {@code /wandb player heritage set} and the heritage
 * dev kit each wrote a different subset of the same change. Committing was eight steps; the admin route was
 * four, so switching someone to a new heritage left them with the old body, the old POWER band and the old
 * ability grants — and every one of those failures is silent. There is now one routine, and these scenarios
 * assert its whole effect rather than the parts any single caller remembered.
 */
public final class HeritageCommitTests {

    /** The attribute ids {@code HeritageAPI.applyStats} owns; {@code clear} has to take them all back off. */
    private static final Identifier TYPE_HEALTH_ID =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "type_health");
    private static final Identifier TYPE_ARMOR_ID =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "type_armor");

    /** A lineage whose band is narrow, closed at both ends, and cannot roll a prodigy. */
    private static final HeritageVariant SQUIB = HeritageVariant.byId("squib");
    /** A lineage with a visibly different body, so a form that failed to follow is detectable. */
    private static final HeritageVariant FULL_GIANT = HeritageVariant.byId("full_giant");

    private HeritageCommitTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("heritage_commit_builds_a_whole_character",
                "heritage: committing rolls Power, sets the body and applies the attributes",
                HeritageCommitTests::commitBuildsAWholeCharacter);
        tests.add("heritage_change_rerolls_power_and_keeps_training",
                "heritage: changing lineage re-rolls Power on the new band without wiping training",
                HeritageCommitTests::changeRerollsPowerAndKeepsTraining);
        tests.add("heritage_clear_takes_everything_back_off",
                "heritage: clearing removes the body, the attributes and the Power roll",
                HeritageCommitTests::clearTakesEverythingBackOff);
        tests.add("heritage_condition_keeps_the_wizard_underneath",
                "heritage: a bite changes the body and the calendar, not who the wizard is",
                HeritageCommitTests::conditionKeepsTheWizardUnderneath);
        tests.add("heritage_condition_reaches_the_client",
                "heritage: a condition is on the wire, not only on the server",
                HeritageCommitTests::conditionReachesTheClient);
        tests.add("heritage_obscurus_seals_the_wand_at_the_packet_gate",
                "heritage: an Obscurus refuses wand packets and a cure gives them back",
                HeritageCommitTests::obscurusSealsTheWandAtThePacketGate);
    }

    // ── scenarios ───────────────────────────────────────────────────────────────────────────────

    private static void commitBuildsAWholeCharacter(GameTestHelper helper) {
        ServerPlayer player = newWizard(helper);
        try {
            WizardTestSupport.check(helper, SQUIB != null, () -> "the 'squib' lineage is gone");
            HeritageAPI.commit(player, Heritage.WIZARDKIND, SQUIB);

            WizardTestSupport.check(helper,
                    HeritageAPI.getPlayerHeritageVariant(player) == SQUIB,
                    () -> "commit did not record the lineage");
            WizardTestSupport.check(helper, HeritageAPI.isLocked(player),
                    () -> "commit left the heritage unlocked, so the gate would reopen on the next login");

            // The POWER roll. Squib is 0-10 and is the one lineage excluded from the prodigy roll, so this
            // is the only band that can be asserted closed on both ends.
            int power = PlayerStatsAPI.getStat(player, PlayerStat.POWER);
            WizardTestSupport.check(helper,
                    power >= PowerBandTable.getBandMin(SQUIB) && power <= PowerBandTable.getBandMax(SQUIB),
                    () -> "committed Power " + power + " is outside the squib band "
                            + PowerBandTable.getBandMin(SQUIB) + "-" + PowerBandTable.getBandMax(SQUIB));

            // The body. Null here is the failure the admin path shipped with: heritage set, stats rolled,
            // and activeFormId left pointing at whatever the player was before.
            WizardTestSupport.check(helper, FormSystemAPI.getPlayerForm(player) != null,
                    () -> "commit assigned no form, so the size profile and render data never applied");

            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    private static void changeRerollsPowerAndKeepsTraining(GameTestHelper helper) {
        ServerPlayer player = newWizard(helper);
        try {
            WizardTestSupport.check(helper, SQUIB != null && FULL_GIANT != null,
                    () -> "the 'squib' or 'full_giant' lineage is gone");
            HeritageAPI.commit(player, Heritage.WIZARDKIND, SQUIB);
            String squibForm = FormSystemAPI.getPlayerFormId(player);

            // Practice, so the re-roll has something it could destroy. Set rather than trained: this
            // scenario is about what survives a lineage change, not about the accumulator.
            PlayerStatsAPI.setStat(player, PlayerStat.PRECISION, 61);
            PlayerStatsAPI.setStat(player, PlayerStat.WILLPOWER, 44);

            HeritageAPI.commit(player, Heritage.GIANT, FULL_GIANT);

            WizardTestSupport.check(helper, PlayerStatsAPI.getStat(player, PlayerStat.PRECISION) == 61,
                    () -> "changing heritage reset PRECISION to "
                            + PlayerStatsAPI.getStat(player, PlayerStat.PRECISION));
            WizardTestSupport.check(helper, PlayerStatsAPI.getStat(player, PlayerStat.WILLPOWER) == 44,
                    () -> "changing heritage reset WILLPOWER to "
                            + PlayerStatsAPI.getStat(player, PlayerStat.WILLPOWER));

            // The re-roll itself. A prodigy result deliberately leaves the band, so it is the alternative
            // rather than a failure — what must not happen is the squib roll surviving onto the new band.
            int power = PlayerStatsAPI.getStat(player, PlayerStat.POWER);
            boolean inBand = power >= PowerBandTable.getBandMin(FULL_GIANT)
                    && power <= PowerBandTable.getBandMax(FULL_GIANT);
            WizardTestSupport.check(helper, inBand || PlayerStatsAPI.isProdigy(player),
                    () -> "Power " + power + " after the change is neither on the new band "
                            + PowerBandTable.getBandMin(FULL_GIANT) + "-"
                            + PowerBandTable.getBandMax(FULL_GIANT) + " nor a prodigy roll");
            WizardTestSupport.check(helper,
                    PlayerStatsAPI.getData(player).powerGrowthAccumulated() == 0,
                    () -> "the growth allowance carried across a change of band");

            // The body has to follow the heritage, which is the half the admin path never did.
            String giantForm = FormSystemAPI.getPlayerFormId(player);
            WizardTestSupport.check(helper, giantForm != null && !giantForm.equals(squibForm),
                    () -> "the form stayed '" + squibForm + "' after changing to Giant");

            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    private static void clearTakesEverythingBackOff(GameTestHelper helper) {
        ServerPlayer player = newWizard(helper);
        try {
            WizardTestSupport.check(helper, FULL_GIANT != null, () -> "the 'full_giant' lineage is gone");
            HeritageAPI.commit(player, Heritage.GIANT, FULL_GIANT);
            WizardTestSupport.check(helper, hasModifier(player, Attributes.MAX_HEALTH, TYPE_HEALTH_ID),
                    () -> "the Giant health modifier was never applied, so this scenario proves nothing");

            HeritageAPI.clear(player, false);

            WizardTestSupport.check(helper, HeritageAPI.getPlayerHeritage(player) == null,
                    () -> "clear left a heritage behind");
            WizardTestSupport.check(helper, !hasModifier(player, Attributes.MAX_HEALTH, TYPE_HEALTH_ID),
                    () -> "clear left the Giant health modifier on the player");
            WizardTestSupport.check(helper, !hasModifier(player, Attributes.ARMOR, TYPE_ARMOR_ID),
                    () -> "clear left the Giant armour modifier on the player");
            WizardTestSupport.check(helper, FormSystemAPI.getPlayerFormId(player) == null,
                    () -> "clear left the player in the Giant body");

            // The quiet one. initializeStatsForNewPlayer refuses a block that is not at defaults, so a
            // POWER roll left behind here means the *next* heritage never rolls at all and keeps the old
            // lineage's number on the new lineage's band.
            PlayerStatsData stats = PlayerStatsAPI.getData(player);
            WizardTestSupport.check(helper, stats.isEmpty(),
                    () -> "clear left Power " + stats.power() + " behind, so the next selection cannot roll");

            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    /**
     * The whole point of the condition rework, on a live player: Remus Lupin is a half-blood wizard with a wand and
     * a teaching post, <em>and</em> a werewolf. The old model replaced his heritage with "Werewolf", re-rolled his
     * POWER onto a different band and left him no family at all.
     */
    private static void conditionKeepsTheWizardUnderneath(GameTestHelper helper) {
        ServerPlayer player = newWizard(helper);
        try {
            HeritageAPI.commit(player, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
            int powerBefore = PlayerStatsAPI.getStat(player, PlayerStat.POWER);
            PlayerStatsAPI.setStat(player, PlayerStat.PRECISION, 58);
            String humanForm = FormSystemAPI.getPlayerFormId(player);

            HeritageAPI.afflict(player, ConditionOrigin.BITTEN);

            PlayerHeritageData data = HeritageAPI.getData(player);
            WizardTestSupport.check(helper, data.getSelectedHeritage() == Heritage.WIZARDKIND
                            && data.getSelectedHeritageVariant() == HeritageVariant.HALF_BLOOD,
                    () -> "the bite changed who they are: " + data.getSelectedHeritage()
                            + "/" + data.getSelectedHeritageVariant());
            WizardTestSupport.check(helper, WerewolfRules.isWerewolf(data),
                    () -> "the bitten wizard is not a werewolf");
            WizardTestSupport.check(helper, data.hasTrait("moon_sensitive") && data.hasTrait("two_worlds"),
                    () -> "traits are not the union of lineage and condition: " + data.getCondition());
            WizardTestSupport.check(helper, PlayerStatsAPI.getStat(player, PlayerStat.POWER) == powerBefore,
                    () -> "the bite re-rolled POWER from " + powerBefore + " to "
                            + PlayerStatsAPI.getStat(player, PlayerStat.POWER));
            WizardTestSupport.check(helper, PlayerStatsAPI.getStat(player, PlayerStat.PRECISION) == 58,
                    () -> "the bite wiped training");
            WizardTestSupport.check(helper, data.canUseWand() && data.canCast(),
                    () -> "a bitten wizard lost their wand and their spellwork");

            // The body follows the condition, because that is the half of it that is physical.
            String wolfForm = FormSystemAPI.getPlayerFormId(player);
            WizardTestSupport.check(helper, WerewolfRules.HUMAN_FORM.equals(wolfForm),
                    () -> "the body after the bite is '" + wolfForm + "', not '"
                            + WerewolfRules.HUMAN_FORM + "' (was '" + humanForm + "')");

            // And a cure puts the wizard back exactly as they were, which is what makes the two layers separable.
            HeritageAPI.cure(player);
            WizardTestSupport.check(helper, HeritageAPI.getData(player).getCondition() == null
                            && !WerewolfRules.isWerewolf(HeritageAPI.getData(player)),
                    () -> "the condition survived a cure");
            WizardTestSupport.check(helper, PlayerStatsAPI.getStat(player, PlayerStat.POWER) == powerBefore
                            && PlayerStatsAPI.getStat(player, PlayerStat.PRECISION) == 58,
                    () -> "the cure touched the character's numbers");
            WizardTestSupport.check(helper,
                    !WerewolfRules.HUMAN_FORM.equals(FormSystemAPI.getPlayerFormId(player)),
                    () -> "the cured wizard is still wearing the werewolf body");

            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    /**
     * A condition that never leaves the server is a condition no HUD, screen or character sheet can show. The
     * transformation state had exactly this bug once: it travelled to one player and to nobody else.
     */
    private static void conditionReachesTheClient(GameTestHelper helper) {
        ServerPlayer player = newWizard(helper);
        try {
            HeritageAPI.commit(player, Heritage.WIZARDKIND, HeritageVariant.PURE_BLOOD);
            HeritageAPI.afflict(player, ConditionOrigin.UNLEASHED);

            HeritageDataSyncS2CPayload sent =
                    HeritageDataSyncS2CPayload.of(HeritageAPI.getData(player), false);
            ByteBuf buf = Unpooled.buffer();
            try {
                HeritageDataSyncS2CPayload.STREAM_CODEC.encode(buf, sent);
                HeritageDataSyncS2CPayload received = HeritageDataSyncS2CPayload.STREAM_CODEC.decode(buf);
                WizardTestSupport.check(helper,
                        "obscurus".equals(received.conditionId()) && "unleashed".equals(received.conditionOriginId()),
                        () -> "the client is told '" + received.conditionId() + "/"
                                + received.conditionOriginId() + "'");
                WizardTestSupport.check(helper, "wizardkind".equals(received.heritageId())
                                && "pure_blood".equals(received.variantId()),
                        () -> "the client is told the wrong lineage: " + received.heritageId()
                                + "/" + received.variantId());
            } finally {
                buf.release();
            }

            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    /**
     * The capability gate, where it is actually enforced. An Obscurus seals wandwork; before conditions existed this
     * was a property of the Obscurial <em>heritage</em>, so every reader had to be switched over or it silently
     * stopped holding.
     */
    private static void obscurusSealsTheWandAtThePacketGate(GameTestHelper helper) {
        ServerPlayer player = newWizard(helper);
        try {
            HeritageAPI.commit(player, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
            WizardTestSupport.check(helper, SpellNetworkGuards.wandRefusal(player) == null,
                    () -> "an ordinary wizard was refused a wand: " + SpellNetworkGuards.wandRefusal(player));

            HeritageAPI.afflict(player, ConditionOrigin.SUPPRESSED);
            WizardTestSupport.check(helper, SpellNetworkGuards.wandRefusal(player) != null,
                    () -> "an Obscurial was allowed to use a wand");
            WizardTestSupport.check(helper,
                    !SkillTreeId.meetsRequirement(SkillTreeId.Requirement.CASTING, HeritageAPI.getData(player)),
                    () -> "the casting regions stayed open to an Obscurial");

            HeritageAPI.cure(player);
            WizardTestSupport.check(helper, SpellNetworkGuards.wandRefusal(player) == null,
                    () -> "the wand stayed sealed after the Obscurus was gone: "
                            + SpellNetworkGuards.wandRefusal(player));

            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    /**
     * A player with no heritage at all, which is the state the gate acts on.
     *
     * <p>Deliberately not {@code WizardTestSupport.makeWandkind}: that shortcut writes the heritage fields
     * directly, which is the very thing these scenarios exist to stop other code doing.
     */
    private static ServerPlayer newWizard(GameTestHelper helper) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "HeritageCommitTest");
        WizardTestSupport.parkAtOrigin(helper, player);
        player.setData(ModAttachments.PLAYER_STATS.get(), PlayerStatsData.EMPTY);
        return player;
    }

    private static boolean hasModifier(ServerPlayer player, Holder<Attribute> attribute, Identifier id) {
        AttributeInstance instance = player.getAttribute(attribute);
        return instance != null && instance.getModifier(id) != null;
    }
}
