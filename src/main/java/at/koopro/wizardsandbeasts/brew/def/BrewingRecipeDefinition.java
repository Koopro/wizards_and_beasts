package at.koopro.wizardsandbeasts.brew.def;

import at.koopro.wizardsandbeasts.brew.BrewingRecipe;
import at.koopro.wizardsandbeasts.brew.CauldronTier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Objects;

/**
 * JSON-friendly description of a brewing recipe. Loaded by
 * {@link BrewingRecipeReloadListener} from
 * {@code data/<ns>/<modId>/brewing_recipes/*.json}.
 */
public record BrewingRecipeDefinition(
        List<IngredientEntry> ingredients,
        CauldronTier cauldronTier,
        int heatTimeTicks,
        String outputBrewId) {

    public static final Codec<BrewingRecipeDefinition> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            IngredientEntry.CODEC.listOf().fieldOf("ingredients").forGetter(BrewingRecipeDefinition::ingredients),
            StringRepresentable.fromValues(CauldronTier::values).optionalFieldOf("cauldronTier", CauldronTier.BRASS).forGetter(BrewingRecipeDefinition::cauldronTier),
            Codec.INT.optionalFieldOf("heatTimeTicks", 200).forGetter(BrewingRecipeDefinition::heatTimeTicks),
            Codec.STRING.fieldOf("outputBrewId").forGetter(BrewingRecipeDefinition::outputBrewId)
    ).apply(inst, BrewingRecipeDefinition::new));

    /**
     * Resolves this definition into a {@link BrewingRecipe}, dropping any
     * ingredient whose item id is unknown. Returns {@code null} if every
     * ingredient was dropped (a recipe with zero ingredients can never match).
     */
    public BrewingRecipe toRecipe(String fullId) {
        List<BrewingRecipe.Ingredient> resolved = ingredients.stream()
                .map(IngredientEntry::resolve)
                .filter(Objects::nonNull)
                .toList();
        if (resolved.isEmpty()) return null;
        return new BrewingRecipe(fullId, resolved, cauldronTier, heatTimeTicks, outputBrewId);
    }

    /** Single ingredient entry: namespaced item id + count. */
    public record IngredientEntry(Identifier item, int count) {

        public static final Codec<IngredientEntry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Identifier.CODEC.fieldOf("item").forGetter(IngredientEntry::item),
                Codec.INT.optionalFieldOf("count", 1).forGetter(IngredientEntry::count)
        ).apply(inst, IngredientEntry::new));

        BrewingRecipe.Ingredient resolve() {
            Item resolved = BuiltInRegistries.ITEM.getValue(item);
            if (resolved == null) return null;
            return new BrewingRecipe.Ingredient(resolved, count);
        }
    }
}
