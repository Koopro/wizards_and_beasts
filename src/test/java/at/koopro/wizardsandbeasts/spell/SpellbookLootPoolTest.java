package at.koopro.wizardsandbeasts.spell;

import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
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
 * That the spell pools the world hands out are made of spells that exist.
 *
 * <p>{@code SpellbookLootModifier} skips a pool entry it cannot resolve, which is the right runtime
 * behaviour — a datapack parses long before the spell registry is populated, and refusing to load
 * would take the working entries down with the typo. It is also completely silent, so a misspelled
 * id in the shipped pools would simply narrow the table and nobody would find out until a player
 * wondered why village libraries never held Lumos.
 *
 * <p>Also asserts what must <em>not</em> be in a pool. An Unforgivable found on a village shelf is a
 * canon problem, not a balance one, and an unimplemented spell in a pool is a book that teaches
 * "still being written".
 *
 * <h2>Read off the files, not off {@code Spells}</h2>
 * The mod's 128 datapack spells reach {@link Spells} through a reload listener, so in a bare unit
 * test the registry holds only the handful defined in Java. The pools are checked against the spell
 * <em>definitions</em> in {@code data/wizards_and_beasts/spells/} instead, with the Java-side
 * registry as the fallback for the few spells that have no JSON of their own.
 */
class SpellbookLootPoolTest {

    private static final Path MODIFIERS =
            Path.of("src", "main", "resources", "data", "wizards_and_beasts", "loot_modifiers");
    private static final Path DEFINITIONS =
            Path.of("src", "main", "resources", "data", "wizards_and_beasts", "spells");

    private static List<Path> spellbookModifiers() throws IOException {
        try (Stream<Path> files = Files.list(MODIFIERS)) {
            List<Path> found = new ArrayList<>();
            for (Path file : files.toList()) {
                if (file.getFileName().toString().endsWith(".json")
                        && Files.readString(file).contains("wizards_and_beasts:spellbook")) {
                    found.add(file);
                }
            }
            return found;
        }
    }

    private static List<String> poolOf(Path file) throws IOException {
        JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        JsonArray spells = json.getAsJsonArray("spells");
        List<String> ids = new ArrayList<>();
        for (JsonElement element : spells) {
            ids.add(element.getAsString());
        }
        return ids;
    }

    /** The definition JSON for a bare spell id, or {@code null} when the spell is defined in Java. */
    private static JsonObject definitionOf(String id) throws IOException {
        Path file = DEFINITIONS.resolve(id.replace("wizards_and_beasts:", "") + ".json");
        return Files.exists(file)
                ? JsonParser.parseString(Files.readString(file)).getAsJsonObject()
                : null;
    }

    @Test
    void everyPoolEntryResolvesToAnImplementedSpell() throws IOException {
        List<Path> modifiers = spellbookModifiers();
        assertFalse(modifiers.isEmpty(), "no spellbook loot modifiers found — the bootstrap is gone");

        for (Path file : modifiers) {
            List<String> pool = poolOf(file);
            assertFalse(pool.isEmpty(), file.getFileName() + " has an empty pool and can never drop a book");
            for (String id : pool) {
                JsonObject definition = definitionOf(id);
                if (definition == null) {
                    Spell javaSpell = Spells.byId(id);
                    assertTrue(javaSpell != null,
                            file.getFileName() + ": no such spell '" + id + "'");
                    assertTrue(javaSpell.isImplemented(),
                            file.getFileName() + ": '" + id + "' is not implemented yet");
                    continue;
                }
                String state = definition.has("implementationState")
                        ? definition.get("implementationState").getAsString()
                        : "implemented";
                assertFalse("coming_soon".equals(state),
                        file.getFileName() + ": '" + id + "' is still being written");
            }
        }
    }

    @Test
    void noPoolOffersADarkArt() throws IOException {
        for (Path file : spellbookModifiers()) {
            for (String id : poolOf(file)) {
                JsonObject definition = definitionOf(id);
                if (definition == null || !definition.has("category")) {
                    continue;
                }
                assertFalse("dark_arts".equalsIgnoreCase(definition.get("category").getAsString()),
                        file.getFileName() + ": '" + id + "' is a Dark Art and does not belong in found loot");
            }
        }
    }

    /**
     * An Obscurial ability is not a spell anybody writes down, and the eligibility layer refuses one
     * outright — so a book holding one is a book that can never be read.
     */
    @Test
    void noPoolOffersAnObscurialAbility() throws IOException {
        for (Path file : spellbookModifiers()) {
            for (String id : poolOf(file)) {
                assertFalse(id.replace("wizards_and_beasts:", "").startsWith("obscurus_"),
                        file.getFileName() + ": '" + id + "' is an Obscurial ability, not a written spell");
            }
        }
    }
}
