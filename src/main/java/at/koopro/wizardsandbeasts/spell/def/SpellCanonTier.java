package at.koopro.wizardsandbeasts.spell.def;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

/**
 * Where a spell's incantation is attested, ordered by the project's canon hierarchy
 * (books &gt; companion books &gt; films &gt; Pottermore &gt; expanded &gt; original).
 *
 * <p>Pure provenance metadata. Nothing in the cast pipeline, learning gates, or Gamp's Law
 * validation reads this — it exists so a lore-purist server operator and future contributors can
 * tell a book incantation from a video-game one, which is otherwise invisible in the datapack.
 *
 * <p>There is deliberately no "unknown" constant: an untriaged spell omits {@code canonTier}
 * entirely, so absence carries that meaning without a value that could be mistaken for a ruling.
 *
 * @see SpellDefinition#canonTier()
 */
public enum SpellCanonTier implements StringRepresentable {
    /** Attested in the seven main novels. */
    BOOKS("books"),
    /** Attested in a Scamander/Whisp companion book. */
    COMPANION("companion"),
    /** Attested on screen but not on the page. */
    FILM("film"),
    /** Attested in Pottermore / Wizarding World authorial writing. */
    POTTERMORE("pottermore"),
    /** Attested only in licensed expanded material such as the video games. */
    EXPANDED("expanded"),
    /** No attestation in any canon source — original to this mod. */
    ORIGINAL("original");

    public static final Codec<SpellCanonTier> CODEC = StringRepresentable.fromEnum(SpellCanonTier::values);

    private final String serializedName;

    SpellCanonTier(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public @NonNull String getSerializedName() {
        return serializedName;
    }
}
