package at.koopro.wizardsandbeasts.brew;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wild ingredient patches, pinned against the bug that kept both out of every world: a {@code matching_blocks}
 * filter for grass without an offset tests the air block the plant would go in, so it never matched once.
 */
class WildPatchFeatureTest {

    private static final Path FEATURES = Path.of("src", "main", "resources", "data", "wizards_and_beasts",
            "worldgen", "configured_feature");

    @ParameterizedTest
    @ValueSource(strings = {"mandrake_patch", "devils_snare_patch"})
    void theGroundFilterLooksBelow(String feature) throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(FEATURES.resolve(feature + ".json"))).getAsJsonObject();
        JsonArray placement = root.getAsJsonObject("config").getAsJsonObject("feature").getAsJsonArray("placement");
        boolean groundBelow = false;
        for (JsonElement filter : placement) {
            JsonObject predicate = filter.getAsJsonObject().getAsJsonObject("predicate");
            if (!"minecraft:matching_blocks".equals(predicate.get("type").getAsString())) {
                continue;
            }
            if (predicate.getAsJsonArray("blocks").toString().contains("minecraft:grass_block")) {
                JsonArray offset = predicate.getAsJsonArray("offset");
                assertTrue(offset != null && offset.get(1).getAsInt() == -1,
                        feature + " tests for grass where the plant goes, not beneath it");
                groundBelow = true;
            }
        }
        assertTrue(groundBelow, feature + " has no ground filter");
    }
}
