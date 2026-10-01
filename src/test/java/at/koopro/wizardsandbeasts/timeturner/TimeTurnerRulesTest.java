package at.koopro.wizardsandbeasts.timeturner;

import at.koopro.wizardsandbeasts.timeturner.TimeTurnerRules.Moment;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static at.koopro.wizardsandbeasts.timeturner.TimeTurnerRules.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** "Each turn is an hour back" — and only through hours the Time-Turner has spent with its wearer. */
class TimeTurnerRulesTest {

    private static Moment at(long time) {
        return new Moment(time, "minecraft:overworld", time, 64, 0);
    }

    /** A trail recorded every {@link TimeTurnerRules#RECORD_EVERY_TICKS} from {@code from} to {@code to}. */
    private static List<Moment> trail(long from, long to) {
        List<Moment> moments = new ArrayList<>();
        for (long t = from; t <= to; t += RECORD_EVERY_TICKS) {
            moments.add(at(t));
        }
        return moments;
    }

    @Test
    void windingCountsWholeTurnsUpToTheMost() {
        assertEquals(0, turns(0));
        assertEquals(0, turns(WIND_TICKS_PER_TURN - 1));
        assertEquals(1, turns(WIND_TICKS_PER_TURN));
        assertEquals(MAX_TURNS, turns(WIND_TICKS_PER_TURN * 50));
    }

    @Test
    void eachTurnIsAnHourBackToWhereTheWearerStood() {
        List<Moment> trail = trail(0, 5000);
        Optional<Moment> one = momentFor(trail, 5000, 1);
        assertEquals(4000, one.orElseThrow().time());
        assertEquals(2000, momentFor(trail, 5000, 3).orElseThrow().time());
        // Between records: the latest place at or before the hour, never one after it.
        assertEquals(3980, momentFor(trail, 4990, 1).orElseThrow().time());
    }

    @Test
    void itCannotReachHoursItHasNotSpentWithTheWearer() {
        List<Moment> halfAnHour = trail(4500, 5000);
        assertTrue(momentFor(halfAnHour, 5000, 1).isEmpty());
        assertTrue(momentFor(List.of(), 5000, 1).isEmpty());
        assertTrue(momentFor(trail(0, 5000), 5000, 0).isEmpty(), "not wound, nowhere to go");
    }

    @Test
    void theTrailCoversTheLongestTrip() {
        assertTrue(TRAIL_CAPACITY * (long) RECORD_EVERY_TICKS >= MAX_TURNS * TICKS_PER_TURN,
                "a full trail must reach back every hour the Time-Turner allows");
    }
}
