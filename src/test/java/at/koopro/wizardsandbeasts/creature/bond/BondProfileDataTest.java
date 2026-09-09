package at.koopro.wizardsandbeasts.creature.bond;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a bond profile accepts, what it refuses, and whether the four the mod ships describe
 * creatures and items that exist.
 *
 * <p>The last part earns its keep twice over. A profile is the <em>whole</em> of a species' opt-in
 * — there is no {@code bondable} flag in Java to fall back on — so a file named after a creature
 * that does not exist produces a creature that silently never bonds, with no error anywhere. And
 * because the Niffler's numbers moved out of {@code NifflerEntity} into
 * {@code creature_bonds/niffler.json}, a typo in that one file is now the difference between the
 * mod's flagship creature working and quietly losing its bond system.
 */
class BondProfileDataTest {

    private static final Gson GSON = new Gson();

    private static final Path BOND_DIR = Path.of("src", "main", "resources", "data",
            "wizards_and_beasts", "creature_bonds");
    private static final Path CREATURES_JAVA = Path.of("src", "main", "java", "at", "koopro",
            "wizardsandbeasts", "registry", "ModCreatures.java");

    @BeforeAll
    static void bootstrapMinecraft() {
        // BondFeed and BondGift resolve their items through BuiltInRegistries.ITEM.
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static DataResult<BondProfile> parse(String json) {
        return BondProfile.CODEC.parse(JsonOps.INSTANCE, GSON.fromJson(json, JsonElement.class));
    }

    private static BondProfile parseOrThrow(String json) {
        return parse(json).getOrThrow(msg -> new AssertionError("expected to parse: " + msg));
    }

    private static List<Path> shippedProfiles() throws IOException {
        try (Stream<Path> files = Files.list(BOND_DIR)) {
            return files.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
    }

    private static BondProfile shipped(String creature) throws IOException {
        return parseOrThrow(Files.readString(BOND_DIR.resolve(creature + ".json")));
    }

    // ── the shape ────────────────────────────────────────────────────────────

    @Test
    void aProfileParsesFromTheDocumentedShape() {
        BondProfile profile = parseOrThrow("""
                {
                  "feeds": [ { "item": "minecraft:stick", "gain": 10, "cooldownSeconds": 45 } ],
                  "followThreshold": 50,
                  "milestones": [25, 50, 75, 100],
                  "gift": { "item": "minecraft:feather", "minBond": 75, "cooldownSeconds": 900 }
                }
                """);

        assertEquals(1, profile.feeds().size());
        assertEquals(10, profile.feeds().getFirst().gain());
        assertEquals(45, profile.feeds().getFirst().cooldownSeconds());
        assertEquals(50, profile.followThreshold());
        assertEquals(List.of(25, 50, 75, 100), profile.milestones());
        assertEquals(75, profile.gift().orElseThrow().minBond());
    }

    @Test
    void everythingButTheWayItGainsBondIsOptional() {
        BondProfile profile = parseOrThrow("""
                { "feeds": [ { "item": "minecraft:stick", "gain": 5 } ] }
                """);
        assertEquals(100, profile.maxBond(), "the ceiling defaults to the Niffler's");
        assertEquals(50, profile.followThreshold());
        assertEquals(List.of(25, 50, 75, 100), profile.milestones());
        assertTrue(profile.gift().isEmpty());
        assertTrue(profile.masteryBond().isEmpty());
    }

    @Test
    void milestonesAreSortedAndDeduplicated() {
        BondProfile profile = parseOrThrow("""
                { "feeds": [ { "item": "minecraft:stick", "gain": 5 } ],
                  "milestones": [100, 25, 50, 25] }
                """);
        assertEquals(List.of(25, 50, 100), profile.milestones());
    }

    @Test
    void theXpTagFallsBackToTheCreatureName() {
        BondProfile profile = parseOrThrow("""
                { "feeds": [ { "item": "minecraft:stick", "gain": 5 } ] }
                """);
        assertEquals("bowtruckle_bond_50",
                profile.xpTag(Identifier.fromNamespaceAndPath("wizards_and_beasts", "bowtruckle"), 50));
    }

    @Test
    void feedForMatchesOnlyTheItemsListed() {
        BondProfile profile = parseOrThrow("""
                { "feeds": [ { "item": "minecraft:stick", "gain": 5 } ] }
                """);
        assertNotNull(profile.feedFor(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK)));
        assertNull(profile.feedFor(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND)));
    }

    // ── refusals: a profile that cannot work is a load failure ────────────────

    @Test
    void aProfileThatCanNeverGainBondIsRefused() {
        DataResult<BondProfile> result = parse("""
                { "feeds": [], "proximityGain": 0 }
                """);
        assertTrue(result.isError(), "no feeds and no proximity gain can never raise the bond");
    }

    @Test
    void aFeedWorthNothingIsRefused() {
        assertTrue(parse("""
                { "feeds": [ { "item": "minecraft:stick", "gain": 0 } ] }
                """).isError());
    }

    @Test
    void aFollowThresholdAboveTheCeilingIsRefused() {
        assertTrue(parse("""
                { "feeds": [ { "item": "minecraft:stick", "gain": 5 } ],
                  "maxBond": 50, "followThreshold": 80 }
                """).isError());
    }

    @Test
    void aMilestoneAboveTheCeilingIsRefused() {
        assertTrue(parse("""
                { "feeds": [ { "item": "minecraft:stick", "gain": 5 } ],
                  "maxBond": 50, "followThreshold": 10, "milestones": [25, 80] }
                """).isError());
    }

    /** An unbounded supply on a zero cooldown is an item printer, not a balance mistake. */
    @Test
    void aGiftWithNoCooldownIsRefused() {
        assertTrue(parse("""
                { "feeds": [ { "item": "minecraft:stick", "gain": 5 } ],
                  "gift": { "item": "minecraft:diamond", "cooldownSeconds": 0 } }
                """).isError());
    }

    @Test
    void aGiftAboveTheCeilingIsRefused() {
        assertTrue(parse("""
                { "feeds": [ { "item": "minecraft:stick", "gain": 5 } ],
                  "maxBond": 50, "followThreshold": 10, "milestones": [25],
                  "gift": { "item": "minecraft:diamond", "minBond": 90, "cooldownSeconds": 60 } }
                """).isError());
    }

    /** An unbounded pair on a zero cooldown is a mob spawner, not a balance mistake. */
    @Test
    void breedingWithNoCooldownIsRefused() {
        assertTrue(parse("""
                { "feeds": [ { "item": "minecraft:wheat", "gain": 5 } ],
                  "breeding": { "item": "minecraft:wheat", "cooldownSeconds": 0 } }
                """).isError());
    }

    @Test
    void breedingAboveTheCeilingIsRefused() {
        assertTrue(parse("""
                { "feeds": [ { "item": "minecraft:wheat", "gain": 5 } ],
                  "maxBond": 50, "followThreshold": 10, "milestones": [25],
                  "breeding": { "item": "minecraft:wheat", "minBond": 90, "cooldownSeconds": 600 } }
                """).isError());
    }

    @Test
    void anUnknownItemIsRefused() {
        assertTrue(parse("""
                { "feeds": [ { "item": "wizards_and_beasts:no_such_item", "gain": 5 } ] }
                """).isError());
    }

    // ── what the mod actually ships ──────────────────────────────────────────

    @Test
    void everyShippedProfileParses() throws IOException {
        List<Path> files = shippedProfiles();
        assertFalse(files.isEmpty(), "the mod should ship bond profiles, or the layer is inert");
        for (Path file : files) {
            DataResult<BondProfile> result = parse(Files.readString(file));
            assertTrue(result.result().isPresent(),
                    () -> file.getFileName() + " failed to parse: "
                            + result.error().map(e -> e.message()).orElse("?"));
        }
    }

    /**
     * A profile is keyed by creature id and nothing cross-checks it at load, so one named after a
     * creature that does not exist is a file that looks like configured content and does nothing.
     */
    @Test
    void everyShippedProfileNamesARegisteredCreature() throws IOException {
        String registrations = Files.readString(CREATURES_JAVA);
        for (Path file : shippedProfiles()) {
            String id = file.getFileName().toString().replace(".json", "");
            assertTrue(registrations.contains('"' + id + '"'),
                    () -> "creature_bonds/" + file.getFileName() + " names '" + id
                            + "', which is not in ModCreatures.MANIFEST or BESPOKE_IDS");
        }
    }

    @Test
    void everyShippedProfileNamesRegisteredItems() throws IOException {
        for (Path file : shippedProfiles()) {
            BondProfile profile = parseOrThrow(Files.readString(file));
            for (BondFeed feed : profile.feeds()) {
                assertTrue(BuiltInRegistries.ITEM.containsValue(feed.item()),
                        () -> file.getFileName() + " feeds an unregistered item");
            }
            profile.gift().ifPresent(gift -> assertTrue(BuiltInRegistries.ITEM.containsValue(gift.item()),
                    () -> file.getFileName() + " gifts an unregistered item"));
        }
    }

    /**
     * The Niffler's numbers, as they were hard-coded in {@code NifflerEntity} before this pass.
     *
     * <p>This is the migration's safety net. The feed table, the milestones, the follow threshold and
     * the bestiary mastery mark all moved from Java into a datapack file, and the constraint on that
     * move was that the creature behaves identically. Anything that changes these has changed the
     * Niffler, which is a decision worth making on purpose.
     */
    @Test
    void theNifflerProfileRestatesItsOriginalHardCodedNumbers() throws IOException {
        BondProfile niffler = shipped("niffler");

        assertEquals(100, niffler.maxBond());
        assertEquals(50, niffler.followThreshold(), "the pouch and the follow goal both keyed off 50");
        assertEquals(List.of(20, 50, 80, 100), niffler.milestones());
        assertEquals(80, niffler.masteryBond().orElseThrow(),
                "reaching 80 marked the bestiary entry MASTERED");
        assertEquals(6.0, niffler.proximityRange(), 1e-6, "company counted within 6 blocks (36 sqr)");
        assertEquals(30, niffler.proximitySeconds(), "one bond per 30 seconds of company");
        assertEquals(1, niffler.proximityGain());
        assertEquals(0, niffler.betrayalPenalty(),
                "the Niffler never lost bond for being hit, and this pass does not change that");

        assertEquals(3, niffler.feeds().size());
        assertFeed(niffler, net.minecraft.world.item.Items.DIAMOND, 20, 120);
        assertFeed(niffler, net.minecraft.world.item.Items.GOLD_INGOT, 15, 60);
        assertFeed(niffler, net.minecraft.world.item.Items.GOLD_NUGGET, 5, 30);
    }

    /** The constant the debug command and the baby hand-over clamp against must match the profile. */
    @Test
    void theNifflerCeilingConstantMatchesItsProfile() throws IOException {
        assertEquals(at.koopro.wizardsandbeasts.entity.niffler.NifflerEntity.MAX_BOND,
                shipped("niffler").maxBond(),
                "NifflerEntity.MAX_BOND and creature_bonds/niffler.json disagree about the ceiling");
    }

    /**
     * A species that breeds must also accept its breeding item as a feed.
     *
     * <p>Entering the mood happens inside a successful feed, so a breeding item the profile does not
     * list as food is never reached: the interaction returns PASS before anything looks at breeding,
     * and the species reads as able to breed while being unable to.
     */
    @Test
    void everyBreedingItemIsAlsoAFeed() throws IOException {
        for (Path file : shippedProfiles()) {
            BondProfile profile = parseOrThrow(Files.readString(file));
            profile.breeding().ifPresent(breeding -> assertNotNull(
                    profile.feedFor(new net.minecraft.world.item.ItemStack(breeding.item())),
                    () -> file.getFileName() + " breeds on "
                            + BuiltInRegistries.ITEM.getKey(breeding.item())
                            + ", which is not in its feeds, so the pair could never enter the mood"));
        }
    }

    /** Both bred species must be able to be shrunk, or a juvenile is born full-size. */
    @Test
    void everyBreedingSpeciesDeclaresTheScaleAttribute() throws IOException {
        for (Path file : shippedProfiles()) {
            BondProfile profile = parseOrThrow(Files.readString(file));
            if (profile.breeding().isEmpty()) {
                continue;
            }
            String id = file.getFileName().toString().replace(".json", "");
            String entityClass = Character.toUpperCase(id.charAt(0))
                    + java.util.regex.Pattern.compile("_([a-z])").matcher(id.substring(1))
                            .replaceAll(m -> m.group(1).toUpperCase()) + "Entity.java";
            Path source = Path.of("src", "main", "java", "at", "koopro", "wizardsandbeasts",
                    "entity", "beast", entityClass);
            if (!Files.exists(source)) {
                continue; // data-driven creature; GenericBeastEntity's baseline covers it
            }
            assertTrue(Files.readString(source).contains("Attributes.SCALE"),
                    () -> entityClass + " breeds but never declares Attributes.SCALE, so "
                            + "getAttribute returns null and its juveniles are born full-size");
        }
    }

    private static void assertFeed(BondProfile profile, net.minecraft.world.item.Item item,
                                   int gain, int cooldownSeconds) {
        BondFeed feed = profile.feedFor(new net.minecraft.world.item.ItemStack(item));
        assertNotNull(feed, () -> "expected a feed entry for " + BuiltInRegistries.ITEM.getKey(item));
        assertEquals(gain, feed.gain());
        assertEquals(cooldownSeconds, feed.cooldownSeconds());
    }
}
