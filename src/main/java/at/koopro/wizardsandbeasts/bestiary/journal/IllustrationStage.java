package at.koopro.wizardsandbeasts.bestiary.journal;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * How much of a creature the journal's plate shows. One stage per depth of knowledge, so the picture fills in the way
 * the notes do (documentation/BESTIARY_ILLUSTRATION_SPEC.md):
 *
 * <ul>
 *   <li>{@link #SILHOUETTE} — met it: a shape in ink, nothing more;</li>
 *   <li>{@link #SKETCH} — watched it: a rough graphite field sketch;</li>
 *   <li>{@link #PORTRAIT} — studied it: a coloured portrait;</li>
 *   <li>{@link #STUDIED} — knows it: the finished plate, annotated;</li>
 *   <li>{@link #SIGNATURE} — the illustration of its signature behaviour, beside the plate once it is known.</li>
 * </ul>
 * An undiscovered creature has no stage at all: not even its outline is the player's to see.
 */
@NullMarked
public enum IllustrationStage implements StringRepresentable {
    SILHOUETTE,
    SKETCH,
    PORTRAIT,
    STUDIED,
    SIGNATURE;

    public static final Codec<IllustrationStage> CODEC = StringRepresentable.fromEnum(IllustrationStage::values);

    /** The plate a tier earns, or {@code null} for an undiscovered creature. */
    public static @Nullable IllustrationStage forTier(DiscoveryTier tier) {
        return switch (tier) {
            case UNKNOWN -> null;
            case ENCOUNTERED -> SILHOUETTE;
            case OBSERVED -> SKETCH;
            case STUDIED -> PORTRAIT;
            case KNOWN -> STUDIED;
        };
    }

    /** The stage before this one, for falling back to the nearest art that exists. */
    public @Nullable IllustrationStage previous() {
        return this == SILHOUETTE || this == SIGNATURE ? null : values()[ordinal() - 1];
    }

    /** Where art for this stage lives by convention: {@code textures/gui/bestiary/journal/<stage>/<entry>.png}. */
    public Identifier conventionalTexture(Identifier entryId) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID,
                "textures/gui/bestiary/journal/" + getSerializedName() + "/" + entryId.getPath() + ".png");
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
