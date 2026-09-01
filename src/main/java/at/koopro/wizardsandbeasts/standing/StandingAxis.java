package at.koopro.wizardsandbeasts.standing;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/**
 * Where a wizard stands in magical society, on three bipolar axes.
 *
 * <p>The design rule that keeps this from becoming four disconnected bars: <b>an axis is stored only
 * if nothing already stores it.</b> Two of the three are computed from data the mod has held all
 * along, so there is exactly one new pair of numbers in the save.
 *
 * <ul>
 *   <li>{@link #TRADITION} is genuinely new and is the only axis stored whole.</li>
 *   <li>{@link #ALIGNMENT} is {@code light − darkCorruption}. The corruption meter predates this
 *       system and keeps all four of its writers — Unforgivables, Horcrux, Resurrection Stone,
 *       Riddle's diary — it simply becomes the dark half of a bipolar axis instead of a lone
 *       one-way counter you could never come back from.</li>
 *   <li>{@link #MINISTRY} is a view over {@code PlayerMinistryRecord}: rank credit minus notoriety.
 *       Nothing about it is stored here, because the criminal record already is.</li>
 * </ul>
 *
 * <p>Every axis runs {@code −bound … +bound} with zero as genuine neutrality, so one band table and
 * one meter widget serve all three. The pole names differ per axis and live in lang.
 */
@NullMarked
public enum StandingAxis implements StringRepresentable {

    /**
     * Blood-status politics: how the player's conduct reads against the Sacred Twenty-Eight's world.
     * Negative is reformist, positive is traditionalist — a direction of travel, not a birthright.
     * A Muggle-born who spends their life in old pure-blood ritual drifts positive, and a pure-blood
     * who does not drifts negative; the heritage variant sets where you start, never where you end.
     */
    TRADITION("tradition", 0xFFB79BE8),

    /** Dark ↔ Light. Composed, never stored — see the class note. */
    ALIGNMENT("alignment", 0xFFE0C060),

    /** How the Ministry regards you. Derived from the criminal record. */
    MINISTRY("ministry", 0xFF7FA8D0);

    public static final Codec<StandingAxis> CODEC = StringRepresentable.fromEnum(StandingAxis::values);

    private final String serializedName;
    private final int color;

    StandingAxis(String serializedName, int color) {
        this.serializedName = serializedName;
        this.color = color;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    /** Sheet colour for this axis' meter. */
    public int color() {
        return color;
    }

    /**
     * True when this axis is a number in the save rather than a computation. Only {@link #TRADITION}
     * is; a deed that tries to write a derived axis is a datapack error, not a silent no-op.
     */
    public boolean isStored() {
        return this == TRADITION;
    }

    public Component displayName() {
        return Component.translatable("standing.wizards_and_beasts.axis." + serializedName);
    }

    /** The name of this axis' negative pole, e.g. "Reformist". */
    public Component negativePole() {
        return Component.translatable("standing.wizards_and_beasts.axis." + serializedName + ".negative");
    }

    /** The name of this axis' positive pole, e.g. "Traditionalist". */
    public Component positivePole() {
        return Component.translatable("standing.wizards_and_beasts.axis." + serializedName + ".positive");
    }

    /**
     * The band's name <em>on this axis</em>. {@link StandingBand} is the shared shape — five steps from
     * one pole to the other — but each axis names its steps differently, because "Dark" and "Wanted"
     * and "Traditionalist" are the same position and not remotely the same word.
     */
    public Component bandName(StandingBand band) {
        return Component.translatable("standing.wizards_and_beasts.axis." + serializedName
                + ".band." + band.name().toLowerCase(Locale.ROOT));
    }

    public static @org.jspecify.annotations.Nullable StandingAxis byName(String name) {
        for (StandingAxis axis : values()) {
            if (axis.serializedName.equals(name)) {
                return axis;
            }
        }
        return null;
    }
}
