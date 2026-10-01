package at.koopro.wizardsandbeasts.brew.tuning;

import at.koopro.wizardsandbeasts.brew.CauldronTier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * An administrator's override of one brewing recipe. Empty fields mean "as the datapack defines it". The recipe's
 * ingredients themselves (which items) and its output are not overridable — only how many of each, so a recipe
 * can be made cheaper or dearer but never turned into a different recipe.
 *
 * @param heatTimeTicks    replaces the time on the heat
 * @param failureChance    replaces the authored failure chance (0–1)
 * @param cauldronTier     replaces the cauldron the recipe needs
 * @param ingredientCounts item id → how many
 */
@NullMarked
public record RecipeOverride(Optional<Integer> heatTimeTicks, Optional<Float> failureChance,
                             Optional<CauldronTier> cauldronTier, Map<String, Integer> ingredientCounts) {

    public static final RecipeOverride NONE = new RecipeOverride(Optional.empty(), Optional.empty(), Optional.empty(), Map.of());

    public static final Codec<RecipeOverride> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.optionalFieldOf("heatTimeTicks").forGetter(RecipeOverride::heatTimeTicks),
            Codec.FLOAT.optionalFieldOf("failureChance").forGetter(RecipeOverride::failureChance),
            CauldronTier.CODEC.optionalFieldOf("cauldronTier").forGetter(RecipeOverride::cauldronTier),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("ingredientCounts", Map.of())
                    .forGetter(RecipeOverride::ingredientCounts)
    ).apply(inst, RecipeOverride::new));

    public RecipeOverride {
        ingredientCounts = Map.copyOf(ingredientCounts);
    }

    public boolean isEmpty() {
        return heatTimeTicks.isEmpty() && failureChance.isEmpty() && cauldronTier.isEmpty() && ingredientCounts.isEmpty();
    }

    public RecipeOverride withHeatTime(Optional<Integer> value) {
        return new RecipeOverride(value, failureChance, cauldronTier, ingredientCounts);
    }

    public RecipeOverride withFailureChance(Optional<Float> value) {
        return new RecipeOverride(heatTimeTicks, value, cauldronTier, ingredientCounts);
    }

    public RecipeOverride withTier(Optional<CauldronTier> value) {
        return new RecipeOverride(heatTimeTicks, failureChance, value, ingredientCounts);
    }

    public RecipeOverride withIngredientCount(String item, Optional<Integer> value) {
        Map<String, Integer> next = new HashMap<>(ingredientCounts);
        value.ifPresentOrElse(v -> next.put(item, v), () -> next.remove(item));
        return new RecipeOverride(heatTimeTicks, failureChance, cauldronTier, next);
    }
}
