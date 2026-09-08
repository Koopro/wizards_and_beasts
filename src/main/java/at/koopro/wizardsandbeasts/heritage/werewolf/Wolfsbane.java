package at.koopro.wizardsandbeasts.heritage.werewolf;

import at.koopro.wizardsandbeasts.effect.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NullMarked;

/**
 * Dosing a werewolf, from code.
 *
 * <p>Two ways a dose can reach a player, and they are authoritative over different things:
 * <ul>
 *   <li><b>The brewed potion</b> — {@code data/wizards_and_beasts/brews/wolfsbane_potion.json}. Its
 *       duration lives in that file, because a brew's strength is a datapack's business in this mod and
 *       always has been ({@code BrewReloadListener}).</li>
 *   <li><b>This class</b> — every dose applied by code: the admin command, and anything an addon calls.
 *       These read {@link WerewolfConfig#wolfsbaneDurationTicks} and
 *       {@link WerewolfConfig#wolfsbaneAmplifierExtendsDuration}.</li>
 * </ul>
 *
 * <p>Amplifier extends rather than deepens, and that is a statement about the mechanic: there is no
 * scale on which a mind is more or less its own, so a better brew can only last longer. A server that
 * disagrees turns the flag off and every dose is the base duration.
 */
@NullMarked
public final class Wolfsbane {

    private Wolfsbane() {}

    /** Doses at full strength for the configured duration. */
    public static void apply(LivingEntity target) {
        apply(target, 0);
    }

    /**
     * Doses at {@code amplifier}.
     *
     * @param amplifier 0-based, as vanilla counts them; each level doubles the duration while
     *                  {@link WerewolfConfig#wolfsbaneAmplifierExtendsDuration} is on
     */
    public static void apply(LivingEntity target, int amplifier) {
        target.addEffect(new MobEffectInstance(
                ModEffects.WOLFSBANE, durationFor(amplifier), Math.max(0, amplifier),
                false, true, true));
    }

    /**
     * The duration a dose at {@code amplifier} lasts.
     *
     * <p>Clamped at a day of ticks. Doubling per level is a fast curve, and without a ceiling an
     * amplifier a command could plausibly pass turns into an overflow rather than a long night.
     */
    public static int durationFor(int amplifier) {
        int base = WerewolfConfig.wolfsbaneDurationTicks;
        if (!WerewolfConfig.wolfsbaneAmplifierExtendsDuration || amplifier <= 0) {
            return base;
        }
        long scaled = (long) base << Math.min(amplifier, 20);
        return (int) Math.min(scaled, 24000L * 10L);
    }
}
