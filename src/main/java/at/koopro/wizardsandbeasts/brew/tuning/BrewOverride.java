package at.koopro.wizardsandbeasts.brew.tuning;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

/**
 * An administrator's override of one brew. Empty fields mean "as the datapack defines it".
 *
 * @param enabled false: no cauldron starts this brew, and a bottle of it cannot be drunk
 * @param effects replaces the brew's drink-time effect list, in {@link BrewEffectText} form. Only for brews whose
 *                effects are one {@code apply_effects} component — the component the potion system applies
 */
@NullMarked
public record BrewOverride(Optional<Boolean> enabled, Optional<String> effects) {

    public static final BrewOverride NONE = new BrewOverride(Optional.empty(), Optional.empty());

    public static final Codec<BrewOverride> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.BOOL.optionalFieldOf("enabled").forGetter(BrewOverride::enabled),
            Codec.STRING.optionalFieldOf("effects").forGetter(BrewOverride::effects)
    ).apply(inst, BrewOverride::new));

    public boolean isEmpty() {
        return enabled.isEmpty() && effects.isEmpty();
    }

    public BrewOverride withEnabled(Optional<Boolean> value) {
        return new BrewOverride(value, effects);
    }

    public BrewOverride withEffects(Optional<String> value) {
        return new BrewOverride(enabled, value);
    }
}
