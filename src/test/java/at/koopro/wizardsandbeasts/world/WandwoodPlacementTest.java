package at.koopro.wizardsandbeasts.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the placement stack every wandwood tree uses.
 *
 * <p>These features used to place on {@code MOTION_BLOCKING}, which is defined as the highest block
 * that blocks motion <em>or contains a fluid</em>. Leaves block motion and water is a fluid, so trees
 * generated on top of other trees' canopies and standing on lake surfaces. Neither symptom throws,
 * logs, or fails a build — the only way to find it is to fly around a fresh world.
 *
 * <p>So the stack is asserted rather than trusted. Three modifiers carry the fix and each is easy to
 * drop by accident: {@code OCEAN_FLOOR} excludes fluids, {@code surface_water_depth_filter} catches
 * shallow edges the heightmap alone misses, and {@code block_predicate_filter} rejects any position
 * the species' own sapling could not survive on — which is what actually stops tree-on-tree.
 *
 * <p>That last one is load-bearing in a way worth stating: the bespoke tree features perform no ground
 * validation of their own. They place a trunk wherever they are handed. There is no second line of
 * defence behind this filter.
 */
class WandwoodPlacementTest {

    private static final Path PLACED = Path.of("src", "main", "resources", "data",
            "wizards_and_beasts", "worldgen", "placed_feature");

    /** species -> its preserved rarity. Retuning density is out of scope; this pins that it stayed put. */
    private static final Map<String, Integer> SPECIES = Map.of(
            "elder", 8, "holly", 5, "rowan", 7, "yew", 6);

    private static JsonArray placement(String species) throws IOException {
        Path file = PLACED.resolve(species + "_tree.json");
        assertTrue(Files.exists(file), "missing placed feature: " + file);
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject()
                .getAsJsonArray("placement");
    }

    private static List<String> types(JsonArray placement) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < placement.size(); i++) {
            out.add(placement.get(i).getAsJsonObject().get("type").getAsString());
        }
        return out;
    }

    @Test
    void everySpeciesUsesTheSameOrderedStack() throws IOException {
        for (String species : SPECIES.keySet()) {
            assertEquals(List.of(
                            "minecraft:rarity_filter",
                            "minecraft:in_square",
                            "minecraft:surface_water_depth_filter",
                            "minecraft:heightmap",
                            "minecraft:biome",
                            "minecraft:block_predicate_filter"),
                    types(placement(species)),
                    species + " no longer uses the corrected placement stack");
        }
    }

    @Test
    void noSpeciesPlacesOnMotionBlocking() throws IOException {
        for (String species : SPECIES.keySet()) {
            JsonArray placement = placement(species);
            for (int i = 0; i < placement.size(); i++) {
                JsonObject modifier = placement.get(i).getAsJsonObject();
                if (!"minecraft:heightmap".equals(modifier.get("type").getAsString())) {
                    continue;
                }
                assertEquals("OCEAN_FLOOR", modifier.get("heightmap").getAsString(),
                        species + " is back on a heightmap that includes fluids or foliage — this is "
                                + "the trees-on-water and trees-on-canopy bug returning");
            }
        }
    }

    @Test
    void waterDepthFilterRejectsAnyWaterAtAll() throws IOException {
        for (String species : SPECIES.keySet()) {
            JsonObject filter = placement(species).get(2).getAsJsonObject();
            assertEquals(0, filter.get("max_water_depth").getAsInt(),
                    species + " tolerates standing water under a tree");
        }
    }

    /** The filter must name that species' own sapling, not another's and not a vanilla one. */
    @Test
    void survivalFilterNamesTheSpeciesOwnSapling() throws IOException {
        for (String species : SPECIES.keySet()) {
            JsonObject state = placement(species).get(5).getAsJsonObject()
                    .getAsJsonObject("predicate").getAsJsonObject("state");
            assertEquals("wizards_and_beasts:" + species + "_sapling", state.get("Name").getAsString(),
                    species + "'s survival filter checks the wrong block, so it filters nothing useful");
        }
    }

    @Test
    void rarityIsUnchanged() throws IOException {
        for (Map.Entry<String, Integer> e : SPECIES.entrySet()) {
            JsonObject rarity = placement(e.getKey()).get(0).getAsJsonObject();
            assertEquals(e.getValue().intValue(), rarity.get("chance").getAsInt(),
                    e.getKey() + " spawn density changed — retuning it was explicitly out of scope");
        }
    }

    /** A sapling the filter names must actually exist, or the filter silently never passes. */
    @Test
    void everyNamedSaplingIsARegisteredBlock() throws IOException {
        Path blockstates = Path.of("src", "generated", "resources", "assets",
                "wizards_and_beasts", "blockstates");
        for (String species : SPECIES.keySet()) {
            assertTrue(Files.exists(blockstates.resolve(species + "_sapling.json")),
                    species + "_sapling has no blockstate, so it is not a registered block and "
                            + "would_survive would reject every position");
        }
    }
}
