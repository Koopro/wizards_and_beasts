package at.koopro.wizardsandbeasts.animagus;

import com.mojang.serialization.Codec;
import org.jspecify.annotations.NullMarked;

/**
 * Where a form's canon standing comes from. Records provenance only — it gates nothing and
 * modifies nothing. Kept so a later editorial pass can sort book-canon forms from the wider
 * Wizarding World material without re-deriving it.
 */
@NullMarked
public enum AnimagusCanonTier {

    /** Attested in the seven novels. */
    BOOK,

    /** Attested in Pottermore / Wizarding World material only. */
    POTTERMORE;

    public static final Codec<AnimagusCanonTier> CODEC =
            Codec.STRING.xmap(s -> valueOf(s.toUpperCase(java.util.Locale.ROOT)),
                    t -> t.name().toLowerCase(java.util.Locale.ROOT));
}
