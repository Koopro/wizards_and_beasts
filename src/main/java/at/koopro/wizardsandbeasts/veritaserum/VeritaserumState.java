package at.koopro.wizardsandbeasts.veritaserum;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * How long a wizard is still unable to lie about who they are.
 *
 * <h2>Why this is not a MobEffect</h2>
 * <p>Unlike {@code WolfsbaneEffect}, which only has to be present or absent, Veritaserum has to be
 * consulted from inside two <em>refusals</em> — {@code PolyjuiceService.drink} and the Occlumency
 * read in {@code LegilimencyServerLogic}. A mob effect would have served for that. What it could not
 * serve for is the rule that a dose cannot be topped up: {@link #dosedAtGameTime} outlives nothing,
 * but it is what lets the service tell a fresh dose from a re-dose without a second store, in the
 * same shape {@code FelixState} uses for its cooldown.
 *
 * <p>Kept deliberately small. Truth is a duration and nothing else — there is no strength, because
 * three drops force the truth from anyone and a better-brewed batch forcing it harder is not a thing
 * the fiction says.
 *
 * @param ticksRemaining how long the compulsion lasts; 0 means none
 * @param dosedAtGameTime when the current dose began, for the re-dose check
 */
@NullMarked
public record VeritaserumState(int ticksRemaining, long dosedAtGameTime) {

    public static final VeritaserumState NONE = new VeritaserumState(0, Long.MIN_VALUE);

    public static final Codec<VeritaserumState> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.optionalFieldOf("ticksRemaining", 0).forGetter(VeritaserumState::ticksRemaining),
            Codec.LONG.optionalFieldOf("dosedAtGameTime", Long.MIN_VALUE)
                    .forGetter(VeritaserumState::dosedAtGameTime)
    ).apply(inst, VeritaserumState::new));

    /** Whether the drinker is currently compelled. */
    public boolean isActive() {
        return ticksRemaining > 0;
    }

    public VeritaserumState withTicks(int ticks) {
        return new VeritaserumState(Math.max(0, ticks), dosedAtGameTime);
    }
}
