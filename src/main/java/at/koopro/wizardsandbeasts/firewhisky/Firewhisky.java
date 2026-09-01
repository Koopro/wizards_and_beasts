package at.koopro.wizardsandbeasts.firewhisky;

import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * How many you have had, and how recently.
 *
 * <p><b>Never pure Strength.</b> A drink that granted Strength and nothing else would be a potion
 * with a different texture, and a worse one than the potion. The first shot already costs a burn; the
 * second and third cost more, and the escalation is the item. Firewhisky is not a buff you keep
 * topped up — it is a decision with a bill attached.
 *
 * <p>Shot times are held in a short per-player deque rather than a single timestamp, because the
 * second and third beats have different windows and "how many in the last two minutes" cannot be
 * derived from "when was the last one".
 */
@NullMarked
public final class Firewhisky {

    // ── the drink ──
    public static final int NUTRITION = 1;
    public static final float SATURATION = 0.1f;

    /** Strength and the fire resistance that comes with a mouthful of burning spirit. */
    public static final int COURAGE_TICKS = 240;      // 12s

    /** The burn, straight after. Heat haze and a bite out of your hunger. */
    public static final int BURN_TICKS = 60;          // 3s

    // ── the escalation ──
    /** A second shot inside this is "one too many". */
    public static final int SECOND_WINDOW_TICKS = 1200;   // 60s
    /** A third inside this is the floor. */
    public static final int THIRD_WINDOW_TICKS = 2400;    // 120s

    /** Nausea and blindness for the second. */
    public static final int SECOND_NAUSEA_TICKS = 300;    // 15s
    public static final int SECOND_BLINDNESS_TICKS = 60;  // 3s

    /** How long a third shot keeps a wizard off their wand. */
    public static final int DRUNK_TICKS = 300;            // 15s

    /** Beyond this, a shot is forgotten entirely. Equals the widest window. */
    private static final int MEMORY_TICKS = THIRD_WINDOW_TICKS;

    /** Recent shots per player, newest last. Never longer than a handful. */
    private static final PlayerScopedState<Deque<Long>> RECENT =
            PlayerScopedState.create("firewhisky_recent");

    private Firewhisky() {}

    /** Which beat this drink is. */
    public enum Round {
        /** The first in a while. Courage and a burn. */
        FIRST,
        /** One too many. Add the room spinning and a moment of dark. */
        SECOND,
        /** The floor. */
        THIRD
    }

    /**
     * Records a shot and reports which beat it landed on.
     *
     * <p>Counts <em>before</em> adding, so the first drink of an evening is {@link Round#FIRST} rather
     * than being counted as one already in you.
     */
    public static Round drink(Player drinker) {
        long now = drinker.level().getGameTime();
        Deque<Long> history = RECENT.computeIfAbsent(drinker.getUUID(), id -> new ArrayDeque<>());
        forget(history, now);

        Round round = classify(timesSince(history, now));
        history.addLast(now);
        return round;
    }

    /** How many shots fall inside each window, as {@code [withinSecond, withinThird]}. */
    public static int[] timesSince(Deque<Long> history, long now) {
        int second = 0;
        int third = 0;
        for (long when : history) {
            long elapsed = now - when;
            if (elapsed < 0L) {
                // Game time moved backwards (a /time set). Treat as ancient rather than as a
                // permanent hangover.
                continue;
            }
            if (elapsed < SECOND_WINDOW_TICKS) {
                second++;
            }
            if (elapsed < THIRD_WINDOW_TICKS) {
                third++;
            }
        }
        return new int[] {second, third};
    }

    /**
     * The beat a shot lands on, given how many are already inside each window.
     *
     * <p>Pure, and the reason it is: the escalation is the whole item, and "does the third shot
     * actually fire" is not something anybody wants to discover by drinking three in a playtest.
     */
    public static Round classify(int[] counts) {
        if (counts[1] >= 2) {
            return Round.THIRD;
        }
        if (counts[0] >= 1) {
            return Round.SECOND;
        }
        return Round.FIRST;
    }

    /** Whether this wizard is currently too drunk to hold a wand steady. */
    public static boolean isDrunk(Player drinker) {
        return drinker.hasEffect(at.koopro.wizardsandbeasts.effect.ModEffects.DRUNK);
    }

    /** Drops shots that are older than any window cares about. */
    private static void forget(Deque<Long> history, long now) {
        List<Long> keep = new ArrayList<>();
        for (long when : history) {
            long elapsed = now - when;
            if (elapsed >= 0L && elapsed < MEMORY_TICKS) {
                keep.add(when);
            }
        }
        history.clear();
        history.addAll(keep);
    }
}
