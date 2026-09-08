package at.koopro.wizardsandbeasts.brew;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
 * Every shipped brew is named and described, and names itself the way {@link BrewNaming} assumes.
 *
 * <p>This is the test that would have caught the original hole. Fourteen brews each carried a
 * {@code displayName} key and a colour; the bottle item read neither, so every potion in the game was
 * called "Brew" and looked identical. Nothing failed, because nothing checked that the data was
 * <em>used</em> — only that it parsed.
 *
 * <p>It also pins the convention {@link BrewNaming} depends on. The derivation is safe precisely
 * because all fourteen definitions agree on {@code brew.<namespace>.<path>.name}; a fifteenth that
 * quietly picked its own key would make the bottle and the cauldron disagree, and this fails the build
 * instead of letting that ship.
 */
class BrewLangParityTest {

    private static final Path BREWS = Path.of("src/main/resources/data/wizards_and_beasts/brews");
    private static final Path LANG =
            Path.of("src/main/resources/assets/wizards_and_beasts/lang/en_us.json");

    private static JsonObject lang() throws IOException {
        return JsonParser.parseString(Files.readString(LANG)).getAsJsonObject();
    }

    private static List<String> brewIds() throws IOException {
        try (Stream<Path> files = Files.list(BREWS)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(path -> path.getFileName().toString().replace(".json", ""))
                    .sorted()
                    .toList();
        }
    }

    private static JsonObject definition(String brewId) throws IOException {
        return JsonParser.parseString(Files.readString(BREWS.resolve(brewId + ".json")))
                .getAsJsonObject();
    }

    @Test
    void thereAreBrewsToCheck() throws IOException {
        // A glob that silently matches nothing would make every assertion below vacuously pass.
        assertTrue(brewIds().size() >= 14, "expected the shipped brew catalogue, found " + brewIds());
    }

    @Test
    void everyBrewNamesItselfByTheDerivedKey() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (String brewId : brewIds()) {
            String declared = definition(brewId).get("displayName").getAsString();
            String derived = BrewNaming.nameKey("wizards_and_beasts:" + brewId);
            if (!derived.equals(declared)) {
                wrong.add(brewId + ": declares '" + declared + "', derivation gives '" + derived + "'");
            }
        }
        assertEquals(List.of(), wrong,
                "a brew that names itself off-convention gets a generic bottle; see BrewNaming");
    }

    @Test
    void everyBrewHasANameInTheLangFile() throws IOException {
        JsonObject lang = lang();
        List<String> missing = new ArrayList<>();
        for (String brewId : brewIds()) {
            String key = BrewNaming.nameKey("wizards_and_beasts:" + brewId);
            if (!lang.has(key)) {
                missing.add(key);
            }
        }
        assertEquals(List.of(), missing, "brews with no name key");
    }

    @Test
    void everyBrewWithFlavourTextHasADescriptionKey() throws IOException {
        // flavorText is a raw string baked into the datapack, so it cannot be translated. The tooltip
        // reads the key instead; this keeps the two from drifting apart as brews are added.
        JsonObject lang = lang();
        List<String> missing = new ArrayList<>();
        for (String brewId : brewIds()) {
            if (!definition(brewId).has("flavorText")) {
                continue;
            }
            String key = BrewNaming.descKey("wizards_and_beasts:" + brewId);
            if (!lang.has(key)) {
                missing.add(key);
            }
        }
        assertEquals(List.of(), missing, "brews with flavour text but no description key");
    }

    @Test
    void everyBrewDeclaresAColourForTheBottleToWear() throws IOException {
        // The bottle tint reads Brew.color(). A brew without one renders in the fallback purple and is
        // indistinguishable from every other colourless brew.
        List<String> missing = new ArrayList<>();
        for (String brewId : brewIds()) {
            if (!definition(brewId).has("color")) {
                missing.add(brewId);
            }
        }
        assertEquals(List.of(), missing, "brews with no colour");
    }
}
