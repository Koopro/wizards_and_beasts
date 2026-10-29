package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.ability.AbilityIds;
import at.koopro.wizardsandbeasts.heritage.ConditionOrigin;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageTransformService;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.vampire.VampireBloodAPI;
import at.koopro.wizardsandbeasts.heritage.vampire.VampireSiring;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.stats.PlayerStatsData;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.List;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * Vampirism acquired in play: a sire's offer, the other player's acceptance, and every way it is refused without
 * anything changing. Survival players throughout, because the blood pool and the sun both exempt creative.
 */
public final class VampireSiringTests {

    private VampireSiringTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("vampire_sire_offer_then_accept_turns_a_wizard",
                "vampire: a sire's offer taken by the other player turns them, and costs the sire blood",
                VampireSiringTests::offerThenAcceptTurnsAWizard);
        tests.add("vampire_sire_never_turns_without_both_acts",
                "vampire: no offer without a crouch, no turning without the other player's acceptance",
                VampireSiringTests::neverTurnsWithoutBothActs);
        tests.add("vampire_sire_refuses_who_cannot_carry_it",
                "vampire: a werewolf or a Goblin is refused, and the sire keeps their blood",
                VampireSiringTests::refusesWhoCannotCarryIt);
        tests.add("vampire_sire_turned_veela_keeps_her_people",
                "vampire: a turned Veela is still a Veela, thirsts, and her shape is the bat",
                VampireSiringTests::turnedVeelaKeepsHerPeople);
    }

    private static void offerThenAcceptTurnsAWizard(GameTestHelper helper) {
        ServerPlayer sire = bornVampire(helper, "SireBorn");
        ServerPlayer wizard = newPlayer(helper, "WillingWizard");
        try {
            HeritageAPI.commit(wizard, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
            long now = helper.getLevel().getGameTime();
            float before = VampireBloodAPI.getBlood(sire);

            sire.setShiftKeyDown(true);
            wizard.setShiftKeyDown(true);
            VampireSiring.Outcome offered = VampireSiring.interact(sire, wizard, now);
            VampireSiring.Outcome taken = VampireSiring.interact(wizard, sire, now + 20);

            check(helper, offered == VampireSiring.Outcome.OFFERED, () -> "the sire's crouch was answered " + offered);
            check(helper, taken == VampireSiring.Outcome.TURNED, () -> "the acceptance was answered " + taken);
            check(helper, HeritageAPI.getData(wizard).getCondition() == ConditionOrigin.SIRED,
                    () -> "the wizard carries " + HeritageAPI.getData(wizard).getCondition());
            check(helper, HeritageAPI.getData(wizard).getSelectedHeritage() == Heritage.WIZARDKIND,
                    () -> "turning changed the wizard's people");
            check(helper, VampireBloodAPI.drinksBlood(wizard), () -> "the turned wizard still eats food");
            check(helper, VampireBloodAPI.getData(wizard).getBlood() == VampireBloodAPI.getData(wizard).getMaxBlood(),
                    () -> "the turned wizard woke without a seeded pool");
            check(helper, Math.abs(before - VampireSiring.SIRE_COST - VampireBloodAPI.getBlood(sire)) < 0.01f,
                    () -> "the sire paid " + (before - VampireBloodAPI.getBlood(sire)) + ", not " + VampireSiring.SIRE_COST);
            check(helper, !VampireSiring.hasOffer(wizard, sire), () -> "the taken offer is still open");

            List<String> grants = new ArrayList<>();
            HeritageTransformService.grantsFor(wizard, grants);
            check(helper, grants.contains(AbilityIds.VAMPIRE_FORM.toString()),
                    () -> "the turned wizard was not given the bat: " + grants);
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, sire);
            WizardTestSupport.retire(helper, wizard);
        }
    }

    private static void neverTurnsWithoutBothActs(GameTestHelper helper) {
        ServerPlayer sire = bornVampire(helper, "SireImpatient");
        ServerPlayer wizard = newPlayer(helper, "UnwillingWizard");
        try {
            HeritageAPI.commit(wizard, Heritage.WIZARDKIND, HeritageVariant.PURE_BLOOD);
            long now = helper.getLevel().getGameTime();

            sire.setShiftKeyDown(false);
            check(helper, VampireSiring.interact(sire, wizard, now) == VampireSiring.Outcome.NONE,
                    () -> "a standing click made an offer");

            sire.setShiftKeyDown(true);
            VampireSiring.interact(sire, wizard, now);
            // The sire clicking again is another offer, never an acceptance on the wizard's behalf.
            VampireSiring.interact(sire, wizard, now + 5);
            check(helper, HeritageAPI.getData(wizard).getCondition() == null,
                    () -> "the sire alone turned the wizard");

            // Taken too late: the offer lapses and nothing is written.
            wizard.setShiftKeyDown(true);
            VampireSiring.Outcome late = VampireSiring.interact(wizard, sire, now + 5 + VampireSiring.OFFER_TICKS + 1);
            check(helper, late == VampireSiring.Outcome.REFUSED, () -> "a lapsed offer was answered " + late);
            check(helper, HeritageAPI.getData(wizard).getCondition() == null,
                    () -> "a lapsed offer still turned the wizard");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, sire);
            WizardTestSupport.retire(helper, wizard);
        }
    }

    private static void refusesWhoCannotCarryIt(GameTestHelper helper) {
        ServerPlayer sire = bornVampire(helper, "SireRefused");
        ServerPlayer wolf = newPlayer(helper, "BittenWizard");
        ServerPlayer goblin = newPlayer(helper, "GoblinBanker");
        try {
            HeritageAPI.commit(wolf, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
            HeritageAPI.afflict(wolf, ConditionOrigin.BITTEN);
            HeritageAPI.commit(goblin, Heritage.GOBLIN, HeritageVariant.GOBLIN_COMMON);
            float before = VampireBloodAPI.getBlood(sire);
            long now = helper.getLevel().getGameTime();
            sire.setShiftKeyDown(true);

            VampireSiring.Outcome toWolf = VampireSiring.interact(sire, wolf, now);
            VampireSiring.Outcome toGoblin = VampireSiring.interact(sire, goblin, now);
            // Even an acceptance forced past the offer is asked again at the door.
            VampireSiring.Outcome forced = VampireSiring.accept(sire, goblin);

            check(helper, toWolf == VampireSiring.Outcome.REFUSED && toGoblin == VampireSiring.Outcome.REFUSED
                            && forced == VampireSiring.Outcome.REFUSED,
                    () -> "refusals were answered " + toWolf + ", " + toGoblin + ", " + forced);
            check(helper, HeritageAPI.getData(wolf).getCondition() == ConditionOrigin.BITTEN,
                    () -> "the werewolf now carries " + HeritageAPI.getData(wolf).getCondition());
            check(helper, HeritageAPI.getData(goblin).getCondition() == null, () -> "the Goblin was turned");
            check(helper, VampireBloodAPI.getBlood(sire) == before, () -> "a refused siring cost the sire blood");

            // A sire too thirsty to give is refused before anything is offered.
            VampireBloodAPI.setBlood(sire, VampireSiring.SIRE_COST - 1f);
            ServerPlayer wizard = newPlayer(helper, "ThirstySireTarget");
            try {
                HeritageAPI.commit(wizard, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
                VampireSiring.Outcome thirsty = VampireSiring.interact(sire, wizard, now);
                check(helper, thirsty == VampireSiring.Outcome.REFUSED && !VampireSiring.hasOffer(wizard, sire),
                        () -> "a thirsty sire's offer was answered " + thirsty);
            } finally {
                WizardTestSupport.retire(helper, wizard);
            }
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, sire);
            WizardTestSupport.retire(helper, wolf);
            WizardTestSupport.retire(helper, goblin);
        }
    }

    private static void turnedVeelaKeepsHerPeople(GameTestHelper helper) {
        ServerPlayer sire = bornVampire(helper, "SireOfVeela");
        ServerPlayer veela = newPlayer(helper, "TurnedVeela");
        try {
            HeritageAPI.commit(veela, Heritage.VEELA, HeritageVariant.VEELA_FULL);
            long now = helper.getLevel().getGameTime();
            sire.setShiftKeyDown(true);
            veela.setShiftKeyDown(true);
            VampireSiring.interact(sire, veela, now);
            VampireSiring.Outcome taken = VampireSiring.interact(veela, sire, now + 1);

            check(helper, taken == VampireSiring.Outcome.TURNED, () -> "the Veela's acceptance was answered " + taken);
            var data = HeritageAPI.getData(veela);
            check(helper, data.getSelectedHeritage() == Heritage.VEELA
                            && data.getSelectedHeritageVariant() == HeritageVariant.VEELA_FULL,
                    () -> "turning changed her to " + data.getSelectedHeritage() + "/" + data.getSelectedHeritageVariant());
            check(helper, data.hasTrait("allure"), () -> "the turned Veela lost her allure");
            check(helper, VampireBloodAPI.drinksBlood(veela), () -> "the turned Veela does not thirst");

            List<String> grants = new ArrayList<>();
            HeritageTransformService.grantsFor(veela, grants);
            check(helper, grants.contains(AbilityIds.VAMPIRE_FORM.toString())
                            && !grants.contains(AbilityIds.VEELA_FORM.toString()),
                    () -> "the turned Veela's shape buttons are " + grants);

            // The cure, an operator's eraser, gives the food back.
            HeritageAPI.cure(veela);
            check(helper, !VampireBloodAPI.drinksBlood(veela), () -> "a cured Veela still thirsts");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, sire);
            WizardTestSupport.retire(helper, veela);
        }
    }

    private static ServerPlayer bornVampire(GameTestHelper helper, String name) {
        ServerPlayer sire = newPlayer(helper, name);
        HeritageAPI.commit(sire, Heritage.VAMPIRE, HeritageVariant.VAMPIRE_BORN);
        return sire;
    }

    private static ServerPlayer newPlayer(GameTestHelper helper, String name) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name, GameType.SURVIVAL);
        WizardTestSupport.parkAtOrigin(helper, player);
        player.setData(ModAttachments.PLAYER_STATS.get(), PlayerStatsData.EMPTY);
        return player;
    }
}
