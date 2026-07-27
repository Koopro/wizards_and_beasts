package at.koopro.wizardsandbeasts.module;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the generated {@code module/*} tags, which are the data the runtime index is built from.
 *
 * <p>Reads the committed JSON rather than a live registry: the point is to catch a regenerated tag set
 * that no longer says what we think it says — a renamed module orphaning its tag, or an item quietly
 * changing owner — without needing a game to be running.
 */
class ModuleTagDataTest {

    private static final Path TAG_ROOT =
            Path.of("src", "generated", "resources", "data", "wizards_and_beasts", "tags");

    private static Map<String, String> ownersOf(String registryFolder) throws IOException {
        Path dir = TAG_ROOT.resolve(registryFolder).resolve("module");
        assertTrue(Files.isDirectory(dir), () -> "missing generated module tags: " + dir);

        Map<String, String> owners = new HashMap<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.toList()) {
                String module = file.getFileName().toString().replace(".json", "");
                try (Reader reader = Files.newBufferedReader(file)) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    json.getAsJsonArray("values")
                            .forEach(value -> owners.put(value.getAsString(), module));
                }
            }
        }
        return owners;
    }

    @Test
    void everyTagFileNamesARealModule() throws IOException {
        for (String registry : List.of("item", "block", "entity_type")) {
            Path dir = TAG_ROOT.resolve(registry).resolve("module");
            try (Stream<Path> files = Files.list(dir)) {
                for (Path file : files.toList()) {
                    String name = file.getFileName().toString().replace(".json", "");
                    // Module.valueOf throws on an unknown constant, which is the failure we want: a
                    // renamed module leaves its tag behind, and the tag is what the index reads.
                    Module module = Module.valueOf(name.toUpperCase(Locale.ROOT));
                    assertEquals(name, module.name().toLowerCase(Locale.ROOT));
                }
            }
        }
    }

    @Test
    void contentIsOwnedByTheModuleItBelongsTo() throws IOException {
        Map<String, String> items = ownersOf("item");

        assertEquals("dark_arts", items.get("wizards_and_beasts:riddles_diary"));
        assertEquals("gringotts", items.get("wizards_and_beasts:galleon"));
        assertEquals("wands", items.get("wizards_and_beasts:wand"));
        assertEquals("broom_flight", items.get("wizards_and_beasts:nimbus_2000"));
        assertEquals("wizarding_food", items.get("wizards_and_beasts:butterbeer"));
        assertEquals("wandwood", items.get("wizards_and_beasts:holly_planks"));
        assertEquals("scholarship", items.get("wizards_and_beasts:parchment"));
        assertEquals("magizoology", items.get("wizards_and_beasts:bezoar"));
        assertEquals("artefacts", items.get("wizards_and_beasts:marauders_map"));
        assertEquals("furnishings", items.get("wizards_and_beasts:brass_cauldron"));
        assertEquals("structures", items.get("wizards_and_beasts:gringotts_white_marble"));
        assertEquals("creatures", items.get("wizards_and_beasts:niffler_spawn_egg"));
    }

    @Test
    void decorativeGringottsBlocksStayWithStructures() throws IOException {
        Map<String, String> blocks = ownersOf("block");

        // The bank's build set is decoration, not banking. Putting it under GRINGOTTS would mean turning
        // currency off also deleted a builder's marble from the creative menu.
        assertEquals("structures", blocks.get("wizards_and_beasts:gringotts_iron_vault_stone"));
        assertEquals("structures", blocks.get("wizards_and_beasts:gringotts_vault_bricks"));
        assertFalse(blocks.containsValue("gringotts"), "GRINGOTTS owns no blocks");
    }

    @Test
    void everyBlockHasAnOwningModule() throws IOException {
        Map<String, String> blocks = ownersOf("block");
        Path blockstates = Path.of("src", "generated", "resources", "assets",
                "wizards_and_beasts", "blockstates");

        try (Stream<Path> files = Files.list(blockstates)) {
            for (Path file : files.toList()) {
                String id = "wizards_and_beasts:" + file.getFileName().toString().replace(".json", "");
                assertNotNull(blocks.get(id), () -> id + " belongs to no module; it would stay reachable "
                        + "in every configuration. Tag it, or decide deliberately that it fails open.");
            }
        }
    }
}
