package at.koopro.wizardsandbeasts.spell;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A spell's category is a promise about what casting it does to the world.
 *
 * <p>Lumos shipped dealing 2.5 magic damage to every monster within five blocks, on top of the
 * blindness and the glow, which made a light charm the only utility spell in the corpus that hurt
 * anything. {@code documentation/CURRENT_STATE.md} describes the intended behaviour as "applies a
 * light field, blinds and marks nearby monsters, and swaps your active spell to Nox" — blindness and
 * the mark are named, damage is not, and the same document cites Lumos as the example of a spell
 * that interacts with the world "not just [as a] damage number". So the damage was the part that did
 * not belong, and this is the assertion that keeps it from coming back.
 *
 * <p>Deliberately narrow. It does not say a utility spell may never touch a mob — Lumos still blinds
 * and marks, Aguamenti still extinguishes, Accio still pulls. It says a spell filed under utility may
 * not deal raw damage, which is the specific line that was crossed.
 */
class SpellRoleJsonTest {

    private static final Path SPELL_DIR =
            Path.of("src", "main", "resources", "data", "wizards_and_beasts", "spells");

    /** Categories whose whole point is harm; a damage component there is the spell working. */
    private static final List<String> COMBAT_ROLES = List.of("combat", "dark_arts");

    private static List<Path> spellFiles() throws IOException {
        try (Stream<Path> files = Files.walk(SPELL_DIR)) {
            return files.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
    }

    /** Every {@code damage} component anywhere in a spell's effect tree, including nested aoe_apply. */
    private static void collectDamage(JsonElement element, List<Float> out) {
        if (element == null || !element.isJsonArray()) {
            return;
        }
        for (JsonElement entry : element.getAsJsonArray()) {
            if (!entry.isJsonObject()) {
                continue;
            }
            JsonObject object = entry.getAsJsonObject();
            String type = object.has("type") ? object.get("type").getAsString() : "";
            if ("damage".equals(type) && object.has("amount")) {
                out.add(object.get("amount").getAsFloat());
            }
            if ("aoe_apply".equals(type)) {
                collectDamage(object.get("effects"), out);
            }
        }
    }

    @Test
    void noUtilitySpellDealsRawDamage() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : spellFiles()) {
            JsonObject spell = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            String category = spell.has("category") ? spell.get("category").getAsString() : "";
            if (COMBAT_ROLES.contains(category)) {
                continue;
            }

            List<Float> damage = new ArrayList<>();
            collectDamage(spell.get("effects"), damage);
            float base = spell.has("baseDamage") ? spell.get("baseDamage").getAsFloat() : 0.0f;

            if (!damage.isEmpty() || base > 0.0f) {
                offenders.add(file.getFileName() + " (" + category + "): baseDamage=" + base
                        + " damage components=" + damage);
            }
        }
        assertTrue(offenders.isEmpty(),
                "a spell filed outside " + COMBAT_ROLES + " must not deal raw damage:\n  "
                        + String.join("\n  ", offenders));
    }

    /**
     * The half of Lumos that is supposed to be there, pinned so that removing the damage is not
     * quietly widened into removing the spell's reason to exist.
     */
    @Test
    void lumosStillBlindsAndMarksWhatItLightsUp() throws IOException {
        JsonObject lumos = JsonParser.parseString(
                Files.readString(SPELL_DIR.resolve("lumos.json"))).getAsJsonObject();

        JsonArray effects = lumos.getAsJsonArray("effects");
        JsonObject aoe = null;
        for (JsonElement entry : effects) {
            JsonObject object = entry.getAsJsonObject();
            if ("aoe_apply".equals(object.get("type").getAsString())) {
                aoe = object;
            }
        }
        assertTrue(aoe != null, "Lumos no longer reaches the things around it at all");
        assertTrue("monsters".equals(aoe.get("filter").getAsString()),
                "Lumos should still single out monsters rather than everything alive");

        List<String> applied = new ArrayList<>();
        for (JsonElement entry : aoe.getAsJsonArray("effects")) {
            JsonObject object = entry.getAsJsonObject();
            if ("apply_effect".equals(object.get("type").getAsString())) {
                applied.add(object.get("effect").getAsString());
            }
        }
        assertTrue(applied.contains("minecraft:blindness"), "a light in the eyes should still blind");
        assertTrue(applied.contains("minecraft:glowing"), "Lumos should still mark what it reveals");

        List<Float> damage = new ArrayList<>();
        collectDamage(effects, damage);
        assertTrue(damage.isEmpty(), "Lumos is a light charm, not a weapon: " + damage);
        assertFalse(lumos.has("baseDamage") && lumos.get("baseDamage").getAsFloat() > 0.0f,
                "Lumos must not carry a base damage either");
    }
}
