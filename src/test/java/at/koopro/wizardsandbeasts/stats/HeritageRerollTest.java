package at.koopro.wizardsandbeasts.stats;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins what changing heritage is allowed to cost a player.
 *
 * <p>{@code PlayerStatsData.withHeritageRoll} is the arithmetic half of
 * {@code PlayerStatsAPI.rerollHeritagePower}, split out for the same reason
 * {@link StatTrainingScaler} was: the interesting part needs no {@code Player} and the part that
 * does is three lines of plumbing.
 *
 * <p>The behaviour under test is a fix, not a refinement. Both routes into a heritage change —
 * {@code /wandb player heritage set} and {@code /wandb player stats reroll_power} — used to be written as
 * "replace the block with {@code EMPTY}, then run the new-player initialiser", because that initialiser was
 * the only thing in the mod that rolled. It refuses to run on a non-empty block, so the wipe was load
 * bearing, and it took PRECISION, REFLEXES, WILLPOWER and all four training accumulators with it. Changing
 * heritage is a change of lineage; it is not an amnesia spell.
 */
class HeritageRerollTest {

    private static PlayerStatsData trainedWizard() {
        Map<PlayerStat, Integer> values = new EnumMap<>(PlayerStat.class);
        values.put(PlayerStat.POWER, 42);
        values.put(PlayerStat.PRECISION, 61);
        values.put(PlayerStat.REFLEXES, 37);
        values.put(PlayerStat.WILLPOWER, 55);

        Map<PlayerStat, Float> training = new EnumMap<>(PlayerStat.class);
        training.put(PlayerStat.PRECISION, 0.4f);
        training.put(PlayerStat.REFLEXES, 0.9f);

        return new PlayerStatsData(values, true, 9, training);
    }

    @Test
    void rerollKeepsEveryTrainedStat() {
        PlayerStatsData rerolled = trainedWizard().withHeritageRoll(70, false);

        assertEquals(61, rerolled.precision(), "PRECISION was earned, not granted by heritage");
        assertEquals(37, rerolled.reflexes(), "REFLEXES was earned, not granted by heritage");
        assertEquals(55, rerolled.willpower(), "WILLPOWER was earned, not granted by heritage");
    }

    @Test
    void rerollKeepsTrainingAccumulators() {
        PlayerStatsData rerolled = trainedWizard().withHeritageRoll(70, false);

        assertEquals(0.4f, rerolled.trainingProgress().get(PlayerStat.PRECISION), 1.0e-6f);
        assertEquals(0.9f, rerolled.trainingProgress().get(PlayerStat.REFLEXES), 1.0e-6f,
                "a nearly-earned point survives a change of lineage");
    }

    @Test
    void rerollReplacesThePowerRollAndTheProdigyFlag() {
        PlayerStatsData rerolled = trainedWizard().withHeritageRoll(70, false);

        assertEquals(70, rerolled.power());
        assertFalse(rerolled.isProdigy(), "prodigy belongs to the roll, so a re-roll can take it away");
        assertTrue(PlayerStatsData.EMPTY.withHeritageRoll(88, true).isProdigy(),
                "...and can give it");
    }

    /**
     * The allowance is spent against a band, and the band is what just changed. Carrying it over would hand
     * a Centaur the growth a Squib had already spent — or, the other way round, let a player bank growth on
     * a generous band and cash it on a mean one.
     */
    @Test
    void rerollResetsTheGrowthAllowance() {
        assertEquals(0, trainedWizard().withHeritageRoll(70, false).powerGrowthAccumulated());
    }

    @Test
    void rerollClampsThePowerItIsGiven() {
        assertEquals(PlayerStatsData.MAX_VALUE, PlayerStatsData.EMPTY.withHeritageRoll(5000, false).power());
        assertEquals(PlayerStatsData.MIN_VALUE, PlayerStatsData.EMPTY.withHeritageRoll(-5, false).power());
    }

    /**
     * Clearing the roll is what lets the next selection roll at all: {@code initializeStatsForNewPlayer}
     * refuses any block that is not at defaults, and an old heritage's POWER is enough to make it refuse.
     * A player who has never trained anything must come out of a heritage reset genuinely empty.
     */
    @Test
    void clearingTheRollOnAnUntrainedPlayerLeavesTheBlockEmpty() {
        PlayerStatsData rolled = PlayerStatsData.EMPTY.withHeritageRoll(64, true);
        assertFalse(rolled.isEmpty());
        assertTrue(rolled.withHeritageRoll(0, false).isEmpty(),
                "a reset heritage must leave a block the next selection is willing to roll into");
    }

    /** ...and a player who has trained keeps that, so the block is correctly *not* empty afterwards. */
    @Test
    void clearingTheRollOnATrainedPlayerKeepsTheirTraining() {
        PlayerStatsData cleared = trainedWizard().withHeritageRoll(0, false);

        assertEquals(0, cleared.power());
        assertEquals(61, cleared.precision());
        assertFalse(cleared.isEmpty(),
                "trained stats survive losing a heritage, so the block is legitimately non-empty");
    }
}
