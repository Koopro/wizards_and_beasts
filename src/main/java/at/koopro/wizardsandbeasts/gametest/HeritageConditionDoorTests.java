package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.heritage.HeritageAdminService;
import at.koopro.wizardsandbeasts.creature.wildlife.LycanthropyInfection;
import at.koopro.wizardsandbeasts.heritage.ConditionOrigin;
import at.koopro.wizardsandbeasts.heritage.ConditionRules;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageAssignment;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.stats.PlayerStatsData;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.EnumSet;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * The one condition slot, on live players. Each scenario is a state the mod used to reach without a word: a werewolf's
 * bite replacing an Obscurus, an operator's {@code condition set} replacing a condition, a werewolf reassigned to a
 * people that cannot carry the curse, and a cured Obscurial left with the dark form's flight.
 *
 * <p>Survival players throughout: a creative player keeps flight whatever happens, so the cure scenario would pass
 * without the fix (lesson: creative players make "nothing happened" assertions vacuous).
 */
public final class HeritageConditionDoorTests {

    private static final AdminContext CONSOLE =
            AdminContext.detached(null, "game-test", EnumSet.allOf(AdminCapability.class));

    private HeritageConditionDoorTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("heritage_bite_finds_no_room_beside_an_obscurus",
                "heritage: a werewolf's bite does not replace an Obscurus",
                HeritageConditionDoorTests::biteFindsNoRoomBesideAnObscurus);
        tests.add("heritage_condition_door_never_overwrites",
                "heritage: a second condition is refused and the first one kept",
                HeritageConditionDoorTests::conditionDoorNeverOverwrites);
        tests.add("heritage_reassign_refuses_to_strand_a_condition",
                "heritage: a werewolf cannot be reassigned to a people that cannot carry the curse",
                HeritageConditionDoorTests::reassignRefusesToStrandACondition);
        tests.add("heritage_cure_gives_back_the_dark_forms_flight",
                "heritage: curing an Obscurial takes away the dark form's flight and meters",
                HeritageConditionDoorTests::cureGivesBackTheDarkFormsFlight);
    }

    private static void biteFindsNoRoomBesideAnObscurus(GameTestHelper helper) {
        ServerPlayer host = newWizard(helper, "ObscurialBitten");
        try {
            HeritageAPI.commit(host, Heritage.WIZARDKIND, HeritageVariant.PURE_BLOOD);
            HeritageAPI.afflict(host, ConditionOrigin.SUPPRESSED);

            boolean took = LycanthropyInfection.bitten(host, 3f, true);

            PlayerHeritageData data = HeritageAPI.getData(host);
            check(helper, !took, () -> "the bite reported that the curse took hold");
            check(helper, data.getCondition() == ConditionOrigin.SUPPRESSED,
                    () -> "the bite replaced the Obscurus with " + data.getCondition());
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, host);
        }
    }

    private static void conditionDoorNeverOverwrites(GameTestHelper helper) {
        ServerPlayer wolf = newWizard(helper, "DoorWolf");
        try {
            HeritageAPI.commit(wolf, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
            check(helper, HeritageAssignment.afflict(wolf, ConditionOrigin.BITTEN) == null,
                    () -> "a half-blood wizard with nothing could not be bitten");

            ConditionRules.Refusal other = HeritageAssignment.afflict(wolf, ConditionOrigin.UNLEASHED);
            ConditionRules.Refusal same = HeritageAssignment.afflict(wolf, ConditionOrigin.SAVAGE_BITTEN);

            check(helper, other == ConditionRules.Refusal.OTHER_CONDITION,
                    () -> "an Obscurus on a werewolf was answered " + other);
            check(helper, same == ConditionRules.Refusal.ALREADY_CARRIES,
                    () -> "a second bite origin was answered " + same);
            check(helper, HeritageAPI.getData(wolf).getCondition() == ConditionOrigin.BITTEN,
                    () -> "a refused condition still changed the slot to " + HeritageAPI.getData(wolf).getCondition());
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, wolf);
        }
    }

    private static void reassignRefusesToStrandACondition(GameTestHelper helper) {
        ServerPlayer wolf = newWizard(helper, "StrandedWolf");
        try {
            HeritageAPI.commit(wolf, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
            HeritageAPI.afflict(wolf, ConditionOrigin.BITTEN);
            PlayerHeritageData data = HeritageAPI.getData(wolf);
            int points = data.getProfessionPoints();

            HeritageAdminService.Outcome outcome = HeritageAdminService.assign(CONSOLE,
                    helper.getLevel().getServer(), wolf.getUUID(), "goblin", HeritageVariant.GOBLIN_COMMON.getId(),
                    true);
            ConditionRules.Refusal direct = HeritageAssignment.assign(wolf, Heritage.GOBLIN,
                    HeritageVariant.GOBLIN_COMMON);

            check(helper, !outcome.success() && outcome.messageKey().endsWith("condition_incompatible"),
                    () -> "the Control Center answered " + outcome);
            check(helper, direct == ConditionRules.Refusal.WOULD_ORPHAN,
                    () -> "the assignment door answered " + direct);
            check(helper, data.getSelectedHeritage() == Heritage.WIZARDKIND
                            && data.getCondition() == ConditionOrigin.BITTEN,
                    () -> "a refused reassignment changed them to " + data.getSelectedHeritage() + " carrying "
                            + data.getCondition());
            check(helper, data.getProfessionPoints() == points,
                    () -> "a refused reassignment reset profession points to " + data.getProfessionPoints());

            // Clearing the condition first is the way through, and it works.
            HeritageAPI.cure(wolf);
            check(helper, HeritageAssignment.assign(wolf, Heritage.GOBLIN, HeritageVariant.GOBLIN_COMMON) == null
                            && data.getSelectedHeritage() == Heritage.GOBLIN,
                    () -> "a cured wizard could not be reassigned to Goblin");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, wolf);
        }
    }

    private static void cureGivesBackTheDarkFormsFlight(GameTestHelper helper) {
        ServerPlayer host = newWizard(helper, "CuredHost");
        try {
            HeritageAPI.commit(host, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
            HeritageAPI.afflict(host, ConditionOrigin.UNLEASHED);
            PlayerHeritageData data = HeritageAPI.getData(host);
            // What the dark form leaves on a host (ObscurialHeritageHandler.updateDarkFormFlight and the meters).
            host.getAbilities().mayfly = true;
            host.getAbilities().flying = true;
            host.onUpdateAbilities();
            data.setFlag("obscurial_flight_granted", "true");
            data.setFlag("obscurial_stress", "64.0");

            HeritageAPI.cure(host);

            check(helper, !host.getAbilities().mayfly && !host.getAbilities().flying,
                    () -> "the cured host can still fly");
            check(helper, data.getCustomFlags().keySet().stream().noneMatch(key -> key.startsWith("obscurial_")),
                    () -> "obscurial flags survived the cure: " + data.getCustomFlags().keySet());
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, host);
        }
    }

    private static ServerPlayer newWizard(GameTestHelper helper, String name) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name, GameType.SURVIVAL);
        WizardTestSupport.parkAtOrigin(helper, player);
        player.setData(ModAttachments.PLAYER_STATS.get(), PlayerStatsData.EMPTY);
        return player;
    }
}
