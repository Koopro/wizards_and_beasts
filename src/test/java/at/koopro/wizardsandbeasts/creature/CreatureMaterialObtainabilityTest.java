package at.koopro.wizardsandbeasts.creature;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every creature material this mod registers can be got hold of in survival.
 *
 * <p>Eleven could not. {@code acromantula_venom}, {@code basilisk_fang}, {@code demiguise_hair},
 * {@code murtlap_essence} and {@code occamy_eggshell} were registered, named, described and
 * unreachable; four of the eight wand cores were in the same state, which meant a third of the
 * wand catalogue advertised a core no player could hold. The failure is silent by construction —
 * an item with no source produces no error, no warning and no missing-texture square, so nothing
 * short of trying to build a wand with it ever finds out.
 *
 * <p>The check is deliberately textual and needs no Minecraft bootstrap: it scans every loot table,
 * harvest rule, recipe and brewing recipe for the item id, which is exactly the set of ways a mod
 * item can enter a player's inventory here. A material that appears in none of them is unobtainable.
 */
class CreatureMaterialObtainabilityTest {

    private static final String NS = "wizards_and_beasts:";

    private static final Path DATA = Path.of("src", "main", "resources", "data", "wizards_and_beasts");
    private static final Path LANG = Path.of("src", "main", "resources", "assets", "wizards_and_beasts",
            "lang", "en_us.json");

    /** Directories a mod item can be granted from. Recipes count: a craft is a way to get one. */
    private static final List<String> SOURCE_DIRECTORIES =
            List.of("loot_table", "recipe", "bestiary/harvest", "brewing_recipes", "ollivander_pool");

    /**
     * The creature materials, named rather than pattern-matched.
     *
     * <p>A regex over the item list drags in every {@code *_spawn_egg}, {@code brass_scales} and
     * {@code chocolate_bar} that happens to contain "scale" or "bar", and each exclusion then has to
     * be argued. This is the actual list: things a beast is the source of.
     */
    private static final Set<String> CREATURE_MATERIALS = new TreeSet<>(List.of(
            "acromantula_venom",
            "basilisk_fang",
            "demiguise_hair",
            "dragon_heartstring",
            "erumpent_horn",
            "ghoul_slime",
            "golden_snidget_feather",
            "granian_hair",
            "hidebehind_claw",
            "hidebehind_shadow_essence",
            "horned_serpent_gem",
            "matagot_essence",
            "mooncalf_dung",
            "murtlap_essence",
            "occamy_eggshell",
            "phoenix_feather",
            "pukwudgie_venom_sac",
            "rougarou_hair",
            "thestral_tail_hair",
            "thunderbird_tail_feather",
            "troll_whisker",
            "unicorn_hair",
            "veela_hair",
            "wampus_cat_hair",
            "white_river_monster_spine",
            "yeti_fur"));

    /** The eight wand cores. Losing a source for one of these dead-ends wandmaking, not just loot. */
    private static final Set<String> WAND_CORES = Set.of(
            "phoenix_feather", "dragon_heartstring", "unicorn_hair", "thestral_tail_hair",
            "veela_hair", "troll_whisker", "wampus_cat_hair", "thunderbird_tail_feather");

    @Test
    void everyCreatureMaterialHasASource() throws IOException {
        String sources = readAllSources();
        List<String> unobtainable = new ArrayList<>();
        for (String material : CREATURE_MATERIALS) {
            if (!sources.contains('"' + NS + material + '"')) {
                unobtainable.add(material);
            }
        }
        assertEquals(List.of(), unobtainable,
                "these creature materials are registered and cannot be obtained in survival: "
                        + unobtainable);
    }

    /**
     * The wand cores again, on their own, because the consequence differs.
     *
     * <p>An unobtainable slime is a gap. An unobtainable core is a wand the bench will offer, list in
     * JEI and never let anyone build — which reads as a broken feature rather than missing content.
     */
    @Test
    void everyWandCoreHasASource() throws IOException {
        String sources = readAllSources();
        List<String> unobtainable = WAND_CORES.stream()
                .filter(core -> !sources.contains('"' + NS + core + '"'))
                .sorted()
                .toList();
        assertEquals(List.of(), unobtainable,
                "wand cores with no source; the wandmaker's bench offers a wand nobody can build: "
                        + unobtainable);
    }

    /** Guard against the list above going stale as new beast materials are registered. */
    @Test
    void theMaterialListStillCoversEveryRegisteredBeastPart() throws IOException {
        String lang = Files.readString(LANG);
        Pattern key = Pattern.compile("\"item\\.wizards_and_beasts\\.([a-z0-9_]+)\"\\s*:");
        Pattern beastPart = Pattern.compile(
                "_venom$|_fang$|_hair$|_feather$|_claw$|_fur$|_horn$|_slime$|_gem$|_dung$|_whisker$"
                        + "|_essence$|_sac$|_eggshell$|_spine$|_heartstring$");

        List<String> missed = new ArrayList<>();
        Matcher matcher = key.matcher(lang);
        while (matcher.find()) {
            String id = matcher.group(1);
            if (beastPart.matcher(id).find() && !CREATURE_MATERIALS.contains(id)) {
                missed.add(id);
            }
        }
        assertEquals(List.of(), missed,
                "new beast materials are registered but not covered by this test's list; add them "
                        + "(and give them a source) rather than deleting this assertion: " + missed);
    }

    /**
     * Every entity loot table names a creature the mod registers.
     *
     * <p>A table filed under a name nothing spawns is dead weight that still reads as coverage in
     * every audit that counts files in the directory.
     */
    @Test
    void everyEntityLootTableBelongsToARegisteredCreature() throws IOException {
        String registrations = Files.readString(Path.of("src", "main", "java", "at", "koopro",
                "wizardsandbeasts", "registry", "ModCreatures.java"))
                + Files.readString(Path.of("src", "main", "java", "at", "koopro",
                "wizardsandbeasts", "registry", "ModEntities.java"));

        List<String> orphans = new ArrayList<>();
        try (Stream<Path> files = Files.list(DATA.resolve(Path.of("loot_table", "entities")))) {
            for (Path table : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String id = table.getFileName().toString().replace(".json", "");
                if (!registrations.contains('"' + id + '"')) {
                    orphans.add(id);
                }
            }
        }
        assertEquals(List.of(), orphans, "loot tables for creatures that are not registered: " + orphans);
    }

    /** Every alpha creature drops something, or is named here as deliberately dropping nothing. */
    @Test
    void everyAlphaCreatureHasALootTable() {
        Set<String> dropsNothingOnPurpose = Set.of("obscurus");
        List<String> missing = AlphaRoster.SHIPPED.stream()
                .filter(id -> !dropsNothingOnPurpose.contains(id))
                .filter(id -> !Files.exists(DATA.resolve(Path.of("loot_table", "entities", id + ".json"))))
                .sorted()
                .toList();
        assertEquals(List.of(), missing, "alpha creatures with no loot table: " + missing);
    }

    /** Concatenation of every file a mod item could be granted by. */
    private static String readAllSources() throws IOException {
        StringBuilder all = new StringBuilder();
        for (String directory : SOURCE_DIRECTORIES) {
            Path root = DATA;
            for (String part : directory.split("/")) {
                root = root.resolve(part);
            }
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    all.append(Files.readString(file)).append('\n');
                }
            }
        }
        // Generated recipes live outside src/main; a datagen'd craft is still a way to get an item.
        Path generated = Path.of("src", "generated", "resources", "data", "wizards_and_beasts", "recipe");
        if (Files.isDirectory(generated)) {
            try (Stream<Path> files = Files.walk(generated)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    all.append(Files.readString(file)).append('\n');
                }
            }
        }
        assertFalse(all.isEmpty(), "found no loot tables or recipes to check against");
        assertTrue(all.length() > 1000, "source scan looks suspiciously small; check the paths");
        return all.toString();
    }
}
