package at.koopro.wizardsandbeasts.bestiary;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every creature's page describes it as a living animal, and every claim a page makes about how a material is had is
 * true of the game: a page that says unicorn hair is shed must be describing a unicorn that sheds.
 */
class CreatureProfileDataTest {

    private static final Path DATA = Path.of("src", "main", "resources", "data", "wizards_and_beasts");
    private static final Path LANG = Path.of("src", "main", "resources", "assets", "wizards_and_beasts", "lang",
            "en_us.json");

    private static List<Path> entries() throws IOException {
        try (Stream<Path> files = Files.list(DATA.resolve("bestiary").resolve("entries"))) {
            return files.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
    }

    private static String id(Path entry) {
        return entry.getFileName().toString().replace(".json", "");
    }

    private static BestiaryEntry parse(Path file) throws IOException {
        return BestiaryEntry.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(file)))
                .getOrThrow(message -> new AssertionError(file + ": " + message));
    }

    @Test
    void everyEntryHasAProfileAndEveryProfileKeyIsTranslated() throws IOException {
        JsonObject lang = JsonParser.parseString(Files.readString(LANG)).getAsJsonObject();
        List<String> problems = new ArrayList<>();
        for (Path file : entries()) {
            BestiaryEntry entry = parse(file);
            if (entry.profile().isEmpty()) {
                problems.add(id(file) + ": no profile");
                continue;
            }
            CreatureProfile profile = entry.profile().get();
            List<String> keys = new ArrayList<>(List.of(profile.diet(), profile.behaviour(), profile.society()));
            keys.addAll(profile.interactions());
            profile.signature().ifPresent(keys::add);
            for (String key : keys) {
                if (!lang.has(key)) {
                    problems.add(id(file) + ": untranslated " + key);
                }
            }
            if (profile.interactions().isEmpty()) {
                problems.add(id(file) + ": no interaction rule");
            }
        }
        assertEquals(List.of(), problems);
    }

    @Test
    void everyClassificationAndBasisAndStudyActHasCopy() throws IOException {
        JsonObject lang = JsonParser.parseString(Files.readString(LANG)).getAsJsonObject();
        String prefix = "bestiary.wizards_and_beasts.";
        for (CreatureProfile.Classification c : CreatureProfile.Classification.values()) {
            assertTrue(lang.has(prefix + "classification." + c.getSerializedName()), c.name());
        }
        for (CreatureProfile.Basis b : CreatureProfile.Basis.values()) {
            assertTrue(lang.has(prefix + "basis." + b.getSerializedName()), b.name());
        }
        for (CreatureProfile.StudyAct a : CreatureProfile.StudyAct.values()) {
            assertTrue(lang.has(prefix + "study." + a.getSerializedName()), a.name());
        }
        for (CreatureProfile.Acquisition a : CreatureProfile.Acquisition.values()) {
            assertTrue(lang.has(prefix + "acquisition." + a.getSerializedName()), a.name());
        }
    }

    /** Canon's two species that declined Being status, and the creatures canon puts in no division. */
    @Test
    void theClassificationsCanonIsExplicitAboutAreKept() throws IOException {
        assertEquals(CreatureProfile.Classification.BEAST_BY_CHOICE, profile("centaur").classification());
        assertEquals(CreatureProfile.Classification.BEAST_BY_CHOICE, profile("merperson").classification());
        assertEquals(CreatureProfile.Classification.UNCLASSIFIED, profile("dementor").classification());
        assertEquals(CreatureProfile.Classification.UNCLASSIFIED, profile("boggart").classification());
        assertEquals(CreatureProfile.Classification.BEAST, profile("werewolf").classification());
    }

    /**
     * What a page says about where a material comes from is checked against the file that makes it so: remains
     * against the loot table, shedding and grooming against the creature's abilities, gifts and husbandry against its
     * bond profile, a studied kill against a harvest rule.
     */
    @Test
    void everyMaterialClaimIsTrueOfTheGame() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Path file : entries()) {
            String id = id(file);
            for (CreatureProfile.Material material : parse(file).profile().orElseThrow().materials()) {
                String item = '"' + material.item().toString() + '"';
                Path source = switch (material.how()) {
                    case REMAINS -> DATA.resolve(Path.of("loot_table", "entities", id + ".json"));
                    case SHED, GROOMING -> DATA.resolve(Path.of("creatures", id + ".json"));
                    case GIFT, HUSBANDRY -> DATA.resolve(Path.of("creature_bonds", id + ".json"));
                    case STUDIED_KILL -> null;
                };
                if (source == null) {
                    if (!harvestRuleExists(id, material.item().toString())) {
                        problems.add(id + ": claims a studied-kill harvest of " + item + " but no harvest rule gives it");
                    }
                    continue;
                }
                String text = Files.exists(source) ? Files.readString(source) : "";
                String type = switch (material.how()) {
                    case SHED -> "\"shed\"";
                    case GROOMING -> "\"groomable\"";
                    default -> "";
                };
                boolean javaSource = material.how() == CreatureProfile.Acquisition.SHED && id.equals("phoenix");
                if (!javaSource && (!text.contains(item) || !text.contains(type))) {
                    problems.add(id + ": claims " + material.item() + " by " + material.how().getSerializedName()
                            + " but " + source + " does not give it");
                }
            }
        }
        assertEquals(List.of(), problems);
    }

    /** A creature whose signature completes its page must be one of those whose code shows it. */
    @Test
    void signaturesAreOnlyClaimedWhereSomethingTriggersThem() throws IOException {
        List<String> triggered = List.of("bowtruckle", "demiguise", "mooncalf", "phoenix", "unicorn");
        for (Path file : entries()) {
            boolean claims = parse(file).profile().orElseThrow().hasSignature();
            assertEquals(triggered.contains(id(file)), claims,
                    id(file) + ": a signature with no trigger would leave the page forever short of KNOWN");
        }
    }

    private static CreatureProfile profile(String id) throws IOException {
        return parse(DATA.resolve(Path.of("bestiary", "entries", id + ".json"))).profile().orElseThrow();
    }

    private static boolean harvestRuleExists(String entryId, String item) throws IOException {
        try (Stream<Path> rules = Files.list(DATA.resolve(Path.of("bestiary", "harvest")))) {
            for (Path rule : rules.toList()) {
                String text = Files.readString(rule);
                if (text.contains("\"wizards_and_beasts:" + entryId + "\"") && text.contains('"' + item + '"')) {
                    return true;
                }
            }
        }
        return false;
    }
}
