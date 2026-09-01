package at.koopro.wizardsandbeasts.brew.def;

import org.jspecify.annotations.Nullable;

import at.koopro.wizardsandbeasts.brew.BrewingRecipe;
import at.koopro.wizardsandbeasts.brew.BrewingRecipe.Catalyst;
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
        String outputBrewId,
        float failureChance,
        java.util.Optional<CatalystEntry> catalyst) {

    public static final Codec<BrewingRecipeDefinition> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            IngredientEntry.CODEC.listOf().fieldOf("ingredients").forGetter(BrewingRecipeDefinition::ingredients),
            StringRepresentable.fromValues(CauldronTier::values).optionalFieldOf("cauldronTier", CauldronTier.PEWTER).forGetter(BrewingRecipeDefinition::cauldronTier),
            Codec.INT.optionalFieldOf("heatTimeTicks", 200).forGetter(BrewingRecipeDefinition::heatTimeTicks),
            Codec.STRING.fieldOf("outputBrewId").forGetter(BrewingRecipeDefinition::outputBrewId),
            Codec.FLOAT.optionalFieldOf("failureChance", 0f).forGetter(BrewingRecipeDefinition::failureChance),
            CatalystEntry.CODEC.optionalFieldOf("catalyst").forGetter(BrewingRecipeDefinition::catalyst)
    ).apply(inst, BrewingRecipeDefinition::new));

    /** Four-argument form for callers that predate difficulty. */
    public BrewingRecipeDefinition(List<IngredientEntry> ingredients, CauldronTier cauldronTier,
                                   int heatTimeTicks, String outputBrewId) {
        this(ingredients, cauldronTier, heatTimeTicks, outputBrewId, 0f, java.util.Optional.empty());
    }

    /** JSON form of {@link BrewingRecipe.Catalyst}. */
    public record CatalystEntry(Identifier item, float windowStart, float windowEnd, float missPenalty) {

        public static final Codec<CatalystEntry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Identifier.CODEC.fieldOf("item").forGetter(CatalystEntry::item),
                Codec.FLOAT.optionalFieldOf("windowStart", 0.4f).forGetter(CatalystEntry::windowStart),
                Codec.FLOAT.optionalFieldOf("windowEnd", 0.7f).forGetter(CatalystEntry::windowEnd),
                Codec.FLOAT.optionalFieldOf("missPenalty", 0.35f).forGetter(CatalystEntry::missPenalty)
        ).apply(inst, CatalystEntry::new));

        @Nullable Catalyst resolve() {
            Item resolved = BuiltInRegistries.ITEM.getValue(item);
            if (resolved == null) return null;
            // A window the wrong way round is a datapack typo, not a recipe. Dropping the catalyst
            // rather than throwing keeps the rest of the recipe usable and logs nothing the player
            // can act on -- the brew simply has no timed add.
            if (windowEnd < windowStart) return null;
            return new Catalyst(resolved, windowStart, windowEnd, missPenalty);
        }
    }

    /**
     * Resolves this definition into a {@link BrewingRecipe}, dropping any
     * ingredient whose item id is unknown. Returns {@code null} if every
     * ingredient was dropped (a recipe with zero ingredients can never match).
     */
    public @Nullable BrewingRecipe toRecipe(String fullId) {
        List<BrewingRecipe.Ingredient> resolved = ingredients.stream()
                .map(IngredientEntry::resolve)
                .filter(Objects::nonNull)
                .toList();
        if (resolved.isEmpty()) return null;
        return new BrewingRecipe(fullId, resolved, cauldronTier, heatTimeTicks, outputBrewId,
                failureChance, catalyst.map(CatalystEntry::resolve)
                        .filter(java.util.Objects::nonNull));
    }

    /**
     * The inverse of {@link #toRecipe(String)}, for the same reason {@code BrewDefinition.fromBrew}
     * exists: the sync payload carries definitions so the JSON codec doubles as the wire format.
     * The recipe's own id is not carried here — it is the map key in the payload, because it comes from
     * the file path rather than the file body.
     */
    public static BrewingRecipeDefinition fromRecipe(BrewingRecipe recipe) {
        List<IngredientEntry> entries = recipe.ingredients().stream()
                .map(ingredient -> new IngredientEntry(
                        BuiltInRegistries.ITEM.getKey(ingredient.item()),
                        ingredient.count()))
                .toList();
        return new BrewingRecipeDefinition(entries, recipe.cauldronTier(),
                recipe.heatTimeTicks(), recipe.outputBrewId(), recipe.failureChance(),
                recipe.catalyst().map(c -> new CatalystEntry(
                        BuiltInRegistries.ITEM.getKey(c.item()),
                        c.windowStart(), c.windowEnd(), c.missPenalty())));
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
