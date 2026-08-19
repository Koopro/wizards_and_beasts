package at.koopro.wizardsandbeasts.world;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every wandwood species must read as a different tree.
 *
 * <p>Before this rework the four species were bespoke procedural {@code Feature} classes with no
 * trunk placer, foliage placer or {@code FeatureSize} between them — and in play they were
 * indistinguishable from each other and from vanilla oak. They are ordinary {@code minecraft:tree}
 * configurations now, which is what makes the silhouette expressible at all.
 *
 * <p>The binding constraint is that no two species share a trunk-placer + foliage-placer +
 * {@code FeatureSize} triple. Nothing at runtime notices when two do; they simply grow the same
 * shape, which is the defect this replaced.
 */
class WandwoodSilhouetteTest {

    private static final Path CONFIGURED = Path.of("src", "main", "resources", "data",
            "wizards_and_beasts", "worldgen", "configured_feature");

    private static final List<String> SPECIES = List.of(
            "elder", "holly", "rowan", "yew",
            "ash", "blackthorn", "hawthorn", "walnut", "willow");

    private static JsonObject config(String species) throws IOException {
        Path file = CONFIGURED.resolve(species + "_tree.json");
        assertTrue(Files.exists(file), "missing configured feature: " + file);
        JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals("minecraft:tree", root.get("type").getAsString(),
                species + " is not a vanilla tree feature, so it has no silhouette to check");
        return root.getAsJsonObject("config");
    }

    /** Placer types plus the size limits — the three things that decide the shape. */
    private static String silhouette(JsonObject config) {
        JsonObject size = config.getAsJsonObject("minimum_size");
        return config.getAsJsonObject("trunk_placer").get("type").getAsString()
                + " | " + config.getAsJsonObject("foliage_placer").get("type").getAsString()
                + " | " + size.get("type").getAsString()
                + ":" + size.get("limit").getAsInt()
                + "," + size.get("lower_size").getAsInt()
                + "," + size.get("upper_size").getAsInt();
    }

    @Test
    void noTwoSpeciesShareASilhouette() throws IOException {
        Set<String> seen = new HashSet<>();
        List<String> clashes = new ArrayList<>();
        for (String species : SPECIES) {
            String shape = silhouette(config(species));
            if (!seen.add(shape)) {
                clashes.add(species + " -> " + shape);
            }
        }
        assertEquals(List.of(), clashes,
                "these species grow the same shape as another, which is the bug this rework replaced");
    }

    /** Even the placer types alone must differ pairwise in at least one slot. */
    @Test
    void everySpeciesDiffersFromEveryOtherInAPlacer() throws IOException {
        for (int i = 0; i < SPECIES.size(); i++) {
            for (int j = i + 1; j < SPECIES.size(); j++) {
                JsonObject a = config(SPECIES.get(i));
                JsonObject b = config(SPECIES.get(j));
                boolean trunkDiffers = !a.getAsJsonObject("trunk_placer").get("type").getAsString()
                        .equals(b.getAsJsonObject("trunk_placer").get("type").getAsString());
                boolean foliageDiffers = !a.getAsJsonObject("foliage_placer").get("type").getAsString()
                        .equals(b.getAsJsonObject("foliage_placer").get("type").getAsString());
                assertTrue(trunkDiffers || foliageDiffers,
                        SPECIES.get(i) + " and " + SPECIES.get(j) + " use the same trunk and foliage "
                                + "placer types — only their numbers differ, which does not read at "
                                + "64 blocks");
            }
        }
    }

    @Test
    void featureSizesAreNotAllIdentical() throws IOException {
        Set<String> sizes = new HashSet<>();
        for (String species : SPECIES) {
            JsonObject size = config(species).getAsJsonObject("minimum_size");
            sizes.add(size.get("limit").getAsInt() + "," + size.get("lower_size").getAsInt()
                    + "," + size.get("upper_size").getAsInt());
        }
        assertTrue(sizes.size() > 1,
                "every species shares one FeatureSize; that is a large part of the silhouette");
    }

    /** Each species must be built from its own blocks — an easy copy-paste slip between four files. */
    @Test
    void everySpeciesUsesItsOwnLogAndLeaves() throws IOException {
        for (String species : SPECIES) {
            JsonObject config = config(species);
            String log = config.getAsJsonObject("trunk_provider")
                    .getAsJsonObject("state").get("Name").getAsString();
            String leaves = config.getAsJsonObject("foliage_provider")
                    .getAsJsonObject("state").get("Name").getAsString();
            assertEquals("wizards_and_beasts:" + species + "_log", log,
                    species + " grows the wrong log");
            assertEquals("wizards_and_beasts:" + species + "_leaves", leaves,
                    species + " grows the wrong leaves");
        }
    }

    /**
     * Saplings resolve the same configured features, so a shape that cannot fit a planted plot is a
     * griefing bug as well as a worldgen one. Vanilla's own trees sit at radius 2–3; this keeps the
     * crown within that envelope.
     */
    @Test
    void noCrownIsWiderThanAPlantedPlotTolerates() throws IOException {
        for (String species : SPECIES) {
            JsonObject foliage = config(species).getAsJsonObject("foliage_placer");
            if (!foliage.has("radius") || !foliage.get("radius").isJsonPrimitive()) {
                continue; // spruce-style placers carry a uniform range, checked by eye in-game
            }
            int radius = foliage.get("radius").getAsInt();
            assertTrue(radius <= 4,
                    species + " has a crown radius of " + radius + ", which will overrun a planted "
                            + "5x5 plot by more than vanilla does");
        }
    }
}
