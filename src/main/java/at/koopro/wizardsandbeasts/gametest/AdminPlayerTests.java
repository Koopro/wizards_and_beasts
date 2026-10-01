package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.player.PlayerActionLog;
import at.koopro.wizardsandbeasts.admin.player.PlayerAdminAction;
import at.koopro.wizardsandbeasts.admin.player.PlayerAdminService;
import at.koopro.wizardsandbeasts.admin.player.PlayerAdminService.Facet;
import at.koopro.wizardsandbeasts.admin.player.PlayerAdminService.Outcome;
import at.koopro.wizardsandbeasts.admin.player.PlayerAdminService.Request;
import at.koopro.wizardsandbeasts.currency.vault.PlayerVaultData;
import at.koopro.wizardsandbeasts.ministry.MinistryRecords;
import at.koopro.wizardsandbeasts.ministry.law.MinistryFines;
import at.koopro.wizardsandbeasts.network.admin.AdminPlayerPayloads;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * The Players section against live players: who may read and act, what an invalid target or argument gets, that
 * destructive actions wait for confirmation, that every authorised attempt is logged with its result, and that a
 * failure part-way through puts the character back.
 */
public final class AdminPlayerTests {

    private static final AdminContext CONSOLE = AdminContext.detached(null, "game-test", EnumSet.allOf(AdminCapability.class));
    /** Everything but money: may read and act on players, may not create money. */
    private static final AdminContext NO_MONEY = AdminContext.detached(null, "game-test-no-money",
            EnumSet.complementOf(EnumSet.of(AdminCapability.MONEY)));
    /** A config administrator: no players capability at all. */
    private static final AdminContext NO_PLAYERS = AdminContext.detached(null, "game-test-config",
            EnumSet.of(AdminCapability.CONFIG));

    private AdminPlayerTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("admin_player_unauthorised_is_refused",
                "admin players: a non-admin's packets read nothing and change nothing; money needs the money authority",
                AdminPlayerTests::unauthorisedIsRefused);
        tests.add("admin_player_invalid_targets_and_arguments",
                "admin players: an offline target, an unknown spell or effect and an out-of-range amount are refused and logged",
                AdminPlayerTests::invalidTargets);
        tests.add("admin_player_destructive_needs_confirmation",
                "admin players: destructive actions are held until confirmed, then applied through each system",
                AdminPlayerTests::destructiveNeedsConfirmation);
        tests.add("admin_player_actions_are_logged",
                "admin players: every authorised attempt is in the world's action log with player, action, admin, time, result",
                AdminPlayerTests::actionsAreLogged);
        tests.add("admin_player_failure_rolls_back",
                "admin players: an action that fails after mutating puts spells, vault and effects back",
                AdminPlayerTests::failureRollsBack);
        tests.add("admin_player_facets_read_live_state",
                "admin players: facets read the owning systems; positions only with world authority",
                AdminPlayerTests::facetsReadLiveState);
    }

    private static Outcome perform(MinecraftServer server, AdminContext actor, ServerPlayer target,
                                   PlayerAdminAction action, String argument, long amount, boolean confirmed) {
        return PlayerAdminService.perform(actor, server, new Request(target.getUUID(), action, argument, amount, confirmed));
    }

    private static PlayerVaultData vault(ServerPlayer player) {
        return player.getData(ModAttachments.VAULT_DATA.get());
    }

    private static PlayerSpellData spells(ServerPlayer player) {
        return player.getData(ModAttachments.SPELL_DATA.get());
    }

    private static void unauthorisedIsRefused(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer outsider = WizardTestSupport.placeMockPlayer(helper, "PlayersOutsider", GameType.SURVIVAL);
        ServerPlayer subject = WizardTestSupport.placeMockPlayer(helper, "PlayersSubject", GameType.SURVIVAL);
        try {
            long galleons = vault(subject).getGalleons();
            int logged = PlayerActionLog.get(server).recent(subject.getUUID(), 100).size();
            WizardTestSupport.drainClientboundPayloads(outsider);
            check(helper, !AdminPlayerPayloads.sendSearch(outsider, "")
                            && !AdminPlayerPayloads.sendFacet(outsider, subject.getUUID(), Facet.ECONOMY)
                            && !AdminPlayerPayloads.sendLog(outsider, subject.getUUID()),
                    () -> "a non-admin was answered a read");
            Outcome money = AdminPlayerPayloads.run(outsider, new AdminPlayerPayloads.ActionRequest(subject.getUUID(),
                    PlayerAdminAction.MONEY_DEPOSIT, "galleons", 100, true));
            Outcome wipe = AdminPlayerPayloads.run(outsider, new AdminPlayerPayloads.ActionRequest(subject.getUUID(),
                    PlayerAdminAction.SPELL_RESET_PROGRESS, "", 0, true));
            check(helper, "unauthorized".equals(money.code()) && "unauthorized".equals(wipe.code())
                    && vault(subject).getGalleons() == galleons, () -> "a non-admin acted: " + money + " / " + wipe);
            for (CustomPacketPayload payload : WizardTestSupport.drainClientboundPayloads(outsider)) {
                check(helper, !(payload instanceof AdminPlayerPayloads.SearchReply)
                                && !(payload instanceof AdminPlayerPayloads.FacetReply)
                                && !(payload instanceof AdminPlayerPayloads.LogReply),
                        () -> "a non-admin received " + payload.type().id());
            }
            check(helper, PlayerActionLog.get(server).recent(subject.getUUID(), 100).size() == logged,
                    () -> "a non-admin's attempts filled the action log");

            // A config administrator reads no character; a players administrator without money authority makes none.
            check(helper, PlayerAdminService.facet(NO_PLAYERS, server, subject.getUUID(), Facet.OVERVIEW) == null
                            && PlayerAdminService.search(NO_PLAYERS, server, "").isEmpty(),
                    () -> "an administrator without the players capability read a character");
            Outcome noMoney = perform(server, NO_MONEY, subject, PlayerAdminAction.MONEY_DEPOSIT, "galleons", 5, true);
            check(helper, "unauthorized".equals(noMoney.code()) && vault(subject).getGalleons() == galleons,
                    () -> "money was created without the money authority: " + noMoney);
            Outcome inspectOk = perform(server, NO_MONEY, subject, PlayerAdminAction.SKILL_POINTS_ADD, "", 1, false);
            check(helper, !"unauthorized".equals(inspectOk.code()), () -> "a players administrator was refused a players action");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, outsider);
            WizardTestSupport.retire(helper, subject);
        }
    }

    private static void invalidTargets(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer subject = WizardTestSupport.placeMockPlayer(helper, "PlayersTarget", GameType.SURVIVAL);
        try {
            UUID nobody = UUID.randomUUID();
            Outcome offline = PlayerAdminService.perform(CONSOLE, server,
                    new Request(nobody, PlayerAdminAction.EFFECTS_CLEAR, "", 0, true));
            check(helper, "no_player".equals(offline.code()) && offline.logSequence() > 0,
                    () -> "an offline target was not refused and logged: " + offline);
            check(helper, PlayerAdminService.facet(CONSOLE, server, nobody, Facet.OVERVIEW) == null,
                    () -> "an offline player had a facet");
            Outcome spell = perform(server, CONSOLE, subject, PlayerAdminAction.SPELL_GRANT, "wizards_and_beasts:no_such_spell", 0, true);
            Outcome effect = perform(server, CONSOLE, subject, PlayerAdminAction.EFFECT_REMOVE, "not an id", 0, false);
            Outcome tooMuch = perform(server, CONSOLE, subject, PlayerAdminAction.MONEY_DEPOSIT, "galleons",
                    PlayerAdminService.MAX_COINS + 1, true);
            Outcome coin = perform(server, CONSOLE, subject, PlayerAdminAction.MONEY_DEPOSIT, "dragots", 1, true);
            Outcome points = perform(server, CONSOLE, subject, PlayerAdminAction.SKILL_POINTS_ADD, "", 0, false);
            for (Outcome outcome : List.of(spell, effect, tooMuch, coin, points)) {
                check(helper, "invalid_argument".equals(outcome.code()), () -> "an invalid argument passed: " + outcome);
            }
            // The previewed effect lands on the administrator; the console has no body to put it on.
            Outcome preview = perform(server, CONSOLE, subject, PlayerAdminAction.EFFECT_PREVIEW, "minecraft:speed", 0, false);
            check(helper, "no_player".equals(preview.code()) && !subject.hasEffect(MobEffects.SPEED),
                    () -> "a preview landed on the target: " + preview);
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, subject);
        }
    }

    private static void destructiveNeedsConfirmation(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer subject = WizardTestSupport.placeMockPlayer(helper, "PlayersDestructive", GameType.SURVIVAL);
        try {
            String spell = Spells.PROTEGO.getId();
            subject.addEffect(new MobEffectInstance(MobEffects.SPEED, 2000, 1));
            vault(subject).depositGalleons(3);
            MinistryRecords.mutate(subject, record -> record.withNotoriety(40f).withOutstandingFine(500));

            for (PlayerAdminAction action : List.of(PlayerAdminAction.SPELL_GRANT, PlayerAdminAction.EFFECTS_CLEAR,
                    PlayerAdminAction.MONEY_WITHDRAW, PlayerAdminAction.MINISTRY_PARDON, PlayerAdminAction.SKILL_RESET)) {
                String argument = action == PlayerAdminAction.SPELL_GRANT ? spell
                        : action == PlayerAdminAction.MONEY_WITHDRAW ? "galleons" : "";
                Outcome held = perform(server, CONSOLE, subject, action, argument, action == PlayerAdminAction.MONEY_WITHDRAW ? 2 : 0, false);
                check(helper, "confirm_required".equals(held.code()), () -> action + " was not held: " + held);
            }
            check(helper, !spells(subject).knowsSpell(spell) && subject.hasEffect(MobEffects.SPEED)
                            && vault(subject).getGalleons() == 3 && MinistryFines.owed(subject) == 500,
                    () -> "an unconfirmed action changed the character");

            check(helper, perform(server, CONSOLE, subject, PlayerAdminAction.SPELL_GRANT, spell, 0, true).success()
                    && spells(subject).knowsSpell(spell), () -> "grant");
            check(helper, perform(server, CONSOLE, subject, PlayerAdminAction.SPELL_REVOKE, spell, 0, true).success()
                    && !spells(subject).knowsSpell(spell), () -> "revoke");
            check(helper, perform(server, CONSOLE, subject, PlayerAdminAction.EFFECTS_CLEAR, "", 0, true).success()
                    && subject.getActiveEffects().isEmpty(), () -> "clear effects");
            Outcome short2 = perform(server, CONSOLE, subject, PlayerAdminAction.MONEY_WITHDRAW, "galleons", 5, true);
            check(helper, "insufficient".equals(short2.code()) && vault(subject).getGalleons() == 3,
                    () -> "withdrawing more than the vault holds: " + short2);
            check(helper, perform(server, CONSOLE, subject, PlayerAdminAction.MONEY_WITHDRAW, "galleons", 2, true).success()
                    && vault(subject).getGalleons() == 1, () -> "withdraw");
            check(helper, perform(server, CONSOLE, subject, PlayerAdminAction.MINISTRY_PARDON, "", 0, true).success()
                    && MinistryFines.owed(subject) == 0 && MinistryRecords.get(subject).notoriety() == 0f, () -> "pardon");
            int before = SkillSystemAPI.getSkillData(subject).getSkillPoints();
            Outcome points = perform(server, CONSOLE, subject, PlayerAdminAction.SKILL_POINTS_ADD, "", 2, false);
            check(helper, points.success() ? SkillSystemAPI.getSkillData(subject).getSkillPoints() == before + 2
                    : "nothing_to_do".equals(points.code()), () -> "skill points: " + points);
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, subject);
        }
    }

    private static void actionsAreLogged(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer subject = WizardTestSupport.placeMockPlayer(helper, "PlayersLogged", GameType.SURVIVAL);
        try {
            Outcome held = perform(server, CONSOLE, subject, PlayerAdminAction.MONEY_DEPOSIT, "sickles", 7, false);
            Outcome done = perform(server, CONSOLE, subject, PlayerAdminAction.MONEY_DEPOSIT, "sickles", 7, true);
            List<PlayerActionLog.Entry> entries = PlayerActionLog.get(server).recent(subject.getUUID(), 2);
            check(helper, entries.size() == 2 && entries.get(0).sequence() == done.logSequence()
                            && entries.get(1).sequence() == held.logSequence(),
                    () -> "the attempts were not logged newest first: " + entries);
            PlayerActionLog.Entry last = entries.get(0);
            check(helper, last.ok() && last.adminName().equals("game-test") && last.playerId().equals(subject.getUUID())
                            && last.playerName().equals("PlayersLogged") && last.action().equals("money_deposit")
                            && last.argument().contains("sickles") && last.timeMillis() > 0 && last.detail().contains("→"),
                    () -> "the entry is missing what happened: " + last);
            check(helper, entries.get(1).result().equals("refused:confirm_required"),
                    () -> "a held attempt was logged as " + entries.get(1).result());
            check(helper, PlayerActionLog.get(server).isDirty() || PlayerActionLog.get(server).recent(null, 1).size() == 1,
                    () -> "the log is not saved with the world");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, subject);
        }
    }

    private static void failureRollsBack(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer subject = WizardTestSupport.placeMockPlayer(helper, "PlayersRollback", GameType.SURVIVAL);
        try {
            String spell = Spells.PROTEGO.getId();
            vault(subject).depositKnuts(10);
            subject.addEffect(new MobEffectInstance(MobEffects.SPEED, 2000, 1));
            PlayerAdminService.failAfterMutationForTest = true;
            Outcome money = perform(server, CONSOLE, subject, PlayerAdminAction.MONEY_DEPOSIT, "knuts", 50, true);
            Outcome grant = perform(server, CONSOLE, subject, PlayerAdminAction.SPELL_GRANT, spell, 0, true);
            Outcome clear = perform(server, CONSOLE, subject, PlayerAdminAction.EFFECTS_CLEAR, "", 0, true);
            PlayerAdminService.failAfterMutationForTest = false;
            check(helper, "failed".equals(money.code()) && "failed".equals(grant.code()) && "failed".equals(clear.code()),
                    () -> "the injected failures were not reported: " + money + " / " + grant + " / " + clear);
            check(helper, vault(subject).getKnuts() == 10, () -> "the vault was not restored: " + vault(subject).getKnuts());
            check(helper, !spells(subject).knowsSpell(spell), () -> "the granted spell survived the rollback");
            MobEffectInstance speed = subject.getEffect(MobEffects.SPEED);
            check(helper, speed != null && speed.getAmplifier() == 1, () -> "the effects were not restored");
            List<PlayerActionLog.Entry> entries = PlayerActionLog.get(server).recent(subject.getUUID(), 3);
            check(helper, entries.stream().allMatch(e -> e.result().equals("failed:failed")),
                    () -> "the failures were not logged as failed: " + entries);
            helper.succeed();
        } finally {
            PlayerAdminService.failAfterMutationForTest = false;
            WizardTestSupport.retire(helper, subject);
        }
    }

    private static void facetsReadLiveState(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer subject = WizardTestSupport.placeMockPlayer(helper, "PlayersFacets", GameType.SURVIVAL);
        try {
            vault(subject).depositGalleons(4);
            subject.addEffect(new MobEffectInstance(MobEffects.SPEED, 600, 0));
            for (Facet facet : Facet.values()) {
                check(helper, PlayerAdminService.facet(CONSOLE, server, subject.getUUID(), facet) != null,
                        () -> "facet " + facet + " could not be read");
            }
            PlayerAdminService.FacetView economy = PlayerAdminService.facet(CONSOLE, server, subject.getUUID(), Facet.ECONOMY);
            check(helper, economy.facts().stream().anyMatch(f -> f.labelKey().endsWith("galleons") && f.value().equals("4")),
                    () -> "the economy facet does not read the vault: " + economy.facts());
            PlayerAdminService.FacetView effects = PlayerAdminService.facet(CONSOLE, server, subject.getUUID(), Facet.EFFECTS);
            check(helper, effects.items().stream().anyMatch(i -> i.id().equals("minecraft:speed") && i.actionable())
                            && effects.options().stream().noneMatch(o -> o.id().equals("minecraft:instant_damage")),
                    () -> "the effects facet: " + effects.items());
            AdminContext noWorld = AdminContext.detached(null, "no-world", EnumSet.of(AdminCapability.PLAYERS));
            check(helper, PlayerAdminService.search(noWorld, server, "PlayersFacets").stream().allMatch(r -> r.location().isEmpty())
                            && PlayerAdminService.search(CONSOLE, server, "PlayersFacets").stream()
                            .anyMatch(r -> !r.location().isEmpty()),
                    () -> "positions were shown without world authority, or not with it");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, subject);
        }
    }
}
