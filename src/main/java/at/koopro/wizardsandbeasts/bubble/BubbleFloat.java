package at.koopro.wizardsandbeasts.bubble;

import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The bubble, and how far it will carry you.
 *
 * <p><b>A tool, not flight.</b> Three separate limits keep it that way, and all three matter: it only
 * lifts while the jump key is <em>held</em>, it stops lifting {@link #MAX_RISE} blocks above where it
 * was blown, and it pops the moment anything hits you. Any one of them on its own would leave a
 * cheap creative-fly; together they make it a way to cross a ravine or reach a ledge and nothing
 * more.
 *
 * <p>The ceiling is measured from where the effect <em>started</em>, not from the ground — so
 * stepping off a cliff mid-float does not hand the player another eight blocks of climb, and neither
 * does chewing at the bottom of a hole.
 */
@NullMarked
public final class BubbleFloat {

    /** How long a bubble lasts. */
    public static final int DURATION_TICKS = 500;    // 25s

    /** Before another one. Long enough that the bubble is a plan rather than a habit. */
    public static final int COOLDOWN_TICKS = 800;    // 40s

    /** How far above the blowing point the bubble will lift. */
    public static final double MAX_RISE = 8.0;

    /**
     * Upward velocity while jump is held.
     *
     * <p>Gentle on purpose — noticeably slower than a jump, so this reads as being carried rather
     * than as flying. Applied as a target the player's motion is eased toward, not as a set, so it
     * cannot be stacked with a jump for a bigger leap.
     */
    public static final double RISE_SPEED = 0.22;

    /** How quickly vertical motion is eased toward the target. */
    public static final float RISE_RESPONSE = 0.35f;

    /** Where each floater's bubble was blown. Cleared when the effect ends, however it ends. */
    private static final PlayerScopedState<Double> ANCHOR_Y =
            PlayerScopedState.create("bubble_float_anchor");

    private BubbleFloat() {}

    /** Records the height a bubble was blown at. */
    public static void anchor(LivingEntity floater) {
        if (floater instanceof net.minecraft.world.entity.player.Player player) {
            ANCHOR_Y.put(player, player.getY());
        }
    }

    /** Forgets the anchor. Safe to call for somebody who never had one. */
    public static void release(LivingEntity floater) {
        if (floater instanceof net.minecraft.world.entity.player.Player player) {
            ANCHOR_Y.remove(player);
        }
    }

    /** The height the bubble was blown at, or {@code null} if this floater has no anchor. */
    public static @Nullable Double anchorOf(LivingEntity floater) {
        return floater instanceof net.minecraft.world.entity.player.Player player
                ? ANCHOR_Y.get(player)
                : null;
    }

    /**
     * Whether a floater at {@code currentY} may still rise from an anchor at {@code anchorY}.
     *
     * <p>Pure, so the one rule that stops this being free flight can be tested without a level.
     */
    public static boolean canRise(double currentY, double anchorY) {
        return currentY < anchorY + MAX_RISE;
    }

    /**
     * The vertical velocity a rising floater should ease toward.
     *
     * <p>Returns the <em>current</em> velocity unchanged once the ceiling is reached, rather than
     * zero — cutting motion dead at the cap would drop the player out of the sky at exactly the
     * moment they were expecting to hover.
     */
    public static double targetVelocity(double currentY, double anchorY, double currentVelocity) {
        return canRise(currentY, anchorY) ? RISE_SPEED : currentVelocity;
    }

    /** The pop: a burst of bubbles and a wet snap. Used by every path that ends a float. */
    public static void pop(LivingEntity floater) {
        floater.level().playSound(null, floater.getX(), floater.getY(), floater.getZ(),
                SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.PLAYERS, 1.0f, 0.8f);
        if (floater.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.BUBBLE_POP,
                    floater.getX(), floater.getY() + floater.getBbHeight() * 0.7, floater.getZ(),
                    40, 0.6, 0.6, 0.6, 0.12);
            level.sendParticles(ParticleTypes.SPLASH,
                    floater.getX(), floater.getY() + floater.getBbHeight() * 0.7, floater.getZ(),
                    20, 0.4, 0.4, 0.4, 0.05);
        }
        release(floater);
    }
}
