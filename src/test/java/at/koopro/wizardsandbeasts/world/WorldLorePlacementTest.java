package at.koopro.wizardsandbeasts.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldLorePlacementTest {

    private static final Path RESOURCES = Path.of("src/main/resources/data/wizards_and_beasts");

    @Test
    void treePlacedFeatures_includeBiomeGuardrail() throws IOException {
        assertHasBiomePlacement("worldgen/placed_feature/elder_tree.json");
        assertHasBiomePlacement("worldgen/placed_feature/yew_tree.json");
        assertHasBiomePlacement("worldgen/placed_feature/holly_tree.json");
        assertHasBiomePlacement("worldgen/placed_feature/rowan_tree.json");
    }

    @Test
    void plantPatches_targetLoreBiomeFamilies() throws IOException {
        assertBiomeSet("neoforge/biome_modifier/mandrake_patch.json",
                Set.of("minecraft:forest", "minecraft:dark_forest", "minecraft:flower_forest"));
        assertBiomeSet("neoforge/biome_modifier/mallowsweet_patch.json",
                Set.of("minecraft:meadow", "minecraft:plains", "minecraft:flower_forest"));
        assertBiomeSet("neoforge/biome_modifier/devils_snare_patch.json",
                Set.of("minecraft:dark_forest", "minecraft:swamp", "minecraft:mangrove_swamp"));
    }

    private static void assertHasBiomePlacement(String relativePath) throws IOException {
        JsonObject root = readJson(relativePath);
        JsonArray placement = root.getAsJsonArray("placement");
        boolean hasBiome = StreamSupport.stream(placement.spliterator(), false)
                .map(e -> e.getAsJsonObject().get("type").getAsString())
                .anyMatch("minecraft:biome"::equals);
        assertTrue(hasBiome, relativePath + " must include minecraft:biome placement guardrail.");
    }

    private static void assertBiomeSet(String relativePath, Set<String> expectedBiomes) throws IOException {
        JsonObject root = readJson(relativePath);
        JsonArray biomes = root.getAsJsonArray("biomes");
        Set<String> actual = StreamSupport.stream(biomes.spliterator(), false)
                .map(e -> e.getAsString())
                .collect(Collectors.toSet());
        assertTrue(actual.equals(expectedBiomes),
                () -> relativePath + " must target lore biome set. expected=" + expectedBiomes + ", actual=" + actual);
    }

    private static JsonObject readJson(String relativePath) throws IOException {
        String content = Files.readString(RESOURCES.resolve(relativePath));
        return JsonParser.parseString(content).getAsJsonObject();
    }
}
