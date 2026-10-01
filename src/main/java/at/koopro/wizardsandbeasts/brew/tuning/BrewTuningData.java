package at.koopro.wizardsandbeasts.brew.tuning;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.Map;

/**
 * Where a world keeps its brew and recipe overrides. Keyed by brew / recipe id; an override for something a later
 * datapack removes is kept, inert, and applies again if it returns.
 */
@NullMarked
public final class BrewTuningData extends SavedData {

    public static final SavedDataType<BrewTuningData> TYPE = new SavedDataType<>(
            WizardsAndBeastsMod.MODID + "_brew_tuning",
            BrewTuningData::new,
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.unboundedMap(Codec.STRING, BrewOverride.CODEC).optionalFieldOf("brews", Map.of())
                            .forGetter(data -> Map.copyOf(data.brews)),
                    Codec.unboundedMap(Codec.STRING, RecipeOverride.CODEC).optionalFieldOf("recipes", Map.of())
                            .forGetter(data -> Map.copyOf(data.recipes))
            ).apply(instance, BrewTuningData::new)));

    private final Map<String, BrewOverride> brews = new HashMap<>();
    private final Map<String, RecipeOverride> recipes = new HashMap<>();

    private BrewTuningData() {
    }

    private BrewTuningData(Map<String, BrewOverride> storedBrews, Map<String, RecipeOverride> storedRecipes) {
        storedBrews.forEach((id, o) -> {
            if (!o.isEmpty()) {
                brews.put(id, o);
            }
        });
        storedRecipes.forEach((id, o) -> {
            if (!o.isEmpty()) {
                recipes.put(id, o);
            }
        });
    }

    public static BrewTuningData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public Map<String, BrewOverride> brews() {
        return Map.copyOf(brews);
    }

    public Map<String, RecipeOverride> recipes() {
        return Map.copyOf(recipes);
    }

    public BrewOverride brew(String id) {
        return brews.getOrDefault(id, BrewOverride.NONE);
    }

    public RecipeOverride recipe(String id) {
        return recipes.getOrDefault(id, RecipeOverride.NONE);
    }

    public void putBrew(String id, BrewOverride override) {
        if (override.isEmpty()) {
            brews.remove(id);
        } else {
            brews.put(id, override);
        }
        setDirty();
    }

    public void putRecipe(String id, RecipeOverride override) {
        if (override.isEmpty()) {
            recipes.remove(id);
        } else {
            recipes.put(id, override);
        }
        setDirty();
    }
}
