package at.koopro.wizardsandbeasts.standing.gate;

import at.koopro.wizardsandbeasts.standing.StandingAxis;
import at.koopro.wizardsandbeasts.standing.StandingBand;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * A window on one axis that a player must be inside.
 *
 * <p>Expressed in {@link StandingBand}s rather than in raw numbers, on purpose. A gate written as
 * "tradition ≥ 42" would mean something different on a server that retuned the axis bound, and would
 * flicker open and shut as a value drifted across the line. A band is stable under retuning and moves
 * in steps, so a locked node stays locked until the player has genuinely changed.
 *
 * @param axis    which axis
 * @param minBand lowest band that satisfies this, inclusive; absent means no floor
 * @param maxBand highest band that satisfies this, inclusive; absent means no ceiling. Present so a
 *                gate can require the <em>absence</em> of a leaning — "not a traditionalist" is a real
 *                requirement, and without a ceiling it could only be written as a floor on the
 *                opposite pole, which is not the same statement.
 */
@NullMarked
public record StandingRequirement(StandingAxis axis,
                                  @Nullable StandingBand minBand,
                                  @Nullable StandingBand maxBand) {

    public static final Codec<StandingRequirement> CODEC = RecordCodecBuilder.<StandingRequirement>create(
            instance -> instance.group(
                    StandingAxis.CODEC.fieldOf("axis").forGetter(StandingRequirement::axis),
                    StandingBand.CODEC.optionalFieldOf("minBand")
                            .forGetter(r -> Optional.ofNullable(r.minBand())),
                    StandingBand.CODEC.optionalFieldOf("maxBand")
                            .forGetter(r -> Optional.ofNullable(r.maxBand()))
            ).apply(instance, (axis, min, max) ->
                    new StandingRequirement(axis, min.orElse(null), max.orElse(null))))
            .validate(StandingRequirement::validate);

    private static DataResult<StandingRequirement> validate(StandingRequirement requirement) {
        if (requirement.minBand == null && requirement.maxBand == null) {
            return DataResult.error(() -> "standing requirement on axis '"
                    + requirement.axis.getSerializedName() + "' has neither minBand nor maxBand, so it "
                    + "admits everyone and gates nothing");
        }
        if (requirement.minBand != null && requirement.maxBand != null
                && requirement.minBand.step() > requirement.maxBand.step()) {
            return DataResult.error(() -> "standing requirement on axis '"
                    + requirement.axis.getSerializedName() + "' has minBand above maxBand, so no player "
                    + "could ever satisfy it");
        }
        return DataResult.success(requirement);
    }

    /** Whether a player currently in {@code band} on this axis satisfies the window. */
    public boolean isSatisfiedBy(StandingBand band) {
        if (minBand != null && band.step() < minBand.step()) {
            return false;
        }
        return maxBand == null || band.step() <= maxBand.step();
    }

    /**
     * What the player has to do about it, for the refusal toast. Names the axis and the pole rather
     * than the band, because "you need to be further toward Traditionalist" is actionable and
     * "requires LEANING_POSITIVE" is not.
     */
    public Component describe() {
        if (minBand != null && minBand.isPositive()) {
            return Component.translatable("standing.wizards_and_beasts.requirement.toward",
                    axis.displayName(), axis.positivePole());
        }
        if (maxBand != null && maxBand.isNegative()) {
            return Component.translatable("standing.wizards_and_beasts.requirement.toward",
                    axis.displayName(), axis.negativePole());
        }
        if (minBand != null && minBand.isNegative() && maxBand != null && maxBand.isPositive()) {
            return Component.translatable("standing.wizards_and_beasts.requirement.neutral",
                    axis.displayName());
        }
        if (maxBand != null) {
            return Component.translatable("standing.wizards_and_beasts.requirement.away",
                    axis.displayName(), axis.positivePole());
        }
        return Component.translatable("standing.wizards_and_beasts.requirement.away",
                axis.displayName(), axis.negativePole());
    }
}
