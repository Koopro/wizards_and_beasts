package at.koopro.wizardsandbeasts.creature.profile;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The activity loop's fallback chain: the best clip the rig has, else a stand-in from idle and walk. */
class CreatureActivityTest {

    private static CreatureActivity.Loop loop(CreatureActivity a, boolean moving, String... clips) {
        Set<String> has = Set.of(clips);
        return a.loop(moving, has::contains, "walk");
    }

    @Test
    void idleAndWalkAreExactlyTheOldBinaryController() {
        assertEquals(new CreatureActivity.Loop("idle", 1.0), loop(CreatureActivity.IDLE, false));
        assertEquals(new CreatureActivity.Loop("walk", 1.0), loop(CreatureActivity.IDLE, true));
        assertEquals(new CreatureActivity.Loop("walk", 1.0), loop(CreatureActivity.WALK, true));
    }

    @Test
    void aRigWithoutTheClipFakesItFromWhatItHas() {
        assertEquals(new CreatureActivity.Loop("idle", 0.35), loop(CreatureActivity.SLEEP, false));
        assertEquals(new CreatureActivity.Loop("walk", 1.5), loop(CreatureActivity.FLEE, true));
        assertEquals(new CreatureActivity.Loop("walk", 0.6), loop(CreatureActivity.SNEAK, true));
        assertEquals(new CreatureActivity.Loop("idle", 1.0), loop(CreatureActivity.FLEE, false));
    }

    @Test
    void theBestClipTheRigHasWins() {
        assertEquals(new CreatureActivity.Loop("run", 1.0), loop(CreatureActivity.FLEE, true, "run", "walk"));
        assertEquals(new CreatureActivity.Loop("graze", 1.0), loop(CreatureActivity.EAT, false, "graze", "sniff"));
        assertEquals(new CreatureActivity.Loop("rest", 1.0), loop(CreatureActivity.SLEEP, false, "rest"));
        assertEquals(new CreatureActivity.Loop("preen", 1.0), loop(CreatureActivity.GROOM, false, "preen", "shake"));
    }

    @Test
    void anActDoneInPlaceShowsTheWalkWhileItIsStillGettingThere() {
        assertEquals(new CreatureActivity.Loop("walk", 1.0), loop(CreatureActivity.DRINK, true, "graze"));
    }

    @Test
    void theByteRoundTripsAndAStrangeByteIsIdle() {
        for (CreatureActivity a : CreatureActivity.values()) {
            assertEquals(a, CreatureActivity.byId(a.id()));
        }
        assertEquals(CreatureActivity.IDLE, CreatureActivity.byId((byte) 120));
        assertEquals(CreatureActivity.IDLE, CreatureActivity.byId((byte) -1));
    }

    @Test
    void fallingAsleepLiesDownAndGettingUpWakes() {
        assertEquals("lie_down", CreatureActivity.bridge(CreatureActivity.IDLE, CreatureActivity.SLEEP));
        assertEquals("lie_down", CreatureActivity.bridge(CreatureActivity.REST, CreatureActivity.SLEEP));
        assertEquals("wake", CreatureActivity.bridge(CreatureActivity.SLEEP, CreatureActivity.IDLE));
        assertEquals("wake", CreatureActivity.bridge(CreatureActivity.SLEEP, CreatureActivity.FLEE));
    }

    @Test
    void noOtherChangeHasABridge() {
        for (CreatureActivity a : CreatureActivity.values()) {
            for (CreatureActivity b : CreatureActivity.values()) {
                if (a != CreatureActivity.SLEEP && b != CreatureActivity.SLEEP || a == b) {
                    assertNull(CreatureActivity.bridge(a, b), a + " -> " + b);
                }
            }
        }
    }

    @Test
    void theBridgeFiresOnTheControllerGenericCreaturesRegister() {
        assertEquals(at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity.BEAST_ACTION_CONTROLLER,
                at.koopro.wizardsandbeasts.util.AnimHelper.ACTION_CONTROLLER);
    }

    @Test
    void theHeadIsLeftToTheClipOnlyWhileItIsBusy() {
        assertEquals(java.util.EnumSet.of(CreatureActivity.GROOM, CreatureActivity.DRINK, CreatureActivity.EAT),
                java.util.Arrays.stream(CreatureActivity.values()).filter(CreatureActivity::headBusy)
                        .collect(java.util.stream.Collectors.toCollection(() -> java.util.EnumSet.noneOf(CreatureActivity.class))));
    }
}
