package at.koopro.wizardsandbeasts.creature.variant;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * One visual variant of a creature — a coat. Implemented by the creatures' own variant enums
 * ({@code HippogriffEntity.Coat}, {@code KelpieEntity.Coat}, {@code NifflerEntity.Coat}), which stay the single
 * source of what variants exist: their ordinal is the synced and saved identity, and the renderer picks the
 * texture from the same constant.
 *
 * <p>What this adds is a common reading of them — id, texture, authored weight — so the variant roll, the admin
 * rules and the Creature Lab can treat every variant-bearing creature alike without a second list.
 */
@NullMarked
public interface CreatureVariant {

    /** Supplied by every enum. */
    String name();

    /** Supplied by every enum; the synced and saved identity. */
    int ordinal();

    /** Stable, lowercase: {@code storm_grey}. Used in setting ids and commands. */
    default String variantId() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Texture sub-path under {@code textures/entity/}, or null for the creature's base texture. */
    @Nullable String variantTexture();

    /** Relative spawn weight as authored in code. Equal weights mean the roll was uniform. */
    default int authoredWeight() {
        return 1;
    }
}
