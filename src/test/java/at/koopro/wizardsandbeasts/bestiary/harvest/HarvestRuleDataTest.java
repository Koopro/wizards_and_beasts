package at.koopro.wizardsandbeasts.bestiary.harvest;

import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.bestiary.data.PlayerBestiaryData;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The datapack half: what a harvest rule accepts, what it refuses, and whether the rules the mod ships
 * name creatures and items that actually exist.
 *
 * <p>The last part earns its keep. An id into another registry that matches nothing is this
 * repository's classic silent datapack failure — the rule loads, never matches, and the material stays
 * unobtainable with no error anywhere.
 */
class HarvestRuleDataTest {

    private static final Gson GSON = new Gson();

    private static final Path HARVEST_DIR = Path.of("src", "main", "resources", "data",
            "wizards_and_beasts", "bestiary", "harvest");
    private static final Path ENTRY_DIR = Path.of("src", "main", "resources", "data",
            "wizards_and_beasts", "bestiary", "entries");

    @BeforeAll
    static void bootstrapMinecraft() {
        // The rule codec resolves its item through BuiltInRegistries.ITEM.
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static DataResult<HarvestRule> parse(String json) {
        return HarvestRule.CODEC.parse(JsonOps.INSTANCE, GSON.fromJson(json, JsonElement.class));
    }

    private static HarvestRule parseOrThrow(String json) {
        return parse(json).getOrThrow(msg -> new AssertionError("expected to parse: " + msg));
    }

    private static List<Path> shippedRuleFiles() throws Exception {
        try (Stream<Path> stream = Files.list(HARVEST_DIR)) {
            return stream.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
    }

    // ── the shape ──

    @Test
    void aRuleParsesFromTheDocumentedShape() {
        HarvestRule rule = parseOrThrow("""
                {
                  "entry": "wizards_and_beasts:unicorn",
                  "item": "minecraft:stick",
                  "minTier": "MASTERED",
                  "chance": 0.35,
                  "cooldownSeconds": 600
                }
                """);

        assertEquals(Identifier.fromNamespaceAndPath("wizards_and_beasts", "unicorn"), rule.entry());
        assertEquals(DiscoveryTier.MASTERED, rule.minTier());
        assertEquals(0.35f, rule.chance(), 1e-5);
        assertEquals(600, rule.cooldownSeconds());
        assertEquals(1, rule.minCount());
        assertEquals(1, rule.maxCount());
    }

    @Test
    void chanceAndCountsAndCooldownAreAllOptional() {
        HarvestRule rule = parseOrThrow("""
                { "entry": "wizards_and_beasts:unicorn", "item": "minecraft:stick", "minTier": "STUDIED" }
                """);
        assertEquals(1.0f, rule.chance(), 1e-5, "an omitted chance is certain");
        assertEquals(0, rule.cooldownSeconds(), "an omitted cooldown is none");
    }

    // ── refusals: a rule that cannot work is a load failure, not a silent no-op ──

    @Test
    void aRuleGatedOnNothingIsRefused() {
        DataResult<HarvestRule> result = parse("""
                { "entry": "wizards_and_beasts:unicorn", "item": "minecraft:stick",
                  "minTier": "UNDISCOVERED" }
                """);
        assertTrue(result.isError());
        assertTrue(result.error().orElseThrow().message().contains("loot table"),
                "the error should point at where an unconditional drop belongs");
    }

    @Test
    void aRuleThatCanNeverDropIsRefused() {
        assertTrue(parse("""
                { "entry": "wizards_and_beasts:unicorn", "item": "minecraft:stick",
                  "minTier": "STUDIED", "chance": 0.0 }
                """).isError());
    }

    @Test
    void aChanceOutsideZeroToOneIsRefused() {
        assertTrue(parse("""
                { "entry": "wizards_and_beasts:unicorn", "item": "minecraft:stick",
                  "minTier": "STUDIED", "chance": 1.5 }
                """).isError());
        assertTrue(parse("""
                { "entry": "wizards_and_beasts:unicorn", "item": "minecraft:stick",
                  "minTier": "STUDIED", "chance": -0.2 }
                """).isError());
    }

    @Test
    void anUnknownItemIsRefused() {
        assertTrue(parse("""
                { "entry": "wizards_and_beasts:unicorn", "item": "wizards_and_beasts:no_such_item",
                  "minTier": "STUDIED" }
                """).isError());
    }

    @Test
    void anUnknownTierIsRefused() {
        assertTrue(parse("""
                { "entry": "wizards_and_beasts:unicorn", "item": "minecraft:stick",
                  "minTier": "LEGENDARY" }
                """).isError());
    }

    // ── what the mod actually ships ──

    @Test
    void everyShippedRuleParses() throws Exception {
        assertTrue(Files.isDirectory(HARVEST_DIR), "shipped harvest directory is missing: " + HARVEST_DIR);
        List<Path> files = shippedRuleFiles();
        assertFalse(files.isEmpty(),
                "the mod should ship at least one harvest rule, or the tier gate is inert");

        for (Path file : files) {
            DataResult<HarvestRule> result = parse(Files.readString(file));
            assertTrue(result.result().isPresent(),
                    () -> file.getFileName() + " failed to parse: "
                            + result.error().map(e -> e.message()).orElse("?"));
        }
    }

    /**
     * A rule naming an entry that does not exist would load, index to nothing and never fire. The
     * loader warns about it at runtime; this turns it into a red build instead.
     */
    @Test
    void everyShippedRuleNamesABestiaryEntryThatExistsAndHasAnEntityType() throws Exception {
        for (Path file : shippedRuleFiles()) {
            HarvestRule rule = parseOrThrow(Files.readString(file));

            Path entryFile = ENTRY_DIR.resolve(rule.entry().getPath() + ".json");
            assertTrue(Files.exists(entryFile),
                    () -> file.getFileName() + " names bestiary entry " + rule.entry()
                            + ", which has no entry file at " + entryFile);

            JsonObject entry = JsonParser.parseString(Files.readString(entryFile)).getAsJsonObject();
            assertTrue(entry.has("entityType"),
                    () -> rule.entry() + " has no entityType, so nothing in the world can ever drop "
                            + "the material " + file.getFileName() + " gates");
        }
    }

    /**
     * The entry existing is not enough: its {@code entityType} has to name a creature that is actually
     * registered, or the rule indexes under an id nothing in the world will ever present and the
     * material stays unobtainable with no error anywhere.
     */
    @Test
    void everyShippedRuleTargetsARegisteredCreature() throws Exception {
        Path creatures = Path.of("src", "main", "java", "at", "koopro", "wizardsandbeasts",
                "registry", "ModCreatures.java");
        Path entities = Path.of("src", "main", "java", "at", "koopro", "wizardsandbeasts",
                "registry", "ModEntities.java");
        String registrations = Files.readString(creatures)
                + (Files.exists(entities) ? Files.readString(entities) : "");

        for (Path file : shippedRuleFiles()) {
            HarvestRule rule = parseOrThrow(Files.readString(file));
            JsonObject entry = JsonParser
                    .parseString(Files.readString(ENTRY_DIR.resolve(rule.entry().getPath() + ".json")))
                    .getAsJsonObject();
            String path = Identifier.parse(entry.get("entityType").getAsString()).getPath();

            assertTrue(registrations.contains('"' + path + '"'),
                    () -> file.getFileName() + " targets entity '" + path
                            + "', which is not registered in ModCreatures or ModEntities");
        }
    }

    @Test
    void everyShippedRuleNamesARegisteredItem() throws Exception {
        for (Path file : shippedRuleFiles()) {
            // Parsing already resolved the item through the registry, so reaching here proves it
            // exists; this pins the intent so a future refactor of the codec cannot quietly drop it.
            HarvestRule rule = parseOrThrow(Files.readString(file));
            assertNotNull(rule.item());
            assertTrue(BuiltInRegistries.ITEM.containsValue(rule.item()),
                    () -> file.getFileName() + " names an unregistered item");
        }
    }

    /**
     * The whole point of the feature: the shipped set has to make a high tier matter. A pack of rules
     * that all sit at SIGHTED would load, pass every other test, and gate nothing worth studying for.
     */
    @Test
    void theShippedRulesActuallyRequireStudy() throws Exception {
        boolean anyMastered = false;
        for (Path file : shippedRuleFiles()) {
            HarvestRule rule = parseOrThrow(Files.readString(file));
            assertTrue(rule.minTier().ordinal() >= DiscoveryTier.STUDIED.ordinal(),
                    () -> file.getFileName() + " gates on " + rule.minTier()
                            + ", which a player reaches without deliberately studying anything");
            anyMastered |= rule.minTier() == DiscoveryTier.MASTERED;
        }
        assertTrue(anyMastered, "at least one shipped material should require MASTERED");
    }

    // ── persistence ──

    @Test
    void aSaveWrittenBeforeHarvestExistedKeepsItsDiscoveries() {
        // Exactly the shape the bestiary codec wrote before lastHarvests existed.
        PlayerBestiaryData restored = PlayerBestiaryData.CODEC
                .parse(JsonOps.INSTANCE, GSON.fromJson(
                        "{\"tiers\": {\"wizards_and_beasts:unicorn\": \"MASTERED\"}}", JsonElement.class))
                .getOrThrow(msg -> new AssertionError("legacy save failed to parse: " + msg));

        assertEquals(DiscoveryTier.MASTERED,
                restored.tiers().get(Identifier.fromNamespaceAndPath("wizards_and_beasts", "unicorn")),
                "a required lastHarvests field would have failed the parse and wiped the bestiary");
        assertTrue(restored.lastHarvests().isEmpty(), "and the player owes no lockout they never earned");
    }

    @Test
    void anEmptySaveIsAnEmptyBestiary() {
        PlayerBestiaryData restored = PlayerBestiaryData.CODEC
                .parse(JsonOps.INSTANCE, GSON.fromJson("{}", JsonElement.class))
                .getOrThrow(msg -> new AssertionError(msg));
        assertTrue(restored.tiers().isEmpty());
        assertTrue(restored.lastHarvests().isEmpty());
    }

    @Test
    void tiersAndLockoutsBothSurviveARoundTrip() {
        Identifier unicorn = Identifier.fromNamespaceAndPath("wizards_and_beasts", "unicorn");
        PlayerBestiaryData original = new PlayerBestiaryData(
                new java.util.HashMap<>(Map.of(unicorn, DiscoveryTier.MASTERED)),
                new java.util.HashMap<>(Map.of(unicorn, 123_456L)));

        JsonElement encoded = PlayerBestiaryData.CODEC.encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow(msg -> new AssertionError("encode failed: " + msg));
        PlayerBestiaryData restored = PlayerBestiaryData.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(msg -> new AssertionError("parse failed: " + msg));

        assertEquals(DiscoveryTier.MASTERED, restored.tiers().get(unicorn));
        assertEquals(123_456L, restored.lastHarvests().get(unicorn));
    }

    /**
     * Two players are two records, and the maps inside one are not shared with the other. The
     * single-map convenience constructor is the one that could have leaked a shared instance.
     */
    @Test
    void oneBestiaryRecordCannotReachAnother() {
        Identifier unicorn = Identifier.fromNamespaceAndPath("wizards_and_beasts", "unicorn");
        PlayerBestiaryData a = new PlayerBestiaryData();
        PlayerBestiaryData b = new PlayerBestiaryData();

        a.tiers().put(unicorn, DiscoveryTier.MASTERED);
        a.lastHarvests().put(unicorn, 99L);

        assertTrue(b.tiers().isEmpty(), "one player's discoveries must not appear on another's record");
        assertTrue(b.lastHarvests().isEmpty(), "nor one player's lockouts");
    }
}
