package at.koopro.wizardsandbeasts.standing;

import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.standing.deed.Deed;
import at.koopro.wizardsandbeasts.standing.deed.DeedTrigger;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The datapack half: what a deed accepts, what it refuses, and what the mod actually ships.
 *
 * <p>The refusals matter more than the acceptances. A deed that parses but can never fire is
 * invisible — it produces no error and no effect — so every way of writing one is turned into a load
 * failure here rather than left to be discovered as "the alignment system doesn't seem to work".
 */
class StandingDeedTest {

    private static final Gson GSON = new Gson();

    private static DataResult<Deed> parse(String json) {
        return Deed.CODEC.parse(JsonOps.INSTANCE, GSON.fromJson(json, JsonElement.class));
    }

    private static Deed parseOrThrow(String json) {
        return parse(json).getOrThrow(msg -> new AssertionError("expected to parse: " + msg));
    }

    // ── what a valid deed looks like ──

    @Test
    void aDeedParsesFromTheDocumentedShape() {
        Deed deed = parseOrThrow("""
                {
                  "trigger": "spell_cast",
                  "match": "expecto_patronum",
                  "effects": { "alignment": 3.0 },
                  "cooldownSeconds": 300
                }
                """);

        assertEquals(DeedTrigger.SPELL_CAST, deed.trigger());
        assertEquals("expecto_patronum", deed.match());
        assertEquals(3.0f, deed.effects().get(StandingAxis.ALIGNMENT), 1e-4);
        assertEquals(300, deed.cooldownSeconds());
    }

    @Test
    void oneDeedMayMoveSeveralAxesAtOnce() {
        Deed deed = parseOrThrow("""
                { "trigger": "offence", "effects": { "tradition": 2.0, "alignment": 1.0 } }
                """);
        assertEquals(2, deed.effects().size(),
                "a single act saying two things about you is the point of the model");
    }

    @Test
    void anAbsentCooldownMeansEveryOccurrenceCounts() {
        assertEquals(0, parseOrThrow("""
                { "trigger": "offence", "effects": { "tradition": 1.0 } }
                """).cooldownSeconds());
    }

    @Test
    void aNegativeCooldownIsFlooredRatherThanInverted() {
        assertEquals(0, parseOrThrow("""
                { "trigger": "offence", "effects": { "tradition": 1.0 }, "cooldownSeconds": -50 }
                """).cooldownSeconds());
    }

    // ── invalid deeds are load failures, not silent no-ops ──

    @Test
    void aDeedWithNoEffectsIsRefused() {
        assertTrue(parse("{ \"trigger\": \"offence\", \"effects\": {} }").isError(),
                "an empty effects map could never do anything");
    }

    @Test
    void aZeroOrNaNDeltaIsRefused() {
        assertTrue(parse("""
                { "trigger": "offence", "effects": { "tradition": 0.0 } }
                """).isError());
    }

    /**
     * The Ministry axis is a view over the criminal record. Letting a deed write it would create the
     * second source of truth the whole design exists to avoid.
     */
    @Test
    void aDeedCannotWriteTheDerivedMinistryAxis() {
        DataResult<Deed> result = parse("""
                { "trigger": "offence", "effects": { "ministry": -5.0 } }
                """);
        assertTrue(result.isError());
        assertTrue(result.error().orElseThrow().message().contains("derived"),
                "the error should say why, not just refuse");
    }

    /**
     * Darkening belongs to {@code DarkCorruptionService}, which applies vocation scaling. A deed
     * bypassing it would make a Dark Arts vocation's corruption discount silently stop applying.
     */
    @Test
    void aDeedCannotDarkenAlignmentDirectly() {
        DataResult<Deed> result = parse("""
                { "trigger": "spell_cast", "match": "crucio", "effects": { "alignment": -8.0 } }
                """);
        assertTrue(result.isError());
        assertTrue(result.error().orElseThrow().message().contains("DarkCorruptionService"));
    }

    @Test
    void anUnknownTriggerIsARefusalRatherThanADeedThatNeverFires() {
        assertTrue(parse("""
                { "trigger": "player_sneezed", "effects": { "tradition": 1.0 } }
                """).isError());
    }

    @Test
    void anUnknownAxisIsRefused() {
        assertTrue(parse("""
                { "trigger": "offence", "effects": { "vibes": 1.0 } }
                """).isError());
    }

    @Test
    void aTierFloorOnlyMakesSenseOnTheBestiaryTrigger() {
        assertTrue(parse("""
                { "trigger": "spell_cast", "minTier": "STUDIED", "effects": { "tradition": 1.0 } }
                """).isError());
        assertTrue(parse("""
                { "trigger": "bestiary_tier", "minTier": "STUDIED", "effects": { "tradition": 1.0 } }
                """).result().isPresent());
    }

    // ── matching ──

    @Test
    void anAbsentMatchAcceptsEveryEventOfItsTrigger() {
        Deed broad = parseOrThrow("""
                { "trigger": "bestiary_tier", "effects": { "tradition": -1.0 } }
                """);
        assertTrue(broad.matches("wizards_and_beasts:niffler"));
        assertTrue(broad.matches("anything_at_all"));
        assertTrue(broad.matches(null));
    }

    @Test
    void aBarePathMatchesTheNamespacedIdSoContentNeedNotRepeatTheNamespace() {
        Deed deed = parseOrThrow("""
                { "trigger": "spell_cast", "match": "protego", "effects": { "alignment": 0.5 } }
                """);
        assertTrue(deed.matches("protego"));
        assertTrue(deed.matches("wizards_and_beasts:protego"));
        assertTrue(deed.matches("WIZARDS_AND_BEASTS:PROTEGO"), "matching is case-insensitive");
        assertFalse(deed.matches("protego_totalum"), "a prefix is not a match");
        assertFalse(deed.matches("expelliarmus"));
        assertFalse(deed.matches(null));
    }

    @Test
    void aNamespacedMatchDoesNotAccidentallyAcceptAnotherNamespace() {
        Deed deed = parseOrThrow("""
                { "trigger": "spell_cast", "match": "othermod:protego", "effects": { "alignment": 0.5 } }
                """);
        assertTrue(deed.matches("othermod:protego"));
        assertFalse(deed.matches("wizards_and_beasts:protego"));
    }

    @Test
    void aTierFloorRejectsEverythingBelowItAndAcceptsEverythingAbove() {
        Deed deed = parseOrThrow("""
                { "trigger": "bestiary_tier", "minTier": "STUDIED", "effects": { "tradition": -1.5 } }
                """);
        assertFalse(deed.meetsTier(DiscoveryTier.SIGHTED));
        assertFalse(deed.meetsTier(DiscoveryTier.ENCOUNTERED));
        assertTrue(deed.meetsTier(DiscoveryTier.STUDIED));
        assertTrue(deed.meetsTier(DiscoveryTier.MASTERED));
        assertFalse(deed.meetsTier(null), "no tier reported cannot clear a floor");
    }

    // ── what the mod actually ships ──

    /**
     * Every shipped deed is parsed with the real codec. A datapack file that fails to load is a WARN
     * on a boot log nobody reads; this turns it into a red build.
     */
    @Test
    void everyShippedDeedParses() throws Exception {
        Path dir = Path.of("src", "main", "resources", "data", "wizards_and_beasts", "magical_deeds");
        assertTrue(Files.isDirectory(dir), "shipped deed directory is missing: " + dir);

        List<Path> files;
        try (Stream<Path> stream = Files.list(dir)) {
            files = stream.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        assertFalse(files.isEmpty(), "the mod should ship at least one deed, or the system is inert");

        for (Path file : files) {
            DataResult<Deed> result = parse(Files.readString(file));
            assertTrue(result.result().isPresent(),
                    () -> file.getFileName() + " failed to parse: "
                            + result.error().map(e -> e.message()).orElse("?"));
        }
    }

    /**
     * Shipped deeds name real spells and real offences. Datapack ids into another registry are the
     * repository's classic silent failure — a typo simply never matches anything.
     */
    @Test
    void shippedDeedsNameThingsThatExist() throws Exception {
        Path dir = Path.of("src", "main", "resources", "data", "wizards_and_beasts", "magical_deeds");
        List<Path> files;
        try (Stream<Path> stream = Files.list(dir)) {
            files = stream.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }

        for (Path file : files) {
            Deed deed = parseOrThrow(Files.readString(file));
            String match = deed.match();
            if (match == null) {
                continue;
            }
            switch (deed.trigger()) {
                case SPELL_CAST -> assertTrue(spellExists(match),
                        () -> file.getFileName() + " names spell '" + match + "', which is neither a "
                                + "datapack spell nor one of the bespoke implementations");
                case OFFENCE -> assertTrue(
                        at.koopro.wizardsandbeasts.ministry.law.MagicalOffence.byName(match) != null,
                        () -> file.getFileName() + " names offence '" + match + "', which does not exist");
                case BESTIARY_TIER -> { /* entry ids are datapack-to-datapack; the loader reports misses */ }
            }
        }
    }

    /**
     * A spell exists if a {@code spells/*.json} file defines it, or if a bespoke {@code spell/impl}
     * class registers it. Both are real sources — Expecto Patronum and Protego are Java-side only.
     */
    private static boolean spellExists(String id) throws Exception {
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;

        Path json = Path.of("src", "main", "resources", "data", "wizards_and_beasts", "spells",
                path + ".json");
        if (Files.exists(json)) {
            return true;
        }
        Path impls = Path.of("src", "main", "java", "at", "koopro", "wizardsandbeasts", "spell", "impl");
        if (!Files.isDirectory(impls)) {
            return false;
        }
        try (Stream<Path> stream = Files.list(impls)) {
            for (Path file : stream.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (Files.readString(file).contains("super(\"" + path + "\"")) {
                    return true;
                }
            }
        }
        return false;
    }
}
