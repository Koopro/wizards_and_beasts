package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.command.WizardsAndBeastsCommands;
import at.koopro.wizardsandbeasts.education.EducationService;
import at.koopro.wizardsandbeasts.event.heritage.HeritageOnboarding;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.journey.ApprenticePath;
import at.koopro.wizardsandbeasts.journey.ApprenticePathService;
import at.koopro.wizardsandbeasts.journey.PathRecord;
import at.koopro.wizardsandbeasts.journey.PathStep;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.network.heritage.HeritageDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.journey.ApprenticePathS2CPayload;
import at.koopro.wizardsandbeasts.registry.CurrencyItemRegistry;
import at.koopro.wizardsandbeasts.registry.MiscItemRegistry;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.spell.learning.SpellLearningService;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.List;
import java.util.UUID;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.retire;

/**
 * The Apprentice's Path end to end on a real server player: what each step pays, that it pays once, that it is saved,
 * which steps a wandless heritage walks, and the ceremony gate that opens the path.
 */
public final class ApprenticePathTests {

    private static final String LUMOS = "wizards_and_beasts:lumos";

    private ApprenticePathTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("path_arrival_pays_once",
                "path: choosing a heritage pays the Ministry Handbook and unlocks the bench, wand blank and handbook "
                        + "recipes; a second look pays nothing more",
                ApprenticePathTests::arrivalPaysOnce);
        tests.add("path_wand_puts_lumos_on_the_wheel",
                "path: a wand that has chosen the player completes the wand step and puts Lumos on an empty wheel "
                        + "slot, selected; casting then pays two Sickles",
                ApprenticePathTests::wandPutsLumosOnTheWheel);
        tests.add("path_own_spell_pays_one_skill_point",
                "path: knowing a spell beyond the starter charms pays exactly one skill point and a duelling dummy, "
                        + "however often the path looks",
                ApprenticePathTests::ownSpellPaysOnePoint);
        tests.add("path_record_survives_a_reconnect",
                "path: done steps and the guided flag are saved with the player and come back on reconnect, so "
                        + "nothing is paid twice",
                ApprenticePathTests::recordSurvivesReconnect);
        tests.add("path_goblin_walks_without_a_wand",
                "path: a goblin, who cannot use a wand, walks only the ceremony and field study; a wand in their "
                        + "pack completes nothing",
                ApprenticePathTests::goblinWalksWithoutAWand);
        tests.add("path_switched_off_pays_nothing",
                "path: a player who switched the path off is paid nothing and sent no steps; switching it back on "
                        + "pays what they have already done",
                ApprenticePathTests::switchedOffPaysNothing);
        tests.add("path_leads_the_education_list",
                "path: a wizard on the path is told its next step first in Education, ahead of the O.W.L.s",
                ApprenticePathTests::leadsTheEducationList);
        tests.add("path_old_save_is_reconciled_without_pay",
                "path: a player who played before the path existed has the steps they already met marked done at "
                        + "login, with the recipes unlocked but no handbook, coin, dummy or skill point",
                ApprenticePathTests::oldSaveReconciledWithoutPay);
        tests.add("path_owed_reward_is_paid_at_login",
                "path: a reward left owed by a failed hand-over is paid at the next login, and only once",
                ApprenticePathTests::owedRewardPaidAtLogin);
        tests.add("path_players_are_paid_separately",
                "path: one player's ceremony pays that player only; a second player beside them is paid nothing",
                ApprenticePathTests::playersPaidSeparately);
        tests.add("path_survives_death",
                "path: done steps come through death and respawn, and the respawned player is not paid again",
                ApprenticePathTests::survivesDeath);
        tests.add("path_gate_reopens_after_respawn",
                "heritage gate: a player who respawned before choosing is put back in front of the ceremony; one who "
                        + "has chosen is not",
                ApprenticePathTests::gateReopensAfterRespawn);
    }

    private static void arrivalPaysOnce(GameTestHelper helper) {
        ServerPlayer player = guided(helper, "PathArrive");
        try {
            check(helper, ApprenticePathService.evaluate(player).isEmpty(),
                    () -> "the path paid a player who has not been through the ceremony");
            HeritageAPI.commit(player, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);

            List<PathStep> first = ApprenticePathService.evaluate(player);
            check(helper, first.contains(PathStep.ARRIVE), () -> "arrival was not noticed: " + first);
            int books = player.getInventory().countItem(MiscItemRegistry.MINISTRY_HANDBOOK.get());
            check(helper, books == 1, () -> "arrival handed over " + books + " handbooks, not one");
            for (String recipe : List.of("wandmakers_bench", "wand_blank", "ministry_handbook")) {
                ResourceKey<net.minecraft.world.item.crafting.Recipe<?>> key = ResourceKey.create(Registries.RECIPE,
                        Identifier.fromNamespaceAndPath("wizards_and_beasts", recipe));
                check(helper, player.getRecipeBook().contains(key), () -> "the " + recipe + " recipe was not unlocked");
            }

            List<PathStep> second = ApprenticePathService.evaluate(player);
            check(helper, second.isEmpty(), () -> "a second look paid again: " + second);
            check(helper, player.getInventory().countItem(MiscItemRegistry.MINISTRY_HANDBOOK.get()) == 1,
                    () -> "the handbook was handed over twice");
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void wandPutsLumosOnTheWheel(GameTestHelper helper) {
        ServerPlayer player = guided(helper, "PathWand");
        try {
            HeritageAPI.commit(player, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
            SpellLearningService.teachStarter(player);
            PlayerSpellData spells = player.getData(ModAttachments.SPELL_DATA.get());
            for (int slot = 0; slot < PlayerSpellData.LOADOUT_SIZE; slot++) {
                spells.setLoadoutSpell(slot, null);
            }
            ApprenticePathService.evaluate(player);
            check(helper, !ApprenticePathService.record(player).has(PathStep.WAND),
                    () -> "the wand step was done with no wand in the pack");

            WizardTestSupport.giveBondedWand(player);
            List<PathStep> paid = ApprenticePathService.evaluate(player);
            check(helper, paid.equals(List.of(PathStep.WAND)), () -> "a bonded wand paid " + paid + ", not the wand step");
            String lumos = Spells.byId(LUMOS).getId();
            check(helper, lumos.equals(spells.getActiveSpellId()),
                    () -> "Lumos is not the selected wheel spell; selected is " + spells.getActiveSpellId());

            spells.incrementCastCount(lumos);
            List<PathStep> cast = ApprenticePathService.evaluate(player);
            check(helper, cast.equals(List.of(PathStep.FIRST_LIGHT)), () -> "a cast paid " + cast);
            int sickles = player.getInventory().countItem(CurrencyItemRegistry.SICKLE.get());
            check(helper, sickles == 2, () -> "first light paid " + sickles + " Sickles, not two");
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void ownSpellPaysOnePoint(GameTestHelper helper) {
        ServerPlayer player = guided(helper, "PathOwnSpell");
        try {
            HeritageAPI.commit(player, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
            SpellLearningService.teachStarter(player);
            ApprenticePathService.evaluate(player);
            check(helper, !ApprenticePathService.record(player).has(PathStep.OWN_SPELL),
                    () -> "the starter charms counted as a spell of one's own");

            int before = SkillSystemAPI.getSkillData(player).getSkillPoints();
            SpellLearningService.grantWithoutTrace(player, Spells.byId("wizards_and_beasts:stupefy"));
            ApprenticePathService.evaluate(player);
            ApprenticePathService.evaluate(player);
            int after = SkillSystemAPI.getSkillData(player).getSkillPoints();
            check(helper, after == before + 1, () -> "a spell of one's own paid " + (after - before) + " points, not one");
            int dummies = player.getInventory().countItem(MiscItemRegistry.DUELLING_DUMMY.get());
            check(helper, dummies == 1, () -> "a spell of one's own paid " + dummies + " dummies, not one");
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void recordSurvivesReconnect(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ServerPlayer first = WizardTestSupport.placeMockPlayer(helper, "PathReturns", id, GameType.SURVIVAL);
        ServerPlayer second = null;
        try {
            first.setData(ModAttachments.APPRENTICE_PATH.get(), PathRecord.EMPTY);
            HeritageAPI.commit(first, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
            ApprenticePathService.evaluate(first);
            helper.getLevel().getServer().getPlayerList().saveAll();
            retire(helper, first);
            first = null;

            second = WizardTestSupport.placeMockPlayer(helper, "PathReturns", id, GameType.SURVIVAL);
            PathRecord back = ApprenticePathService.record(second);
            check(helper, back.guided() && back.has(PathStep.ARRIVE),
                    () -> "the path came back from a reconnect as " + back);
            ServerPlayer returned = second;
            check(helper, ApprenticePathService.evaluate(returned).isEmpty(),
                    () -> "a reconnect paid the ceremony again");
            helper.succeed();
        } finally {
            retire(helper, first);
            retire(helper, second);
        }
    }

    private static void goblinWalksWithoutAWand(GameTestHelper helper) {
        ServerPlayer goblin = guided(helper, "PathGoblin");
        try {
            HeritageAPI.commit(goblin, Heritage.GOBLIN, HeritageVariant.GOBLIN_COMMON);
            WizardTestSupport.giveBondedWand(goblin);
            List<PathStep> paid = ApprenticePathService.evaluate(goblin);
            check(helper, paid.equals(List.of(PathStep.ARRIVE)), () -> "a goblin with a wand was paid " + paid);
            var facts = ApprenticePathService.facts(goblin, ApprenticePathService.record(goblin));
            List<PathStep> steps = ApprenticePath.applicable(facts);
            check(helper, !steps.contains(PathStep.WAND) && steps.contains(PathStep.FIELD_STUDY),
                    () -> "a goblin's path is " + steps);
            helper.succeed();
        } finally {
            retire(helper, goblin);
        }
    }

    private static void switchedOffPaysNothing(GameTestHelper helper) {
        ServerPlayer player = guided(helper, "PathOff");
        try {
            ApprenticePathService.setGuided(player, false);
            HeritageAPI.commit(player, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
            WizardTestSupport.drainClientboundPayloads(player);
            check(helper, ApprenticePathService.evaluate(player).isEmpty(), () -> "a switched-off path paid");
            ApprenticePathService.sync(player);
            ApprenticePathS2CPayload sent = lastPath(player);
            check(helper, sent != null && sent.steps().isEmpty(),
                    () -> "a switched-off path sent steps: " + sent);

            ApprenticePathService.setGuided(player, true);
            check(helper, ApprenticePathService.record(player).has(PathStep.ARRIVE),
                    () -> "switching the path back on did not pay the ceremony already done");
            check(helper, player.getInventory().countItem(MiscItemRegistry.MINISTRY_HANDBOOK.get()) == 1,
                    () -> "switching the path back on did not hand over the handbook");
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void leadsTheEducationList(GameTestHelper helper) {
        ServerPlayer player = guided(helper, "PathEducation");
        try {
            HeritageAPI.commit(player, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
            ApprenticePathService.evaluate(player);
            var next = EducationService.view(player).next();
            check(helper, !next.isEmpty() && next.getFirst().getContents() instanceof TranslatableContents t
                            && t.getKey().equals("education.next.path"),
                    () -> "Education does not lead with the path: " + next);
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void gateReopensAfterRespawn(GameTestHelper helper) {
        Runnable release = WizardTestSupport.leaseModule(Module.HERITAGE);
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "GateRespawn", GameType.SURVIVAL);
        try {
            WizardTestSupport.parkAtOrigin(helper, player);
            HeritageAPI.clear(player, false);
            WizardTestSupport.drainClientboundPayloads(player);
            WizardsAndBeastsCommands.onPlayerRespawn(new PlayerEvent.PlayerRespawnEvent(player, false));
            check(helper, gateOpened(player), () -> "a respawn before the ceremony did not reopen the gate");

            HeritageAPI.commit(player, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
            WizardTestSupport.drainClientboundPayloads(player);
            WizardsAndBeastsCommands.onPlayerRespawn(new PlayerEvent.PlayerRespawnEvent(player, false));
            check(helper, !gateOpened(player), () -> "a respawn after the ceremony opened the gate again");
            check(helper, !HeritageOnboarding.promptIfNeeded(player), () -> "the gate prompts a player who has chosen");
            helper.succeed();
        } finally {
            retire(helper, player);
            release.run();
        }
    }

    private static void oldSaveReconciledWithoutPay(GameTestHelper helper) {
        ServerPlayer veteran = guided(helper, "PathVeteran");
        try {
            HeritageAPI.commit(veteran, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
            SpellLearningService.teachStarter(veteran);
            WizardTestSupport.giveBondedWand(veteran);
            SpellLearningService.grantWithoutTrace(veteran, Spells.byId("wizards_and_beasts:stupefy"));
            veteran.getStats().setValue(veteran, Stats.CUSTOM.get(Stats.PLAY_TIME), 72_000);
            veteran.removeData(ModAttachments.APPRENTICE_PATH.get());
            int points = SkillSystemAPI.getSkillData(veteran).getSkillPoints();

            ApprenticePathService.login(veteran, false);
            PathRecord record = ApprenticePathService.record(veteran);
            check(helper, record.has(PathStep.ARRIVE) && record.has(PathStep.WAND) && record.has(PathStep.OWN_SPELL),
                    () -> "the old save was reconciled to " + record);
            check(helper, record.owed().isEmpty(), () -> "reconciled steps are owed: " + record.owed());
            check(helper, veteran.getInventory().countItem(MiscItemRegistry.MINISTRY_HANDBOOK.get()) == 0
                            && veteran.getInventory().countItem(MiscItemRegistry.DUELLING_DUMMY.get()) == 0,
                    () -> "the veteran was handed items for the past");
            int after = SkillSystemAPI.getSkillData(veteran).getSkillPoints();
            check(helper, after == points, () -> "the veteran was paid " + (after - points) + " skill points");
            check(helper, veteran.getRecipeBook().contains(recipe("wandmakers_bench")),
                    () -> "the veteran's recipe book was not given the bench");
            check(helper, ApprenticePathService.evaluate(veteran).isEmpty(), () -> "a look after reconciling paid");
            helper.succeed();
        } finally {
            retire(helper, veteran);
        }
    }

    private static void owedRewardPaidAtLogin(GameTestHelper helper) {
        ServerPlayer player = guided(helper, "PathOwed");
        try {
            HeritageAPI.commit(player, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
            // As a hand-over that threw would have left it: recorded, still owed.
            player.setData(ModAttachments.APPRENTICE_PATH.get(), PathRecord.EMPTY.earned(PathStep.ARRIVE));
            ApprenticePathService.login(player, true);
            check(helper, player.getInventory().countItem(MiscItemRegistry.MINISTRY_HANDBOOK.get()) == 1,
                    () -> "the owed handbook was not paid at login");
            check(helper, !ApprenticePathService.record(player).owes(PathStep.ARRIVE), () -> "the debt was not struck");
            ApprenticePathService.login(player, true);
            check(helper, player.getInventory().countItem(MiscItemRegistry.MINISTRY_HANDBOOK.get()) == 1,
                    () -> "a second login paid the debt again");
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void playersPaidSeparately(GameTestHelper helper) {
        ServerPlayer chosen = guided(helper, "PathFirstOf2");
        ServerPlayer waiting = guided(helper, "PathSecondOf2");
        try {
            HeritageAPI.commit(chosen, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
            ApprenticePathService.evaluate(chosen);
            ApprenticePathService.evaluate(waiting);
            check(helper, chosen.getInventory().countItem(MiscItemRegistry.MINISTRY_HANDBOOK.get()) == 1,
                    () -> "the player who chose was not paid");
            check(helper, waiting.getInventory().countItem(MiscItemRegistry.MINISTRY_HANDBOOK.get()) == 0
                            && ApprenticePathService.record(waiting).done().isEmpty(),
                    () -> "the player still at the gate was paid or recorded: " + ApprenticePathService.record(waiting));
            helper.succeed();
        } finally {
            retire(helper, chosen);
            retire(helper, waiting);
        }
    }

    private static void survivesDeath(GameTestHelper helper) {
        ServerPlayer player = guided(helper, "PathMortal");
        HeritageAPI.commit(player, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
        ApprenticePathService.evaluate(player);
        helper.startSequence()
                .thenExecute(() -> player.kill(helper.getLevel()))
                .thenIdle(2)
                .thenExecute(() -> {
                    ServerPlayer fresh = helper.getLevel().getServer().getPlayerList()
                            .respawn(player, false, Entity.RemovalReason.KILLED);
                    try {
                        PathRecord record = ApprenticePathService.record(fresh);
                        check(helper, record.guided() && record.has(PathStep.ARRIVE),
                                () -> "the path came through death as " + record);
                        check(helper, ApprenticePathService.evaluate(fresh).isEmpty(),
                                () -> "the respawned player was paid again");
                        helper.succeed();
                    } finally {
                        retire(helper, fresh);
                    }
                });
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private static ResourceKey<net.minecraft.world.item.crafting.Recipe<?>> recipe(String path) {
        return ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("wizards_and_beasts", path));
    }

    /** A survival player with the path switched on (mock players start with it off). */
    private static ServerPlayer guided(GameTestHelper helper, String name) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name, GameType.SURVIVAL);
        WizardTestSupport.parkAtOrigin(helper, player);
        player.setData(ModAttachments.APPRENTICE_PATH.get(), PathRecord.EMPTY);
        return player;
    }

    private static boolean gateOpened(ServerPlayer player) {
        for (var payload : WizardTestSupport.drainClientboundPayloads(player)) {
            if (payload instanceof HeritageDataSyncS2CPayload sync && sync.openSelector()) {
                return true;
            }
        }
        return false;
    }

    private static @org.jspecify.annotations.Nullable ApprenticePathS2CPayload lastPath(ServerPlayer player) {
        ApprenticePathS2CPayload last = null;
        for (var payload : WizardTestSupport.drainClientboundPayloads(player)) {
            if (payload instanceof ApprenticePathS2CPayload path) {
                last = path;
            }
        }
        return last;
    }
}
