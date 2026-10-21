package at.koopro.wizardsandbeasts.bestiary.journal;

import at.koopro.wizardsandbeasts.bestiary.BestiaryEntry;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped journal data: the layout file is the code's default, every entry's art override exists, and the five
 * showcase entries carry the whole illustration set (documentation/BESTIARY_ILLUSTRATION_SPEC.md).
 */
class JournalDataTest {

    private static final Path DATA = Path.of("src/main/resources/data/wizards_and_beasts");
    private static final Path ASSETS = Path.of("src/main/resources/assets");
    private static final Path LANG = ASSETS.resolve("wizards_and_beasts/lang/en_us.json");
    static final List<String> SHOWCASE = List.of("niffler", "bowtruckle", "unicorn", "mooncalf", "hippogriff");

    @Test
    void theShippedLayoutIsTheDefault() throws IOException {
        JournalLayout shipped = JournalLayout.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString(Files.readString(DATA.resolve("bestiary_journal/layout.json")))).getOrThrow();
        assertEquals(JournalLayout.DEFAULT, shipped, "layout.json and JournalLayout.DEFAULT disagree");
    }

    @Test
    void aBrokenLayoutIsRefused() {
        String twice = "{\"sections\":[{\"section\":\"size\",\"page\":\"plate\",\"reveal\":\"ENCOUNTERED\"},"
                + "{\"section\":\"size\",\"page\":\"notes\",\"reveal\":\"OBSERVED\"}]}";
        assertTrue(JournalLayout.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(twice)).isError());
        String unknown = "{\"sections\":[{\"section\":\"lore\",\"page\":\"notes\",\"reveal\":\"UNKNOWN\"}]}";
        assertTrue(JournalLayout.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(unknown)).isError());
        String late = "{\"sections\":[{\"section\":\"lore\",\"page\":\"notes\",\"reveal\":\"OBSERVED\",\"partial\":\"KNOWN\"}]}";
        assertTrue(JournalLayout.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(late)).isError());
    }

    @Test
    void everyArtOverrideAndCaptionResolves() throws IOException {
        JsonObject lang = JsonParser.parseString(Files.readString(LANG)).getAsJsonObject();
        try (var files = Files.list(DATA.resolve("bestiary/entries"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                BestiaryEntry entry = BestiaryEntry.CODEC.parse(JsonOps.INSTANCE,
                        JsonParser.parseString(Files.readString(file))).getOrThrow();
                for (Identifier texture : entry.journal().art().values()) {
                    assertTrue(Files.exists(asset(texture)), file.getFileName() + " names missing art " + texture);
                }
                entry.journal().signatureCaption().ifPresent(key ->
                        assertTrue(lang.has(key), file.getFileName() + " caption " + key + " has no text"));
            }
        }
    }

    @Test
    void theShowcaseEntriesAreComplete() throws IOException {
        JsonObject lang = JsonParser.parseString(Files.readString(LANG)).getAsJsonObject();
        for (String id : SHOWCASE) {
            BestiaryEntry entry = BestiaryEntry.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                    Files.readString(DATA.resolve("bestiary/entries/" + id + ".json")))).getOrThrow();
            assertTrue(entry.profile().isPresent() && entry.profile().get().hasSignature(), id + " has no signature");
            assertTrue(lang.has(entry.profile().get().signature().get()), id + " signature has no text");
            assertTrue(entry.journal().signatureCaption().isPresent(), id + " has no signature caption");
            for (IllustrationStage stage : IllustrationStage.values()) {
                Path png = asset(entry.journal().textureFor(stage, entry.id()));
                assertTrue(Files.exists(png), id + " is missing its " + stage.getSerializedName() + " plate");
                BufferedImage image = ImageIO.read(png.toFile());
                assertNotNull(image, png.toString());
                assertEquals(96, image.getWidth(), png + " is not 96 wide (spec §2)");
                assertEquals(96, image.getHeight(), png + " is not 96 high (spec §2)");
                assertTrue(image.getColorModel().hasAlpha(), png + " has no transparency (spec §2)");
            }
        }
    }

    private static Path asset(Identifier texture) {
        return ASSETS.resolve(texture.getNamespace()).resolve(texture.getPath());
    }
}
