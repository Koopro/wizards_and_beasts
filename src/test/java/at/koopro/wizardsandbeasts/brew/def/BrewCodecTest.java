package at.koopro.wizardsandbeasts.brew.def;

import at.koopro.wizardsandbeasts.brew.CauldronTier;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JSON round-trip tests for the brewing codecs. Resolution against vanilla
 * registries (mob effects, items) is intentionally not exercised here — that
 * path requires a Minecraft bootstrap and is covered by integration runs.
 */
class BrewCodecTest {

    private static final Gson GSON = new Gson();

    @Test
    void brewDefinition_minimal() {
        BrewDefinition def = parseBrew("""
                {
                  "displayName": "Test Brew",
                  "color": 16711935,
                  "effects": [
                    { "id": "minecraft:speed", "duration": 200 }
                  ]
                }
                """);
        assertEquals("Test Brew", def.displayName());
        assertEquals(16711935, def.color());
        assertEquals(1, def.effects().size());
        assertEquals(200, def.effects().get(0).duration());
        assertEquals(0, def.effects().get(0).amplifier(), "amplifier defaults to 0");
        assertFalse(def.effects().get(0).ambient(), "ambient defaults to false");
        assertEquals(Optional.empty(), def.flavorText());
    }

    @Test
    void brewDefinition_roundTrip_preservesAllFields() {
        BrewDefinition def = parseBrew("""
                {
                  "displayName": "Wiggenweld",
                  "color": -16728081,
                  "flavorText": "A healing draught.",
                  "effects": [
                    { "id": "minecraft:regeneration", "duration": 200, "amplifier": 1, "ambient": true },
                    { "id": "minecraft:absorption", "duration": 600, "amplifier": 0 }
                  ]
                }
                """);

        JsonElement encoded = BrewDefinition.CODEC.encodeStart(JsonOps.INSTANCE, def)
                .result().orElseThrow();
        BrewDefinition reparsed = BrewDefinition.CODEC.parse(JsonOps.INSTANCE, encoded)
                .result().orElseThrow();
        assertEquals(def, reparsed, "encode -> decode must be idempotent");
    }

    @Test
    void recipeDefinition_minimal() {
        BrewingRecipeDefinition def = parseRecipe("""
                {
                  "ingredients": [
                    { "item": "minecraft:glistering_melon_slice" }
                  ],
                  "outputBrewId": "wizards_and_beasts:wiggenweld_potion"
                }
                """);
        assertEquals(CauldronTier.BRASS, def.cauldronTier(),
                "cauldronTier defaults to BRASS");
        assertEquals(200, def.heatTimeTicks(), "heatTimeTicks defaults to 200");
        assertEquals(1, def.ingredients().size());
        assertEquals(1, def.ingredients().get(0).count(), "count defaults to 1");
    }

    @Test
    void recipeDefinition_pewterTier_overridesDefaults() {
        BrewingRecipeDefinition def = parseRecipe("""
                {
                  "ingredients": [
                    { "item": "minecraft:nether_wart", "count": 3 },
                    { "item": "minecraft:fermented_spider_eye", "count": 1 }
                  ],
                  "cauldronTier": "pewter",
                  "heatTimeTicks": 600,
                  "outputBrewId": "wizards_and_beasts:advanced_brew"
                }
                """);
        assertEquals(CauldronTier.PEWTER, def.cauldronTier());
        assertEquals(600, def.heatTimeTicks());
        assertEquals(2, def.ingredients().size());
        assertEquals(3, def.ingredients().get(0).count());
    }

    private static BrewDefinition parseBrew(String json) {
        JsonElement element = GSON.fromJson(json, JsonElement.class);
        var result = BrewDefinition.CODEC.parse(JsonOps.INSTANCE, element);
        if (result.error().isPresent()) {
            throw new AssertionError("Brew codec parse failed: " + result.error().get().message());
        }
        return result.result().orElseThrow();
    }

    private static BrewingRecipeDefinition parseRecipe(String json) {
        JsonElement element = GSON.fromJson(json, JsonElement.class);
        var result = BrewingRecipeDefinition.CODEC.parse(JsonOps.INSTANCE, element);
        if (result.error().isPresent()) {
            throw new AssertionError("Recipe codec parse failed: " + result.error().get().message());
        }
        return result.result().orElseThrow();
    }
}
