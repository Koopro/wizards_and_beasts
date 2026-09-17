package at.koopro.wizardsandbeasts.ministry.trace;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/**
 * How far a piece of magic broke the International Statute of Secrecy.
 *
 * <p>Measured by what Muggles actually saw, not by what was cast. Lumos in an empty wood is {@link #LOW}:
 * visible magic, nobody to see it. The same Lumos in front of a villager is {@link #MODERATE}. A blast in
 * front of a crowd is {@link #SEVERE}, and dark magic or a dangerous beast in front of any Muggle is
 * {@link #EXTREME}.
 */
@NullMarked
public enum Exposure implements StringRepresentable {

    /** Nothing a Muggle could have seen — unseen magic, or inside a place Muggles cannot find. */
    NONE(0),
    /** Visible magic with no Muggle there to see it. */
    LOW(0),
    /** A visible effect near a Muggle. The Obliviators tidy it up. */
    MODERATE(1),
    /** A large magical event seen by several Muggles. */
    SEVERE(2),
    /** Dark magic or a dangerous creature seen by Muggles. */
    EXTREME(3);

    public static final Codec<Exposure> CODEC = StringRepresentable.fromEnum(Exposure::values);

    private final int gravity;

    Exposure(int gravity) {
        this.gravity = gravity;
    }

    public int gravity() {
        return gravity;
    }

    /** True once a Muggle saw something the Ministry has to clean up. */
    public boolean breachesSecrecy() {
        return ordinal() >= MODERATE.ordinal();
    }

    public boolean atLeast(Exposure other) {
        return ordinal() >= other.ordinal();
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
