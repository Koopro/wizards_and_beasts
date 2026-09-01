package at.koopro.wizardsandbeasts.felix;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * How lucky a wizard currently is.
 *
 * <h2>Why this is not a MobEffect</h2>
 * <p>Felix Felicis shipped as Luck II plus a couple of decorations, and that is the thing this class
 * exists to stop being. A {@code MobEffect} can only say "for N ticks, this modifier applies" — it has
 * no room for a strength that came from how well the potion was brewed, no room for an internal
 * cooldown so a near-death save cannot fire twice in a second, and no room for the memory that you
 * drank one an hour ago and should not have another yet. Luck II is a number on a loot roll. Felix is
 * supposed to be a run of good fortune.
 *
 * <h2>Fields</h2>
 * <ul>
 *   <li>{@code ticksRemaining} — counts down while the potion is working; 0 means it is not.</li>
 *   <li>{@code strength} — 1 to {@link FelixFortune#MAX_STRENGTH}, from the brew. Scales every roll.</li>
 *   <li>{@code cooldownUntil} — game time before which another dose is refused or punished. Outlives
 *       the luck itself, which is the whole point of it.</li>
 *   <li>{@code lastSaveGameTime} — when the combat hook last pulled somebody out of the fire. Its own
 *       field rather than a separate map, because it has exactly the same lifetime as the rest of
 *       this and a second store would be a second thing to clear.</li>
 * </ul>
 *
 * <p>Immutable. Every transition returns a new state, so there is no way to half-apply one — a
 * partially-mutated Felix state is exactly the kind of bug that would show up as "sometimes the
 * cooldown does not stick".
 */
@NullMarked
public record FelixState(int ticksRemaining, int strength, long cooldownUntil, long lastSaveGameTime) {

    public static final FelixState NONE = new FelixState(0, 0, 0L, Long.MIN_VALUE);

    public static final Codec<FelixState> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.optionalFieldOf("ticksRemaining", 0).forGetter(FelixState::ticksRemaining),
            Codec.INT.optionalFieldOf("strength", 0).forGetter(FelixState::strength),
            Codec.LONG.optionalFieldOf("cooldownUntil", 0L).forGetter(FelixState::cooldownUntil),
            Codec.LONG.optionalFieldOf("lastSaveGameTime", Long.MIN_VALUE)
                    .forGetter(FelixState::lastSaveGameTime)
    ).apply(inst, FelixState::new));

    /** Whether the potion is currently working. */
    public boolean isActive() {
        return ticksRemaining > 0;
    }

    /**
     * Whether another dose is still barred at {@code gameTime}.
     *
     * <p>Guarded against a negative gap because game time can move backwards across a world restore,
     * and an unguarded comparison would leave a player barred for however far back it went.
     */
    public boolean isOnCooldown(long gameTime) {
        return cooldownUntil > gameTime && cooldownUntil - gameTime <= FelixFortune.MAX_SANE_COOLDOWN;
    }

    public FelixState withTicks(int ticks) {
        return new FelixState(Math.max(0, ticks), strength, cooldownUntil, lastSaveGameTime);
    }

    public FelixState withSaveAt(long gameTime) {
        return new FelixState(ticksRemaining, strength, cooldownUntil, gameTime);
    }

    /** The state after the luck runs out: no luck, no strength, but the cooldown remembers. */
    public FelixState expired(long cooldownUntil) {
        return new FelixState(0, 0, cooldownUntil, lastSaveGameTime);
    }
}
