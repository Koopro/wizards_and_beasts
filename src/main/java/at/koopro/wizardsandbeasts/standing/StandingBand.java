package at.koopro.wizardsandbeasts.standing;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/**
 * How far along an axis a wizard has travelled, in five steps. Shared by every
 * {@link StandingAxis}; the words each axis puts on a step come from {@link StandingAxis#bandName}.
 *
 * <p>Bands exist so that everything downstream reacts to a <em>step</em> rather than to a raw float.
 * Gates, notices and the sheet all read a band, which means a value drifting by a tenth cannot spam
 * a toast or flicker a lock — the same reason {@code WantedLevel} bands notoriety.
 */
@NullMarked
public enum StandingBand implements StringRepresentable {

    STRONG_NEGATIVE(-2),
    LEANING_NEGATIVE(-1),
    NEUTRAL(0),
    LEANING_POSITIVE(1),
    STRONG_POSITIVE(2);

    /** Serializes as the lowercase constant name, so a datapack gate reads {@code "leaning_positive"}. */
    public static final Codec<StandingBand> CODEC = StringRepresentable.fromEnum(StandingBand::values);

    private final int step;

    StandingBand(int step) {
        this.step = step;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** −2 … +2. Ordered so comparisons read as "further toward the positive pole". */
    public int step() {
        return step;
    }

    /** True for the two bands on the negative side; {@link #NEUTRAL} is neither. */
    public boolean isNegative() {
        return step < 0;
    }

    public boolean isPositive() {
        return step > 0;
    }
}
