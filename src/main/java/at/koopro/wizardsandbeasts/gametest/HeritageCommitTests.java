package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.form.FormSystemAPI;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
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
