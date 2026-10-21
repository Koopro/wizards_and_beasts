package at.koopro.wizardsandbeasts.creature.companion;

import at.koopro.wizardsandbeasts.creature.companion.CompanionRules.Protection;
import at.koopro.wizardsandbeasts.creature.companion.CompanionRules.Refusal;
import at.koopro.wizardsandbeasts.creature.companion.CompanionRules.Relation;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The companion arithmetic: stages, obedience, gains and caps, personality, inheritance, protection. */
class CompanionRulesTest {

    private static final CompanionProfile FIGHTER = profile(List.of(
            CompanionCommand.FOLLOW, CompanionCommand.STAY, CompanionCommand.RETURN, CompanionCommand.SEARCH,
            CompanionCommand.DEFEND, CompanionCommand.ATTACK), true);
    private static final CompanionProfile GENTLE = profile(List.of(
            CompanionCommand.FOLLOW, CompanionCommand.STAY, CompanionCommand.DEFEND), false);

    private static CompanionProfile profile(List<CompanionCommand> commands, boolean fight) {
        return new CompanionProfile(List.of(new CompanionProfile.WeightedTrait("brave", 1),
                new CompanionProfile.WeightedTrait("skittish", 1), new CompanionProfile.WeightedTrait("loyal", 2)),
                2, 10, 30, 200, 500, commands, List.of(), fight, Optional.empty(), Optional.empty());
    }

    private static PersonalityTrait trait(Map<Interaction, Float> multipliers, List<CompanionCommand> refuses,
                                          float flee, float training) {
        return new PersonalityTrait(multipliers, refuses, flee, 1.0f, training, false);
    }

    private static final PersonalityTrait SKITTISH = trait(Map.of(Interaction.CARE, 1.5f),
            List.of(CompanionCommand.ATTACK, CompanionCommand.DEFEND), 0.5f, 1.0f);
    private static final PersonalityTrait GREEDY = trait(Map.of(Interaction.GIFT, 2.0f, Interaction.FEED, 0.75f),
            List.of(), 0.0f, 1.0f);
    private static final PersonalityTrait SLOW = trait(Map.of(), List.of(), 0.0f, 0.5f);

    // -- stages ---------------------------------------------------------------------------------------------

    @Test
    void stagesFollowBondAndTraining() {
        assertEquals(CompanionStage.WILD, CompanionRules.stage(FIGHTER, false, 100, 50, 100, 999), "unowned is wild");
        assertEquals(CompanionStage.WILD, CompanionRules.stage(FIGHTER, true, 9, 50, 100, 0));
        assertEquals(CompanionStage.CURIOUS, CompanionRules.stage(FIGHTER, true, 10, 50, 100, 0));
        assertEquals(CompanionStage.TRUSTING, CompanionRules.stage(FIGHTER, true, 30, 50, 100, 0));
        assertEquals(CompanionStage.BONDED, CompanionRules.stage(FIGHTER, true, 50, 50, 100, 199));
        assertEquals(CompanionStage.TRAINED, CompanionRules.stage(FIGHTER, true, 50, 50, 100, 200));
        assertEquals(CompanionStage.TRAINED, CompanionRules.stage(FIGHTER, true, 99, 50, 100, 600),
                "mastery needs the full bond as well as the training");
        assertEquals(CompanionStage.MASTERED, CompanionRules.stage(FIGHTER, true, 100, 50, 100, 500));
    }

    // -- obedience ------------------------------------------------------------------------------------------

    @Test
    void commandsNeedTheSpeciesTheStageAndTheMeansToFight() {
        assertEquals(Refusal.UNKNOWN_TO_SPECIES, CompanionRules.check(CompanionCommand.PATROL, FIGHTER,
                CompanionStage.MASTERED, List.of(), true));
        assertEquals(Refusal.NOT_YET, CompanionRules.check(CompanionCommand.STAY, FIGHTER, CompanionStage.CURIOUS,
                List.of(), true));
        assertEquals(Refusal.OK, CompanionRules.check(CompanionCommand.STAY, FIGHTER, CompanionStage.TRUSTING,
                List.of(), true));
        assertEquals(Refusal.NOT_YET, CompanionRules.check(CompanionCommand.DEFEND, FIGHTER, CompanionStage.BONDED,
                List.of(), true));
        assertEquals(Refusal.OK, CompanionRules.check(CompanionCommand.DEFEND, FIGHTER, CompanionStage.TRAINED,
                List.of(), true));
        assertEquals(Refusal.CANNOT_FIGHT, CompanionRules.check(CompanionCommand.DEFEND, GENTLE, CompanionStage.MASTERED,
                List.of(), false));
    }

    /** Beastmaster capstone: one stage sooner and no temperament refusals — but no new species abilities. */
    @Test
    void aBeastMastersVoiceIsObeyedAStageSooner() {
        assertEquals(Refusal.NOT_YET, CompanionRules.check(CompanionCommand.DEFEND, FIGHTER, CompanionStage.BONDED,
                List.of(), true, false));
        assertEquals(Refusal.OK, CompanionRules.check(CompanionCommand.DEFEND, FIGHTER, CompanionStage.BONDED,
                List.of(), true, true), "a bonded creature defends a Beastmaster");
        assertEquals(Refusal.OK, CompanionRules.check(CompanionCommand.STAY, FIGHTER, CompanionStage.CURIOUS,
                List.of(), true, true), "a curious creature stays for a Beastmaster");
        assertEquals(Refusal.OK, CompanionRules.check(CompanionCommand.ATTACK, FIGHTER, CompanionStage.TRAINED,
                List.of(SKITTISH), true, true), "no temperament refuses a Beastmaster");
        assertEquals(Refusal.TEMPERAMENT, CompanionRules.check(CompanionCommand.ATTACK, FIGHTER, CompanionStage.TRAINED,
                List.of(SKITTISH), true, false));
        assertEquals(Refusal.CANNOT_FIGHT, CompanionRules.check(CompanionCommand.DEFEND, GENTLE, CompanionStage.MASTERED,
                List.of(), false, true), "the voice persuades; it does not grow claws");
        assertEquals(Refusal.UNKNOWN_TO_SPECIES, CompanionRules.check(CompanionCommand.PATROL, FIGHTER,
                CompanionStage.MASTERED, List.of(), true, true));
        assertEquals(CompanionStage.WILD, CompanionRules.oneStageFurther(CompanionStage.WILD),
                "a wild creature has no owner to obey");
        assertEquals(CompanionStage.MASTERED, CompanionRules.oneStageFurther(CompanionStage.MASTERED));
    }

    @Test
    void aSkittishCreatureRefusesToFightUntilMastered() {
        assertEquals(Refusal.TEMPERAMENT, CompanionRules.check(CompanionCommand.ATTACK, FIGHTER, CompanionStage.TRAINED,
                List.of(SKITTISH), true));
        assertEquals(Refusal.OK, CompanionRules.check(CompanionCommand.ATTACK, FIGHTER, CompanionStage.MASTERED,
                List.of(SKITTISH), true));
        assertEquals(Refusal.OK, CompanionRules.check(CompanionCommand.RETURN, FIGHTER, CompanionStage.BONDED,
                List.of(SKITTISH), true), "temperament only refuses what the trait names");
    }

    @Test
    void combatCommandsAreNotOfferedToACreatureThatCannotFight() {
        assertEquals(List.of(CompanionCommand.FOLLOW, CompanionCommand.STAY), CompanionRules.offered(GENTLE, false));
        assertTrue(CompanionRules.offered(FIGHTER, true).contains(CompanionCommand.ATTACK));
        assertFalse(CompanionRules.offered(FIGHTER, false).contains(CompanionCommand.ATTACK));
    }

    // -- gains ----------------------------------------------------------------------------------------------

    @Test
    void personalityScalesBondAndTheDailyCapLimitsHowOften() {
        CompanionRules.Gain greedyGift = CompanionRules.bondGain(Interaction.GIFT, 8, List.of(GREEDY), 0);
        assertEquals(16, greedyGift.bond(), "greedy values a gift twice as much");
        assertEquals(8, greedyGift.units(), "but it still uses the day's one gift");
        assertEquals(CompanionRules.Gain.NONE, CompanionRules.bondGain(Interaction.GIFT, 8, List.of(GREEDY), 8),
                "one favourite treat a day, whatever the temperament");
        assertEquals(new CompanionRules.Gain(2, 2), CompanionRules.bondGain(Interaction.CARE, 3, List.of(), 4),
                "only what is left of the cap counts");
        assertEquals(new CompanionRules.Gain(3, 2), CompanionRules.bondGain(Interaction.CARE, 3, List.of(SKITTISH), 4),
                "and personality scales what is left");
        assertEquals(8, CompanionRules.bondGain(Interaction.FEED, 10, List.of(GREEDY), 999).bond(),
                "feeding has no cap of its own");
        assertEquals(1, CompanionRules.bondGain(Interaction.EXPLORE, 1, List.of(trait(Map.of(Interaction.EXPLORE, 0.1f),
                List.of(), 0f, 1f)), 0).bond(), "a positive gain never rounds to nothing");
    }

    @Test
    void trainingIsCappedPerDayAndScaledByTemperament() {
        assertEquals(10, CompanionRules.trainingGain(List.of(), 0));
        assertEquals(5, CompanionRules.trainingGain(List.of(SLOW), 0));
        assertEquals(0, CompanionRules.trainingGain(List.of(), CompanionRules.TRAINING_DAILY_CAP));
        assertEquals(3, CompanionRules.trainingGain(List.of(), CompanionRules.TRAINING_DAILY_CAP - 3));
    }

    @Test
    void theMostCautiousTraitDecidesWhenItFlees() {
        assertEquals(0.5f, CompanionRules.fleeBelow(List.of(GREEDY, SKITTISH)));
        assertEquals(0.0f, CompanionRules.fleeBelow(List.of(GREEDY)));
    }

    // -- personality ----------------------------------------------------------------------------------------

    @Test
    void rollingGivesDistinctKnownTraits() {
        for (int seed = 0; seed < 50; seed++) {
            List<String> rolled = CompanionRules.roll(FIGHTER.traitPool(), 2, new Random(seed), t -> !t.equals("brave"));
            assertEquals(2, rolled.size());
            assertFalse(rolled.contains("brave"), "an unknown trait was rolled");
            assertEquals(2, rolled.stream().distinct().count());
        }
        assertEquals(List.of(), CompanionRules.roll(FIGHTER.traitPool(), 2, new Random(1), t -> false));
    }

    @Test
    void youngTakeAfterTheirParents() {
        int fromParents = 0;
        for (int seed = 0; seed < 200; seed++) {
            List<String> child = CompanionRules.inherit(List.of("brave"), List.of("loyal"), FIGHTER.traitPool(), 2,
                    0.0f, new Random(seed), t -> true);
            assertEquals(List.of("brave", "loyal"), child, "with no mutation a child is one trait from each parent");
            fromParents++;
        }
        assertEquals(200, fromParents);
        int mutated = 0;
        for (int seed = 0; seed < 400; seed++) {
            List<String> child = CompanionRules.inherit(List.of("brave"), List.of("loyal"), FIGHTER.traitPool(), 2,
                    1.0f, new Random(seed), t -> true);
            assertEquals(2, child.size());
            assertEquals("brave", child.getFirst(), "the first parent's trait always carries");
            if (!child.get(1).equals("loyal")) {
                mutated++;
            }
        }
        assertTrue(mutated > 0, "full mutation chance never produced a fresh trait");
    }

    // -- protection -----------------------------------------------------------------------------------------

    @Test
    void protectionMatrix() {
        for (Protection rule : Protection.values()) {
            for (boolean pvp : new boolean[]{true, false}) {
                assertTrue(CompanionRules.mayHarm(Relation.OWNER, rule, pvp));
                assertTrue(CompanionRules.mayHarm(Relation.HOSTILE, rule, pvp));
                assertFalse(CompanionRules.mayHarm(Relation.TRUSTED, rule, pvp), "a trusted friend may never");
            }
        }
        assertFalse(CompanionRules.mayHarm(Relation.STRANGER, Protection.OWNER_ONLY, true),
                "owner-only protects even on a PvP server");
        assertTrue(CompanionRules.mayHarm(Relation.STRANGER, Protection.PVP_FLAG, true));
        assertFalse(CompanionRules.mayHarm(Relation.STRANGER, Protection.PVP_FLAG, false));
        assertTrue(CompanionRules.mayHandle(Relation.TRUSTED));
        assertFalse(CompanionRules.mayHandle(Relation.STRANGER));
        assertFalse(CompanionRules.mayHandle(Relation.HOSTILE));
    }

    // -- memory ---------------------------------------------------------------------------------------------

    @Test
    void dailyCapsResetOnANewDay() {
        CompanionMemory memory = CompanionMemory.EMPTY.withGain(Interaction.CARE, 4, 10);
        assertEquals(4, memory.gainedToday(Interaction.CARE, 10));
        assertEquals(0, memory.gainedToday(Interaction.CARE, 11));
        CompanionMemory next = memory.withGain(Interaction.CARE, 2, 11);
        assertEquals(2, next.gainedToday(Interaction.CARE, 11), "yesterday's gain does not carry into today");
    }

    @Test
    void historyIsBounded() {
        CompanionMemory memory = CompanionMemory.EMPTY;
        for (int i = 0; i < 40; i++) {
            memory = memory.remember(i, "k", Integer.toString(i));
        }
        assertEquals(CompanionMemory.MAX_HISTORY, memory.history().size());
        assertEquals("39", memory.history().getLast().arg());
    }

    @Test
    void trustAndHostilityAreExclusive() {
        UUID other = UUID.randomUUID();
        CompanionMemory memory = CompanionMemory.EMPTY.withTrust(other, true).withHostile(other, true);
        assertTrue(memory.hates(other));
        assertFalse(memory.trusts(other));
    }

    @Test
    void memoryAndRosterRoundTrip() {
        UUID other = UUID.randomUUID();
        CompanionMemory memory = CompanionMemory.EMPTY.withTraits(List.of("brave", "loyal"))
                .withMode(CompanionCommand.STAY, new BlockPos(1, 2, 3)).withTraining(120, 20, 5)
                .withTrust(other, true).withLineage(2, List.of("Pip", "Moss"), 4)
                .remember(5, "companion.wizards_and_beasts.history.born", "Pip & Moss")
                .withGain(Interaction.CARE, 3, 5).withReady("ability", 900L).withStage(CompanionStage.TRAINED);
        CompanionMemory back = CompanionMemory.CODEC.parse(JsonOps.INSTANCE,
                CompanionMemory.CODEC.encodeStart(JsonOps.INSTANCE, memory).getOrThrow()).getOrThrow();
        assertEquals(memory, back);
    }
}
