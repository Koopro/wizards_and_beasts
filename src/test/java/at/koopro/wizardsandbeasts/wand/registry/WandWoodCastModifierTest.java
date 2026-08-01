package at.koopro.wizardsandbeasts.wand.registry;

import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the wood cast contributions that were transcribed out of {@code WandStatsResolver.applyWood}
 * when woods became datapack-driven.
 *
 * <p>The migration was required to be behaviour-neutral, so these are not chosen values — they are
 * the exact numbers the hardcoded switch used. If one of them changes, that is a balance decision
 * and should be a deliberate one, not a side effect.
 */
class WandWoodCastModifierTest {

    private static final Path WOODS = Path.of("src", "main", "resources", "data",
            "wizards_and_beasts", "wizards_and_beasts", "wand_woods");

    /** wood id -> the contribution the enum table produced before the migration. */
    private static final Map<String, WandCastModifiers> EXPECTED = Map.of(
            "elder", new WandCastModifiers(1.05f, 0.95f, 1.0f, 0.0f, Map.of()),
            "yew", new WandCastModifiers(1.0f, 1.0f, 1.0f, 0.0f,
                    Map.of(SpellCategory.DARK_ARTS, 0.05f)),
            "holly", new WandCastModifiers(1.0f, 1.0f, 1.0f, 0.0f,
                    Map.of(SpellCategory.COMBAT, 0.05f)),
            "rowan", new WandCastModifiers(1.0f, 1.0f, 1.0f, -0.02f,
                    Map.of(SpellCategory.DEFENSE, 0.05f)));

    /**
     * Woods the brief requires to ship neutral: their {@code spell_modifiers} are authored but
     * unmapped, and inventing cast values for them is a balance decision that has not been made.
     */
    private static final List<String> EXPECTED_NEUTRAL =
            List.of("ash", "blackthorn", "hawthorn", "vine", "walnut", "willow");

    @Test
    void everyWoodDefinition_decodes() throws IOException {
        Map<String, WandWoodDefinition> decoded = decodeAll();
        assertEquals(10, decoded.size(), "Expected 10 wood definitions, found " + decoded.keySet());
    }

    @Test
    void wiredWoods_keepTheirPreMigrationContributions() throws IOException {
        Map<String, WandWoodDefinition> decoded = decodeAll();
        for (Map.Entry<String, WandCastModifiers> e : EXPECTED.entrySet()) {
            WandWoodDefinition def = decoded.get(e.getKey());
            assertTrue(def != null, "Missing wood definition: " + e.getKey());
            assertEquals(e.getValue(), def.castModifiers(),
                    e.getKey() + " must contribute exactly what the enum table did.");
        }
    }

    @Test
    void unwiredWoods_shipNeutral() throws IOException {
        Map<String, WandWoodDefinition> decoded = decodeAll();
        List<String> nonNeutral = new ArrayList<>();
        for (String id : EXPECTED_NEUTRAL) {
            WandWoodDefinition def = decoded.get(id);
            assertTrue(def != null, "Missing wood definition: " + id);
            if (!def.castModifiers().isNeutral()) nonNeutral.add(id);
        }
        assertTrue(nonNeutral.isEmpty(),
                "These woods must ship neutral until their modifiers are ruled on: " + nonNeutral);
    }

    @Test
    void absentCastModifiers_decodeToNeutral() {
        WandCastModifiers parsed = WandCastModifiers.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString("{}"))
                .getOrThrow();
        assertEquals(WandCastModifiers.NEUTRAL, parsed);
        assertTrue(parsed.isNeutral());
    }

    /** The school-keyed map stays authored and untouched; nothing reads it yet. */
    @Test
    void spellModifiers_areStillPresentAndUnmapped() throws IOException {
        for (WandWoodDefinition def : decodeAll().values()) {
            assertTrue(!def.spellModifiers().isEmpty(),
                    "spell_modifiers must survive the migration for the pending mapping ruling.");
        }
    }

    private static Map<String, WandWoodDefinition> decodeAll() throws IOException {
        assertTrue(Files.isDirectory(WOODS), "Missing wood definition directory: " + WOODS);
        Map<String, WandWoodDefinition> out = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(WOODS)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                String id = path.getFileName().toString().replace(".json", "");
                JsonElement json = JsonParser.parseString(Files.readString(path));
                out.put(id, WandWoodDefinition.CODEC.parse(JsonOps.INSTANCE, json)
                        .getOrThrow(msg -> new AssertionError(path + ": " + msg)));
            }
        }
        return out;
    }
}
