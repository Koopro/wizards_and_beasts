package at.koopro.wizardsandbeasts.resources;

import at.koopro.wizardsandbeasts.bestiary.BestiaryEntry;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the Ministry of Magic classification domain in the shipped bestiary datapack.
 *
 * <p>The Ministry scale in <em>Fantastic Beasts and Where to Find Them</em> has exactly five grades
 * (X…XXXXX), so {@code mmRating} is valid only over 1–5. The field is optional: absence means the
 * creature holds no grade at all, which is the correct state for an ordinary animal with magical
 * uses (a toad, an owl) rather than assigning it the real grade {@code X}.
 */
class BestiaryClassificationRangeTest {

    private static final Path ENTRY_DIR =
            Path.of("src", "main", "resources", "data", "wizards_and_beasts", "bestiary", "entries");

    @Test
    void everyEntryParsesAndRatesWithinTheFiveGradeScale() throws IOException {
        List<Path> files = entryFiles();
        assertFalse(files.isEmpty(), "no bestiary entries found under " + ENTRY_DIR);

        for (Path file : files) {
            JsonElement json = JsonParser.parseString(Files.readString(file));
            BestiaryEntry entry = BestiaryEntry.CODEC.parse(JsonOps.INSTANCE, json)
                    .getOrThrow(msg -> new AssertionError(file + ": " + msg));

            entry.mmRating().ifPresent(rating -> assertTrue(rating >= 1 && rating <= 5,
                    file + ": mmRating " + rating + " is outside the five-grade Ministry scale (1-5)"));
        }
    }

    /** Absence must round-trip as absence, not as a defaulted grade. */
    @Test
    void anEntryWithoutAnMmRatingParsesAsUnclassified() throws IOException {
        List<Path> unrated = new ArrayList<>();
        for (Path file : entryFiles()) {
            JsonObject obj = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (obj.has("mmRating")) {
                continue;
            }
            unrated.add(file);
            BestiaryEntry entry = BestiaryEntry.CODEC.parse(JsonOps.INSTANCE, obj)
                    .getOrThrow(msg -> new AssertionError(file + ": " + msg));
            assertEquals(Optional.empty(), entry.mmRating(),
                    file + ": a missing mmRating must stay absent, not acquire a default grade");
        }
        assertFalse(unrated.isEmpty(),
                "expected at least one unclassified entry (toad) to exercise the absent-rating path");
    }

    /** The codec must reject an out-of-scale grade at load rather than rendering nine X glyphs. */
    @Test
    void codecRejectsRatingsOutsideTheScale() throws IOException {
        JsonObject template = JsonParser.parseString(Files.readString(entryFiles().getFirst()))
                .getAsJsonObject();

        for (int rating : new int[]{0, 6, 9, -1}) {
            JsonObject mutated = template.deepCopy();
            mutated.addProperty("mmRating", rating);
            assertTrue(BestiaryEntry.CODEC.parse(JsonOps.INSTANCE, mutated).isError(),
                    "mmRating " + rating + " is outside 1-5 and must fail to parse");
        }
    }

    private static List<Path> entryFiles() throws IOException {
        try (Stream<Path> files = Files.list(ENTRY_DIR)) {
            return files.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
    }
}
