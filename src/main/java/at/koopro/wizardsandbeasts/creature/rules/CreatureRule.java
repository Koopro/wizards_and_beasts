package at.koopro.wizardsandbeasts.creature.rules;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * An administrator's override of one creature's rules. Empty fields mean "as the mod and its datapacks ship it".
 *
 * <p>Covers only decisions the game already makes in one place: whether a natural spawn attempt for this creature
 * may succeed (the placement check) and how its variant is rolled when it first spawns. Spawn weight, group size
 * and biomes are datapack data baked into the biomes at world load and are shown, not overridden.
 *
 * @param naturalSpawn   false refuses every natural / chunk-generation spawn of this creature
 * @param variantEnabled variant id → false excludes it from the spawn roll
 * @param variantWeight  variant id → replaces its authored roll weight
 */
@NullMarked
public record CreatureRule(Optional<Boolean> naturalSpawn, Map<String, Boolean> variantEnabled,
                           Map<String, Integer> variantWeight) {

    public static final CreatureRule NONE = new CreatureRule(Optional.empty(), Map.of(), Map.of());

    public static final Codec<CreatureRule> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.BOOL.optionalFieldOf("naturalSpawn").forGetter(CreatureRule::naturalSpawn),
            Codec.unboundedMap(Codec.STRING, Codec.BOOL).optionalFieldOf("variantEnabled", Map.of())
                    .forGetter(CreatureRule::variantEnabled),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("variantWeight", Map.of())
                    .forGetter(CreatureRule::variantWeight)
    ).apply(inst, CreatureRule::new));

    public CreatureRule {
        variantEnabled = Map.copyOf(variantEnabled);
        variantWeight = Map.copyOf(variantWeight);
    }

    public boolean isEmpty() {
        return naturalSpawn.isEmpty() && variantEnabled.isEmpty() && variantWeight.isEmpty();
    }

    public CreatureRule withNaturalSpawn(Optional<Boolean> value) {
        return new CreatureRule(value, variantEnabled, variantWeight);
    }

    public CreatureRule withVariantEnabled(String variant, Optional<Boolean> value) {
        return new CreatureRule(naturalSpawn, with(variantEnabled, variant, value), variantWeight);
    }

    public CreatureRule withVariantWeight(String variant, Optional<Integer> value) {
        return new CreatureRule(naturalSpawn, variantEnabled, with(variantWeight, variant, value));
    }

    private static <V> Map<String, V> with(Map<String, V> map, String key, Optional<V> value) {
        Map<String, V> out = new HashMap<>(map);
        value.ifPresentOrElse(v -> out.put(key, v), () -> out.remove(key));
        return out;
    }
}
