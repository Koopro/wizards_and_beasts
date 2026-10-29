package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.event.heritage.ObscurialHeritageHandler;
import at.koopro.wizardsandbeasts.heritage.ConditionOrigin;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialAbility;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialMastery;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialResourceManager;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialServerLogic;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.cast.SpellRejectCodes;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.stats.PlayerStatsData;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * The Obscurus's mastery on live hosts: what earns it, what a collapse costs on each path, the gate Grasp now asks
 * instead of a curse an Obscurial could never learn, and the stale forced-break deadline that used to throw a voluntary
 * dark form straight back out. Survival hosts, because the collapse wound and the debuffs skip creative.
 */
public final class ObscurialMasteryTests {

    private static final String FORCED_FLAG = "obscurial_forced_dark_until_tick";

    private ObscurialMasteryTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("obscurial_mastery_comes_from_venting_under_strain",
                "obscurial: a vent under real strain earns mastery; a vent at ease does not",
                ObscurialMasteryTests::masteryComesFromVentingUnderStrain);
        tests.add("obscurial_mastery_collapse_costs_by_path",
                "obscurial: a collapse is softened on the controlled path, harsher and wounding on the unleashed one",
                ObscurialMasteryTests::collapseCostsByPath);
        tests.add("obscurial_mastery_gates_grasp",
                "obscurial: Grasp answers to mastery, not to an Imperius proficiency the host cannot learn",
                ObscurialMasteryTests::masteryGatesGrasp);
        tests.add("obscurial_stale_forced_break_is_cleared",
                "obscurial: a forced-break deadline does not outlive the dark form it was set for",
                ObscurialMasteryTests::staleForcedBreakIsCleared);
    }

    private static void masteryComesFromVentingUnderStrain(GameTestHelper helper) {
        ServerPlayer host = newHost(helper, "VentingHost", ConditionOrigin.SUPPRESSED);
        try {
            ObscurialResourceManager.setStress(host, 10f);
            ObscurialServerLogic.stressVent(host);
            check(helper, ObscurialResourceManager.getMastery(host) == 0f,
                    () -> "a vent at ease earned " + ObscurialResourceManager.getMastery(host));

            ObscurialResourceManager.setStressVentCooldownUntil(host, 0L);
            ObscurialResourceManager.setStress(host, 60f);
            ObscurialServerLogic.stressVent(host);
            check(helper, ObscurialResourceManager.getMastery(host) == ObscurialMastery.VENT_GAIN,
                    () -> "a vent under strain earned " + ObscurialResourceManager.getMastery(host));

            // The cooldown still holds: a second vent at once earns nothing.
            ObscurialResourceManager.setStress(host, 60f);
            ObscurialServerLogic.stressVent(host);
            check(helper, ObscurialResourceManager.getMastery(host) == ObscurialMastery.VENT_GAIN,
                    () -> "a vent on cooldown still earned mastery");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, host);
        }
    }

    private static void collapseCostsByPath(GameTestHelper helper) {
        ServerPlayer controlled = newHost(helper, "ControlledHost", ConditionOrigin.SUPPRESSED);
        ServerPlayer unleashed = newHost(helper, "UnleashedHost", ConditionOrigin.UNLEASHED);
        try {
            long now = helper.getLevel().getGameTime();
            long base = ObscurialResourceManager.getDefaultLockoutTicks();
            ObscurialResourceManager.setMastery(controlled, 100f);
            ObscurialResourceManager.setMastery(unleashed, 100f);
            float before = unleashed.getHealth();

            ObscurialHeritageHandler.collapse(controlled, HeritageAPI.getData(controlled), helper.getLevel(),
                    ObscurialMastery.Path.CONTROLLED, 100f);
            ObscurialHeritageHandler.collapse(unleashed, HeritageAPI.getData(unleashed), helper.getLevel(),
                    ObscurialMastery.Path.UNLEASHED, 100f);

            long controlledLockout = ObscurialResourceManager.getLockoutUntilTick(controlled) - now;
            long unleashedLockout = ObscurialResourceManager.getLockoutUntilTick(unleashed) - now;
            check(helper, controlledLockout == Math.round(base * 0.5f),
                    () -> "a mastered controlled collapse locked out for " + controlledLockout + ", base " + base);
            check(helper, unleashedLockout == Math.round(base * 1.25f),
                    () -> "an unleashed collapse locked out for " + unleashedLockout + ", base " + base);
            check(helper, unleashed.getHealth() < before, () -> "an unleashed collapse did not wound the host");
            check(helper, controlled.getHealth() == controlled.getMaxHealth(),
                    () -> "a controlled collapse wounded the host");
            check(helper, ObscurialResourceManager.getMastery(controlled) == 100f - ObscurialMastery.COLLAPSE_LOSS,
                    () -> "a collapse left mastery at " + ObscurialResourceManager.getMastery(controlled));
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, controlled);
            WizardTestSupport.retire(helper, unleashed);
        }
    }

    private static void masteryGatesGrasp(GameTestHelper helper) {
        ServerPlayer host = newHost(helper, "GraspingHost", ConditionOrigin.UNLEASHED);
        try {
            HeritageAPI.getData(host).setActiveFormId("obscurial_dark");
            PlayerSpellData spells = host.getData(ModAttachments.SPELL_DATA.get());
            String grasp = ObscurialAbility.GRASP.spellId();

            ObscurialResourceManager.setMastery(host, ObscurialMastery.GRASP_MASTERY - 1f);
            ObscurialServerLogic.useAbility(host, grasp);
            int refused = spells.getRejectCounts().getOrDefault(SpellRejectCodes.ABILITY_REQUIREMENTS_UNMET, 0);
            check(helper, refused == 1, () -> "Grasp below its mastery was not refused: " + spells.getRejectCounts());
            check(helper, spells.getCastCount(grasp) == 0, () -> "Grasp below its mastery was cast");

            ObscurialResourceManager.setMastery(host, ObscurialMastery.GRASP_MASTERY);
            ObscurialServerLogic.useAbility(host, grasp);
            check(helper, spells.getCastCount(grasp) == 1,
                    () -> "Grasp at its mastery was not cast; rejects " + spells.getRejectCounts());
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, host);
        }
    }

    private static void staleForcedBreakIsCleared(GameTestHelper helper) {
        ServerPlayer host = newHost(helper, "StaleBreakHost", ConditionOrigin.SUPPRESSED);
        PlayerHeritageData data = HeritageAPI.getData(host);
        // What a forced break leaves behind when it ends in a collapse instead of running out: a deadline in the past.
        data.setFlag(FORCED_FLAG, "1");
        helper.startSequence()
                .thenWaitUntil(() -> check(helper, data.getFlag(FORCED_FLAG) == null,
                        () -> "the stale forced-break deadline is still set while the host is in human form"))
                .thenExecute(() -> WizardTestSupport.retire(helper, host))
                .thenSucceed();
    }

    private static ServerPlayer newHost(GameTestHelper helper, String name, ConditionOrigin origin) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name, GameType.SURVIVAL);
        WizardTestSupport.parkAtOrigin(helper, player);
        player.setData(ModAttachments.PLAYER_STATS.get(), PlayerStatsData.EMPTY);
        HeritageAPI.commit(player, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
        HeritageAPI.afflict(player, origin);
        return player;
    }
}
