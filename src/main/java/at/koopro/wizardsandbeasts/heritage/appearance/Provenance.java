package at.koopro.wizardsandbeasts.heritage.appearance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * Where an appearance entry's design came from — a canon citation, or an admission that there isn't
 * one.
 *
 * <p>The field exists because this mod has twice shipped invented detail that later read as canon:
 * the vampire's appearance rests on roughly two lines about Sanguini in <i>Half-Blood Prince</i>, and
 * the human exertion flare has no textual basis at all. Recording which is which at the data layer
 * means a later reader can tell a sourced decision from an extrapolated one without archaeology.
 *
 * <p><b>Exactly one of the two must be present.</b> A citation plus a fan-extrapolation flag is a
 * contradiction, and neither is an entry nobody has thought about. Both are rejected at datapack
 * load rather than at review time.
 */
public record Provenance(Optional<String> citation, boolean fanExtrapolation) {

    public static final Codec<Provenance> CODEC = RecordCodecBuilder.<Provenance>create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("citation").forGetter(Provenance::citation),
            Codec.BOOL.optionalFieldOf("fanExtrapolation", false).forGetter(Provenance::fanExtrapolation)
    ).apply(instance, Provenance::new)).validate(Provenance::validate);

    /** A sourced entry. {@code citation} is book + chapter, e.g. {@code "Goblet of Fire, ch. 8"}. */
    public static Provenance cited(String citation) {
        return new Provenance(Optional.of(citation), false);
    }

    /** An entry with no textual basis. Must be labelled in {@code MIGRATION_DELTAS.md} as well. */
    public static Provenance extrapolated() {
        return new Provenance(Optional.empty(), true);
    }

    private static DataResult<Provenance> validate(Provenance provenance) {
        boolean hasCitation = provenance.citation.isPresent() && !provenance.citation.get().isBlank();
        if (hasCitation == provenance.fanExtrapolation) {
            return DataResult.error(() -> hasCitation
                    ? "provenance carries both a citation and fanExtrapolation=true; pick one"
                    : "provenance carries neither a citation nor fanExtrapolation=true");
        }
        return DataResult.success(provenance);
    }
}
