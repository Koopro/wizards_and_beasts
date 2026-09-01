package at.koopro.wizardsandbeasts.handbook;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
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
 * Every shipped handbook chapter decodes, and the index it produces is coherent.
 *
 * <p>A chapter is pure datapack JSON read by a {@code SimpleJsonResourceReloadListener}, which means
 * a schema mistake does not fail a build, fail a boot, or throw anywhere a developer is looking — the
 * listener logs one line and the chapter is simply absent from the book. The failure mode of a
 * mistyped page {@code type} or a missing {@code sort_index} is a handbook that quietly has one fewer
 * chapter than its author thinks, which is exactly the sort of thing nobody notices until a player
 * asks where the instructions went.
 *
 * <p>So these tests are the boot the chapters never get. They read the files off disk the way
 * {@code BroomDefinitionCodecTest} does, rather than through a resource pack, because the point is to
 * check what is committed.
 */
class HandbookChapterCodecTest {

    private static final Gson GSON = new Gson();
    private static final Path CHAPTER_DIR =
            Path.of("src", "main", "resources", "data", "wizards_and_beasts", "handbook", "chapters");

    private static List<Path> chapterFiles() throws IOException {
        try (Stream<Path> files = Files.list(CHAPTER_DIR)) {
            List<Path> jsons = files.filter(p -> p.toString().endsWith(".json")).sorted().toList();
            assertFalse(jsons.isEmpty(), "expected handbook chapter JSONs on disk");
            return jsons;
        }
    }

    private static HandbookChapter parse(Path json) throws IOException {
        JsonElement element = GSON.fromJson(Files.readString(json), JsonElement.class);
        return HandbookChapter.CODEC.parse(JsonOps.INSTANCE, element)
                .getOrThrow(err -> new AssertionError(json.getFileName() + ": " + err));
    }

    // ── the schema ──

    @Test
    void everyShippedChapterDecodes() throws IOException {
        for (Path json : chapterFiles()) {
            HandbookChapter chapter = parse(json);
            assertFalse(chapter.pages().isEmpty(), json.getFileName() + ": a chapter with no pages");
        }
    }

    @Test
    void everyChapterIdMatchesItsFilename() throws IOException {
        // The listener keys chapters by file path, so an id that disagrees with the filename is a
        // chapter that answers to two different names depending on who is asking.
        for (Path json : chapterFiles()) {
            String stem = json.getFileName().toString().replace(".json", "");
            assertEquals(stem, parse(json).id().getPath(),
                    json.getFileName() + ": chapter id must match its filename");
        }
    }

    // ── the index ──

    @Test
    void sortIndicesAreUnique() throws IOException {
        // Ties fall back to id order, so a duplicate is not a crash — it is two chapters whose
        // positions in the book swap around for reasons the author never chose.
        Map<Integer, String> seen = new HashMap<>();
        for (Path json : chapterFiles()) {
            HandbookChapter chapter = parse(json);
            String previous = seen.put(chapter.sortIndex(), chapter.id().getPath());
            assertEquals(null, previous, "sort_index " + chapter.sortIndex()
                    + " is claimed by both " + previous + " and " + chapter.id().getPath());
        }
    }

    @Test
    void titlesAreLangKeysAndDistinct() throws IOException {
        Set<String> titles = new HashSet<>();
        for (Path json : chapterFiles()) {
            HandbookChapter chapter = parse(json);
            String title = chapter.title();
            assertTrue(title.startsWith("handbook.wizards_and_beasts."),
                    chapter.id() + ": title must be a lang key, got \"" + title + "\"");
            assertTrue(titles.add(title), "two chapters share the title key " + title);
        }
    }

    // ── the text ──

    @Test
    void everyTextPageUsesLangKeysRatherThanProse() throws IOException {
        // The one mistake that looks right in the JSON: writing the sentence itself into `body`
        // instead of the key that holds it. It renders — as the raw prose, untranslatable — so it
        // survives a visual check.
        List<String> offenders = new ArrayList<>();
        for (Path json : chapterFiles()) {
            HandbookChapter chapter = parse(json);
            for (HandbookPage page : chapter.pages()) {
                if (!(page instanceof HandbookPage.Text text)) {
                    continue;
                }
                if (!isLangKey(text.body())) {
                    offenders.add(chapter.id() + " body: " + truncate(text.body()));
                }
                text.heading().filter(h -> !isLangKey(h))
                        .ifPresent(h -> offenders.add(chapter.id() + " heading: " + truncate(h)));
            }
        }
        assertTrue(offenders.isEmpty(), "handbook text must be lang keys, not prose: " + offenders);
    }

    private static boolean isLangKey(String value) {
        return value.startsWith("handbook.wizards_and_beasts.") && !value.contains(" ");
    }

    private static String truncate(String value) {
        return value.length() <= 40 ? value : value.substring(0, 40) + "...";
    }
}
