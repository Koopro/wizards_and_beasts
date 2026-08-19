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
 * Pins the authored wood cast contributions.
 *
 * <p>These were originally the exact numbers transcribed out of {@code WandStatsResolver.applyWood}
 * when woods became datapack-driven, because that migration was required to be behaviour-neutral.
 * They are now the authored tuning table: all ten woods carry a block, and the four that were wired
 * before — elder, holly, rowan, yew — were <b>deliberately re-tuned</b> rather than preserved.
 *
 * <p>The reason to keep pinning them is unchanged. Nothing else in the build notices if one of these
 * numbers moves: a wood that contributes the wrong amount casts perfectly well. If one changes, that
 * is a balance decision and should be a deliberate one, not a side effect.
 *
 * <p>Two category bonuses that were asked for are absent on purpose. {@code SpellCategory} has only
 * {@code COMBAT}, {@code UTILITY}, {@code DEFENSE} and {@code DARK_ARTS}; a Healing bonus was
 * specified for hawthorn and willow and has no counterpart, so it was omitted rather than mapped onto
 * a category that means something else.
 */
class WandWoodCastModifierTest {

    private static final Path WOODS = Path.of("src", "main", "resources", "data",
            "wizards_and_beasts", "wizards_and_beasts", "wand_woods");

    /** wood id -> its authored contribution. All ten, so a new wood cannot slip in unpinned. */
    private static final Map<String, WandCastModifiers> EXPECTED = Map.ofEntries(
            Map.entry("ash", new WandCastModifiers(1.00f, 0.95f, 1.00f, -0.02f, Map.of())),
            Map.entry("blackthorn", new WandCastModifiers(1.12f, 1.00f, 1.00f, 0.01f,
                    Map.of(SpellCategory.DEFENSE, 0.10f))),
            Map.entry("elder", new WandCastModifiers(1.20f, 0.85f, 1.15f, -0.03f, Map.of())),
            // Healing +15% omitted: no SpellCategory counterpart.
            Map.entry("hawthorn", new WandCastModifiers(1.10f, 1.00f, 1.00f, 0.05f,
                    Map.of(SpellCategory.DARK_ARTS, 0.15f))),
            Map.entry("holly", new WandCastModifiers(0.95f, 0.95f, 1.00f, -0.02f,
                    Map.of(SpellCategory.DEFENSE, 0.12f))),
            Map.entry("rowan", new WandCastModifiers(0.92f, 0.90f, 1.00f, -0.04f,
                    Map.of(SpellCategory.DEFENSE, 0.15f, SpellCategory.DARK_ARTS, -0.10f))),
            Map.entry("vine", new WandCastModifiers(1.05f, 1.00f, 1.10f, 0.00f, Map.of())),
            Map.entry("walnut", new WandCastModifiers(1.08f, 0.95f, 1.05f, 0.02f, Map.of())),
            // Healing +15% omitted, which leaves willow with no category bonus at all.
            Map.entry("willow", new WandCastModifiers(0.95f, 0.92f, 1.00f, -0.03f, Map.of())),
            Map.entry("yew", new WandCastModifiers(1.18f, 1.00f, 1.05f, 0.02f,
                    Map.of(SpellCategory.DARK_ARTS, 0.15f))));

    @Test
    void everyWoodDefinition_decodes() throws IOException {
        Map<String, WandWoodDefinition> decoded = decodeAll();
        assertEquals(10, decoded.size(), "Expected 10 wood definitions, found " + decoded.keySet());
    }

    @Test
    void everyWoodContributesItsAuthoredValues() throws IOException {
        Map<String, WandWoodDefinition> decoded = decodeAll();
        for (Map.Entry<String, WandCastModifiers> e : EXPECTED.entrySet()) {
            WandWoodDefinition def = decoded.get(e.getKey());
            assertTrue(def != null, "Missing wood definition: " + e.getKey());
            assertEquals(e.getValue(), def.castModifiers(),
                    e.getKey() + " no longer contributes its authored values — if that was a balance "
                            + "change, update this table deliberately.");
        }
    }

    /** No wood ships neutral any more; a neutral one would contribute nothing and look fine. */
    @Test
    void noWoodShipsNeutral() throws IOException {
        List<String> neutral = new ArrayList<>();
        for (Map.Entry<String, WandWoodDefinition> e : decodeAll().entrySet()) {
            if (e.getValue().castModifiers().isNeutral()) {
                neutral.add(e.getKey());
            }
        }
        assertTrue(neutral.isEmpty(),
                "these woods contribute nothing to a cast: " + neutral);
    }

    /** Every wood is pinned above, so a newly added one cannot arrive untested. */
    @Test
    void everyWoodOnDiskIsPinned() throws IOException {
        for (String id : decodeAll().keySet()) {
            assertTrue(EXPECTED.containsKey(id),
                    "wood '" + id + "' has no expected contribution here — add it to EXPECTED");
        }
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
