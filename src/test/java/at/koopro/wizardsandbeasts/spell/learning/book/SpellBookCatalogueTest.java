package at.koopro.wizardsandbeasts.spell.learning.book;

import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The spell textbooks (spell acquisition remake, owner 2026-11-27): every spell anyone can learn is in a book, every
 * book is made of spells that exist, the first-year pair can be read by someone who knows nothing, and the
 * bookseller never sells a restricted book. Read off the files, as {@code SpellbookLootPoolTest} does: a bare unit
 * test has only the Java spells registered.
 */
class SpellBookCatalogueTest {

    private static final Path DATA = Path.of("src", "main", "resources", "data", "wizards_and_beasts");
    private static final Path BOOKS = DATA.resolve(SpellBooks.DIRECTORY);
    private static final Path SPELLS = DATA.resolve("spells");
    private static final Path LANG = Path.of("src", "main", "resources", "assets", "wizards_and_beasts", "lang",
            "en_us.json");
    /** The Java spells a player may learn (Obscurus Surge/Grasp are Obscurial abilities, not written spells). */
    private static final List<String> JAVA_LEARNABLE =
            List.of("protego", "expecto_patronum", "avada_kedavra", "imperio", "fiendfyre");

    private static Map<String, JsonObject> books() throws IOException {
        Map<String, JsonObject> out = new HashMap<>();
        try (Stream<Path> files = Files.list(BOOKS)) {
            for (Path f : files.toList()) {
                String name = f.getFileName().toString();
                if (name.endsWith(".json")) {
                    out.put(name.substring(0, name.length() - 5), JsonParser.parseString(Files.readString(f)).getAsJsonObject());
                }
            }
        }
        return out;
    }

    private static List<String> chapters(JsonObject book) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : book.getAsJsonArray("chapters")) {
            out.add(e.getAsString().replace("wizards_and_beasts:", ""));
        }
        return out;
    }

    private static Set<String> implementedJsonSpells() throws IOException {
        Set<String> out = new HashSet<>();
        try (Stream<Path> files = Files.list(SPELLS)) {
            for (Path f : files.toList()) {
                JsonObject spell = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
                String state = spell.has("implementationState") ? spell.get("implementationState").getAsString()
                        : "implemented";
                if (!"coming_soon".equals(state)) {
                    String name = f.getFileName().toString();
                    out.add(name.substring(0, name.length() - 5));
                }
            }
        }
        return out;
    }

    @Test
    void everyShippedBookHasItsFileAndTitle() throws IOException {
        Map<String, JsonObject> books = books();
        JsonObject lang = JsonParser.parseString(Files.readString(LANG)).getAsJsonObject();
        for (String id : SpellBooks.SHIPPED) {
            assertTrue(books.containsKey(id), id + " is shipped but has no spell_books/" + id + ".json");
            assertTrue(lang.has("spell_book.wizards_and_beasts." + id + ".title"), id + " has no title");
            assertTrue(lang.has("spell_book.wizards_and_beasts." + id + ".author"), id + " has no author line");
        }
        assertEquals(new HashSet<>(SpellBooks.SHIPPED), books.keySet(), "a book file is not in SpellBooks.SHIPPED");
    }

    @Test
    void everyChapterIsALearnableSpell() throws IOException {
        Set<String> implemented = implementedJsonSpells();
        for (var entry : books().entrySet()) {
            List<String> chapters = chapters(entry.getValue());
            assertFalse(chapters.isEmpty(), entry.getKey() + " teaches nothing");
            for (String c : chapters) {
                boolean java = JAVA_LEARNABLE.contains(c);
                if (!java) {
                    assertTrue(implemented.contains(c), entry.getKey() + ": chapter '" + c + "' is not an implemented spell");
                } else {
                    Spell spell = Spells.byId(c);
                    assertTrue(spell != null && spell.isImplemented(), entry.getKey() + ": no Java spell '" + c + "'");
                }
            }
        }
    }

    @Test
    void everyLearnableSpellIsInABook() throws IOException {
        Set<String> inABook = new HashSet<>();
        for (JsonObject book : books().values()) {
            inABook.addAll(chapters(book));
        }
        Set<String> learnable = new HashSet<>(implementedJsonSpells());
        learnable.addAll(JAVA_LEARNABLE);
        learnable.removeAll(inABook);
        assertTrue(learnable.isEmpty(), "spells no book teaches, so nobody can ever learn them: " + learnable);
    }

    /**
     * The first-year pair is the whole of a new student's start: each of its spells needs nothing, or only a spell
     * the pair itself teaches. Otherwise the first books would hold a chapter nobody could open.
     */
    @Test
    void theFirstYearListCanBeReadFromNothing() throws IOException {
        Map<String, JsonObject> books = books();
        Set<String> pair = new HashSet<>();
        for (String id : SpellBooks.FIRST_YEAR_LIST) {
            pair.addAll(chapters(books.get(id)));
        }
        for (String spell : pair) {
            JsonObject def = JsonParser.parseString(Files.readString(SPELLS.resolve(spell + ".json"))).getAsJsonObject();
            JsonObject req = def.has("requirement") ? def.getAsJsonObject("requirement") : new JsonObject();
            String type = req.has("type") ? req.get("type").getAsString() : "none";
            String pre = req.has("prerequisiteId") ? req.get("prerequisiteId").getAsString().replace("wizards_and_beasts:", "") : "";
            assertTrue(type.equals("none") || (type.equals("knows") && pair.contains(pre)),
                    "first-year chapter " + spell + " needs " + type + " " + pre + ", which the first-year books do not teach");
        }
    }

    @Test
    void theBooksellerNeverSellsARestrictedBook() throws IOException {
        Map<String, JsonObject> books = books();
        JsonObject shop = JsonParser.parseString(Files.readString(DATA.resolve("locations/flourish_and_blotts.json")))
                .getAsJsonObject();
        Set<String> stocked = new HashSet<>();
        for (JsonElement e : shop.getAsJsonObject("market").getAsJsonArray("sells")) {
            JsonObject good = e.getAsJsonObject();
            assertTrue(good.has("spell_book"), "Flourish and Blotts sells " + good.get("id") + ", which is not a book");
            String book = good.get("spell_book").getAsString();
            assertTrue(books.containsKey(book), "Flourish and Blotts sells an unknown book " + book);
            JsonObject entry = books.get(book);
            assertFalse(entry.has("restricted") && entry.get("restricted").getAsBoolean(),
                    "Flourish and Blotts sells the restricted " + book);
            stocked.add(book);
        }
        for (String id : SpellBooks.FIRST_YEAR_LIST) {
            assertTrue(stocked.contains(id), "the first-year book " + id + " is not on sale");
        }
    }
}
