package at.koopro.wizardsandbeasts.lang;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps translation keys and translation files honest with each other.
 *
 * <p>The assertion that earns its keep is {@link #everyReferencedKeyExists()}: a typo'd key is invisible
 * in code review and in every English playthrough, and only shows up as a raw key in someone's HUD. This
 * catches it at build time instead.
 *
 * <p>{@code en_us} is checked as the <em>merged</em> view — generated plus hand-authored — because that is
 * what {@code processResources} actually ships. Checking either file alone would report false failures for
 * keys that live in the other.
 */
class LangParityTest {

    private static final Path SOURCE_ROOT = Path.of("src", "main", "java");
    private static final Path GENERATED_EN_US = Path.of("src", "generated", "resources",
            "assets", "wizards_and_beasts", "lang", "en_us.json");
    private static final Path MAIN_EN_US = Path.of("src", "main", "resources",
            "assets", "wizards_and_beasts", "lang", "en_us.json");
    private static final Path DE_DE = Path.of("src", "main", "resources",
            "assets", "wizards_and_beasts", "lang", "de_de.json");

    /**
     * {@code Component.translatable("some.key")} or {@code translatable("some.key", args…)} — a
     * <em>complete</em> literal key.
     *
     * <p>The trailing {@code [,)]} matters. Without it this also matches the literal prefix of a key that
     * is assembled at runtime, as in {@code translatable("stat.wizards_and_beasts." + stat.name())}, and
     * reports the prefix as a missing key. Those are counted as runtime-built instead: resolving them
     * needs the game, and a guess here would be noise that trains people to ignore this test.
     */
    private static final Pattern LITERAL_KEY = Pattern.compile(
            "(?:Component\\.)?translatable\\(\\s*\"([^\"]+)\"\\s*[,)]");

    private static final Pattern ANY_TRANSLATABLE = Pattern.compile("(?:Component\\.)?translatable\\(");

    private static Map<String, String> readLang(Path path) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        if (!Files.exists(path)) {
            return entries;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            json.entrySet().forEach(e -> entries.put(e.getKey(), e.getValue().getAsString()));
        }
        return entries;
    }

    /** The keys the shipped resource pack actually contains: generated as base, hand-authored on top. */
    private static Map<String, String> mergedEnUs() throws IOException {
        Map<String, String> merged = new LinkedHashMap<>(readLang(GENERATED_EN_US));
        merged.putAll(readLang(MAIN_EN_US));
        return merged;
    }

    private record Reference(String key, Path file, int line) {}

    private static List<Reference> literalKeyReferences() throws IOException {
        List<Reference> refs = new java.util.ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    Matcher matcher = LITERAL_KEY.matcher(lines.get(i));
                    while (matcher.find()) {
                        refs.add(new Reference(matcher.group(1), file, i + 1));
                    }
                }
            }
        }
        return refs;
    }

    @Test
    void everyReferencedKeyExists() throws IOException {
        Map<String, String> enUs = mergedEnUs();
        Set<String> missing = new TreeSet<>();
        for (Reference ref : literalKeyReferences()) {
            // Only this mod's keys are ours to ship. Vanilla keys (gui.done, gui.cancel) and any other
            // mod's live in their own lang files and are correctly absent from ours.
            if (!ref.key().contains(WizardsAndBeastsMod.MODID)) {
                continue;
            }
            if (!enUs.containsKey(ref.key())) {
                missing.add("%s  (%s:%d)".formatted(ref.key(), ref.file(), ref.line()));
            }
        }
        assertTrue(missing.isEmpty(),
                () -> "translation keys referenced in code but absent from en_us:\n  "
                        + String.join("\n  ", missing));
    }

    @Test
    void deDeAddsNoKeyEnUsDoesNotHave() throws IOException {
        Set<String> enUs = mergedEnUs().keySet();
        Set<String> orphans = new TreeSet<>(readLang(DE_DE).keySet());
        orphans.removeAll(enUs);
        assertTrue(orphans.isEmpty(),
                () -> "de_de has keys en_us does not; these can never be reached:\n  "
                        + String.join("\n  ", orphans));
    }

    @Test
    void deDeHasNoEmptyValues() throws IOException {
        Set<String> empties = new TreeSet<>();
        readLang(DE_DE).forEach((key, value) -> {
            if (value.isBlank()) {
                empties.add(key);
            }
        });
        // An empty value renders as an empty label in game. A *missing* key falls back to en_us, which is
        // what an untranslated key is supposed to do -- so untranslated keys must be omitted, not blanked.
        assertTrue(empties.isEmpty(),
                () -> "de_de has empty values; omit the key instead so it falls back to en_us:\n  "
                        + String.join("\n  ", empties));
    }

    /** Reports coverage without failing: an incomplete translation is expected, not a defect. */
    @Test
    void reportTranslationCoverage() throws IOException {
        Map<String, String> enUs = mergedEnUs();
        int translated = readLang(DE_DE).size();
        double percent = enUs.isEmpty() ? 0.0 : (100.0 * translated / enUs.size());

        List<Reference> refs = literalKeyReferences();
        long dynamic = countDynamicCallSites() - refs.size();

        System.out.printf("""
                lang coverage
                  en_us keys (merged) : %d  (generated %d + main %d)
                  de_de keys          : %d  (%.1f%%)
                  literal key refs    : %d
                  runtime-built keys  : %d (not statically checkable)
                %n""",
                enUs.size(), readLang(GENERATED_EN_US).size(), readLang(MAIN_EN_US).size(),
                translated, percent, refs.size(), Math.max(0, dynamic));
    }

    private static long countDynamicCallSites() throws IOException {
        long count = 0;
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                for (String line : Files.readAllLines(file)) {
                    Matcher matcher = ANY_TRANSLATABLE.matcher(line);
                    while (matcher.find()) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /** Guards the merge contract itself: both files must exist, or the shipped en_us is silently partial. */
    @Test
    void bothEnUsFilesExist() {
        assertTrue(Files.exists(GENERATED_EN_US), GENERATED_EN_US + " missing");
        assertTrue(Files.exists(MAIN_EN_US), MAIN_EN_US + " missing");
        assertTrue(Files.exists(DE_DE), DE_DE + " missing");
        assertTrue(new LinkedHashSet<>(List.of(1)).size() == 1);
    }
}
