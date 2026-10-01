package at.koopro.wizardsandbeasts.heritage.rules;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

/**
 * An administrator's override of one heritage's rules. Every field is optional: empty means "as the mod ships
 * it", so removing a field restores the shipped behaviour exactly and a later release that finishes a heritage
 * is not masked by a stale copy of the old flag.
 *
 * <p>Covers only rules some existing code path already decides in one place: whether a new character may
 * choose the heritage (the selection packet's gate) and whether the heritage's own voluntary change of shape
 * may begin ({@code HeritageTransformService}). Nothing here is a second copy of a value read elsewhere.
 *
 * @param selectable     whether new characters may choose this heritage at the onboarding gate
 * @param transformation whether this heritage's plain two-form change may begin
 */
@NullMarked
public record HeritageRule(Optional<Boolean> selectable, Optional<Boolean> transformation) {

    public static final HeritageRule NONE = new HeritageRule(Optional.empty(), Optional.empty());

    public static final Codec<HeritageRule> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.BOOL.optionalFieldOf("selectable").forGetter(HeritageRule::selectable),
            Codec.BOOL.optionalFieldOf("transformation").forGetter(HeritageRule::transformation)
    ).apply(inst, HeritageRule::new));

    public boolean isEmpty() {
        return selectable.isEmpty() && transformation.isEmpty();
    }

    public HeritageRule withSelectable(Optional<Boolean> value) {
        return new HeritageRule(value, transformation);
    }

    public HeritageRule withTransformation(Optional<Boolean> value) {
        return new HeritageRule(selectable, value);
    }
}
