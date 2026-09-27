package at.koopro.wizardsandbeasts.entity.beast;

import at.koopro.wizardsandbeasts.entity.beast.PhoenixRebirth.Phase;
import at.koopro.wizardsandbeasts.entity.beast.PhoenixRebirth.Transition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The burning, without a world: one way in, one clock, each transition exactly once, and a save resumes it. */
class PhoenixRebirthTest {

    @Test
    void aBurningRunsAshesThenRisingThenAliveOnce() {
        PhoenixRebirth rebirth = new PhoenixRebirth();
        assertTrue(rebirth.begin());
        List<Transition> seen = new ArrayList<>();
        for (int i = 0; i < PhoenixRebirth.ASHES_TICKS + PhoenixRebirth.RISING_TICKS + 50; i++) {
            Transition t = rebirth.tick();
            if (t != Transition.NONE) {
                seen.add(t);
            }
        }
        assertEquals(List.of(Transition.RISE, Transition.REBORN), seen, "each transition fires exactly once");
        assertEquals(Phase.ALIVE, rebirth.phase());
    }

    @Test
    void theAshesLastTheirTimeAndTheRisingItsOwn() {
        PhoenixRebirth rebirth = new PhoenixRebirth();
        rebirth.begin();
        for (int i = 1; i < PhoenixRebirth.ASHES_TICKS; i++) {
            assertEquals(Transition.NONE, rebirth.tick());
        }
        assertEquals(Transition.RISE, rebirth.tick());
        assertEquals(Phase.RISING, rebirth.phase());
        for (int i = 1; i < PhoenixRebirth.RISING_TICKS; i++) {
            assertEquals(Transition.NONE, rebirth.tick());
        }
        assertEquals(Transition.REBORN, rebirth.tick());
    }

    @Test
    void aBurningCannotStartTwice() {
        PhoenixRebirth rebirth = new PhoenixRebirth();
        assertTrue(rebirth.begin());
        assertFalse(rebirth.begin(), "a second death hook while in ashes starts nothing");
        rebirth.tick();
        assertFalse(rebirth.begin());
        assertEquals(PhoenixRebirth.ASHES_TICKS - 1, rebirth.ticksLeft(), "and does not reset the clock");
    }

    @Test
    void anAlivePhoenixIgnoresTheClock() {
        PhoenixRebirth rebirth = new PhoenixRebirth();
        for (int i = 0; i < 500; i++) {
            assertEquals(Transition.NONE, rebirth.tick());
        }
        assertFalse(rebirth.burning());
    }

    @Test
    void aSavedBurningResumesWhereItWas() {
        PhoenixRebirth before = new PhoenixRebirth();
        before.begin();
        for (int i = 0; i < 40; i++) {
            before.tick();
        }
        PhoenixRebirth after = new PhoenixRebirth();
        after.restore(before.phase(), before.ticksLeft());
        assertEquals(Phase.ASHES, after.phase());
        assertEquals(before.ticksLeft(), after.ticksLeft());
        assertFalse(after.begin(), "a restored burning is still a burning");
    }

    @Test
    void aDamagedSaveCannotWedgeTheBird() {
        PhoenixRebirth rebirth = new PhoenixRebirth();
        rebirth.restore(Phase.ASHES, -7);
        assertEquals(1, rebirth.ticksLeft(), "clamped to finish next tick, not never");
        assertEquals(Transition.RISE, rebirth.tick());
        rebirth.restore(Phase.RISING, 99_999);
        assertEquals(PhoenixRebirth.RISING_TICKS, rebirth.ticksLeft());
        rebirth.restore(Phase.ALIVE, 50);
        assertEquals(0, rebirth.ticksLeft());
    }
}
