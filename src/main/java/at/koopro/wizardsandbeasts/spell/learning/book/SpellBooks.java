package at.koopro.wizardsandbeasts.spell.learning.book;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * The spell textbooks, as data: {@code spell_books/<id>.json} names the spells (chapters) one book teaches.
 *
 * <p>A book is one item ({@code standard_book_of_spells}) with the {@code spell_book} component naming an entry
 * here, so a pack can add a book without a new item. Its title and author are lang keys built from the id
 * ({@code spell_book.wizards_and_beasts.<path>.title} / {@code .author}), so the client names a book without this
 * table, which only the server holds; the chapter list reaches a reader in {@code SpellBookOpenS2CPayload}.
 *
 * <p>Canon base and placement: {@code tasks/spell_books_catalogue_2026_11_27.md} (owner OK 2026-11-27). Grade 1 is
 * the PS ch.5 / Pottermore list; {@code the_dark_forces} (Trimble) is the first-year Defence text that holds Lumos.
 */
@NullMarked
public final class SpellBooks {

    public static final String DIRECTORY = "spell_books";
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * The books this mod ships, in shelf order. The creative tab and the shop are built from it, so it names ids
     * rather than reading the server-side table (the client has none). {@code SpellBookCatalogueTest} pins that each
     * has a JSON file.
     */
    public static final List<String> SHIPPED = List.of(
            "standard_book_of_spells_grade_1",
            "the_dark_forces",
            "standard_book_of_spells_grade_2",
            "curses_and_counter_curses",
            "standard_book_of_spells_grade_3",
            "standard_book_of_spells_grade_4",
            "standard_book_of_spells_grade_5",
            "standard_book_of_spells_grade_6",
            "standard_book_of_spells_grade_7",
            "practical_defensive_magic",
            "achievements_in_charming",
            "half_blood_princes_potion_book",
            "magick_moste_evile");

    /** The first-year book list handed out by the Apprentice's Path (PS ch.5: Goshawk Grade 1 + Trimble). */
    public static final List<String> FIRST_YEAR_LIST = List.of("standard_book_of_spells_grade_1", "the_dark_forces");

    /**
     * One textbook.
     *
     * @param chapters   spell ids, in reading order
     * @param grade      school year it is set for, 0 for none
     * @param restricted kept off a bookseller's shelf (the Restricted Section's kind of book)
     */
    public record Book(List<String> chapters, int grade, boolean restricted) {
        public static final Codec<Book> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.listOf().fieldOf("chapters").forGetter(Book::chapters),
                Codec.INT.optionalFieldOf("grade", 0).forGetter(Book::grade),
                Codec.BOOL.optionalFieldOf("restricted", false).forGetter(Book::restricted)
        ).apply(i, Book::new));
    }

    private static volatile Map<Identifier, Book> books = Map.of();

    private SpellBooks() {
    }

    /** The book with this id ({@code "grade_1"} or {@code "wizards_and_beasts:grade_1"}), or null. */
    public static @Nullable Book get(String id) {
        Identifier key = Identifier.tryParse(id.contains(":") ? id : WizardsAndBeastsMod.MODID + ":" + id);
        return key == null ? null : books.get(key);
    }

    public static Map<Identifier, Book> all() {
        return books;
    }

    /** Replaces every book at once (reload, tests). */
    public static void replaceAll(Map<Identifier, Book> loaded) {
        books = Map.copyOf(loaded);
    }

    /** The canonical id stored on a stack: namespaced. */
    public static String canonical(String id) {
        return id.contains(":") ? id : WizardsAndBeastsMod.MODID + ":" + id;
    }

    /** The path half of a book id: what its lang keys and cover are named after. */
    public static String path(String id) {
        return id.substring(id.indexOf(':') + 1);
    }

    public static final class Loader extends SimpleJsonResourceReloadListener<Book> {

        public Loader() {
            super(Book.CODEC, FileToIdConverter.json(DIRECTORY));
        }

        @Override
        protected void apply(Map<Identifier, Book> entries, ResourceManager resourceManager, ProfilerFiller profiler) {
            replaceAll(entries);
            LOGGER.info("[Spells] Loaded {} spell book(s)", entries.size());
        }
    }
}
