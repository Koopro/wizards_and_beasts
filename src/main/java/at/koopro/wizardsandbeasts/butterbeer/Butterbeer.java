package at.koopro.wizardsandbeasts.butterbeer;

import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NullMarked;

/**
 * What a mug of Butterbeer is worth, and how often it is worth it.
 *
 * <p>The interesting rule is the second one. Butterbeer is cheap, stacks, and hands out three
 * effects — so without a brake it is simply a better potion that you drink constantly. The brake is
 * deliberately <em>not</em> an item cooldown: a cooldown says "you cannot", which reads as the game
 * being broken when you are holding a drink and cannot drink it. This lets you drink as much as you
 * like and quietly stops stacking the effects, which is how a second pint actually works.
 *
 * <h2>Nothing here is drunkenness</h2>
 * Butterbeer barely affects humans in the books, and a wizarding mod that models it as a debuff is a
 * mod that cannot be run for children. Warmth and Mellow are both pleasant; the only unpleasant
 * outcome in the whole item is a config-gated queasiness from downing two mugs inside thirty
 * seconds, and it ships off.
 */
@NullMarked
public final class Butterbeer {

    /** Hunger restored, as briefed. */
    public static final int NUTRITION = 2;
    /** Saturation restored, as briefed. */
    public static final float SATURATION = 0.4f;

    /** Warmth: no more freezing, and a golden steam that says so. */
    public static final int WARMTH_TICKS = 1800;        // 90s
    /** The Regeneration the drink already granted, kept exactly as it was. */
    public static final int REGENERATION_TICKS = 160;   // 8s
    /** Mellow: neutral things stop minding you, and the world goes slightly soft at the edges. */
    public static final int MELLOW_TICKS = 900;         // 45s

    /**
     * How long a mug's effects are considered still in you.
     *
     * <p>Drink inside this and the second mug is food and nothing else. Two minutes is longer than
     * Warmth's ninety seconds on purpose: the window has to outlast the longest effect, or a patient
     * player could chain Warmth indefinitely by waiting for it to lapse.
     */
    public static final int EFFECT_WINDOW_TICKS = 2400; // 120s

    /** Downing a second mug faster than this is what the optional queasiness answers to. */
    public static final int GULP_WINDOW_TICKS = 600;    // 30s

    /** Ticks of Nausea for a fast second mug, when the server has that switched on. */
    public static final int GULP_NAUSEA_TICKS = 100;    // 5s

    /** Game time each player last finished a mug. */
    private static final PlayerScopedState<Long> LAST_DRINK_TICK =
            PlayerScopedState.create("butterbeer_last_drink");

    private Butterbeer() {}

    /** What a mug does for this player right now. */
    public enum Pour {
        /** The full round: hunger, Warmth, Regeneration and Mellow. */
        FULL,
        /** Too soon. Hunger only — the effects already in you do not stack. */
        REFILL_ONLY
    }

    /** Whether this mug pours a full round or just tops up the drinker's hunger. */
    public static Pour pourFor(Player player) {
        return withinWindow(player, EFFECT_WINDOW_TICKS) ? Pour.REFILL_ONLY : Pour.FULL;
    }

    /** Whether this mug is being knocked back on top of a very recent one. */
    public static boolean isGulping(Player player) {
        return withinWindow(player, GULP_WINDOW_TICKS);
    }

    /** Records that a mug was finished. Call after reading {@link #pourFor}. */
    public static void markDrunk(Player player) {
        LAST_DRINK_TICK.put(player, player.level().getGameTime());
    }

    /**
     * Whether the last mug was inside {@code window} ticks.
     *
     * <p>A negative elapsed time — which happens when a world's game time is set backwards by a
     * command — counts as "not recent" rather than as a permanent lockout.
     */
    private static boolean withinWindow(Player player, int window) {
        Long last = LAST_DRINK_TICK.get(player);
        if (last == null) {
            return false;
        }
        long elapsed = player.level().getGameTime() - last;
        return elapsed >= 0L && elapsed < window;
    }
}
