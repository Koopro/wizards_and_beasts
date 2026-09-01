package at.koopro.wizardsandbeasts.broom;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every broom must look like itself in the inventory, not merely have a file that could make it.
 *
 * <p>Written after the failure it checks for. Seven of the eight model files existed, were named
 * correctly, and sat next to seven distinct sprites — and every one of them inherited
 * {@code wizards_and_beasts:item/broom} while declaring no textures of its own, so all eight items
 * drew the generic broom's icon and the seven sprites were never loaded. Checking that the files
 * exist passes on that. Checking that each model names *its own* texture does not, and neither does
 * checking that the sprites differ byte for byte.
 */
class BroomItemModelTest {

    private static final Gson GSON = new Gson();
    private static final Path ASSETS = Path.of("src", "main", "resources", "assets", "wizards_and_beasts");
    private static final Path MODELS = ASSETS.resolve(Path.of("models", "item"));
    private static final Path TEXTURES = ASSETS.resolve(Path.of("textures", "item"));
    private static final Path REGISTRY = Path.of("src", "main", "java", "at", "koopro",
            "wizardsandbeasts", "registry", "BroomItemRegistry.java");

    /** The hold pose a shaft-like item wants: angled in hand rather than held flat. */
    private static final String HANDHELD = "minecraft:item/handheld";

    @Test
    void everyBroomModelNamesItsOwnTexture() throws IOException {
        for (String broom : registeredBrooms()) {
            Path model = MODELS.resolve(broom + ".json");
            assertTrue(Files.exists(model), broom + " has no item model at " + model);

            JsonObject json = GSON.fromJson(Files.readString(model), JsonObject.class);
            assertTrue(json.has("textures"),
                    model.getFileName() + " declares no textures, so it draws whatever its parent "
                            + "draws — which is how all eight brooms once shared one icon");

            String layer0 = json.getAsJsonObject("textures").get("layer0").getAsString();
            assertEquals("wizards_and_beasts:item/" + broom, layer0,
                    model.getFileName() + " points at another broom's sprite");

            assertTrue(Files.exists(TEXTURES.resolve(broom + ".png")),
                    broom + " names a sprite that does not exist");
        }
    }

    /** A shaft is held in the hand, not presented flat like a page. */
    @Test
    void everyBroomUsesTheHandheldPose() throws IOException {
        for (String broom : registeredBrooms()) {
            JsonObject json = GSON.fromJson(
                    Files.readString(MODELS.resolve(broom + ".json")), JsonObject.class);
            assertEquals(HANDHELD, json.get("parent").getAsString(),
                    broom + " does not use the handheld pose, so it is held flat in hand");
        }
    }

    /**
     * The sprites must differ by content, not merely by filename.
     *
     * <p>This is the assertion that actually holds the line. Eight files with eight names can still
     * be eight copies of one picture, and a broom roster whose whole point is that the tiers are
     * distinguishable fails silently on that.
     */
    @Test
    void everyBroomSpriteIsDistinct() throws IOException, NoSuchAlgorithmException {
        Map<String, String> byDigest = new HashMap<>();
        for (String broom : registeredBrooms()) {
            byte[] png = Files.readAllBytes(TEXTURES.resolve(broom + ".png"));
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(png));
            String clash = byDigest.put(digest, broom);
            assertNull(clash, broom + " and " + clash + " are the same picture. They are supposed to "
                    + "be told apart in a hotbar without reading the tooltip.");
        }
    }

    /** Names passed to {@code registerBroom}, so a new broom is covered without touching this test. */
    private static List<String> registeredBrooms() throws IOException {
        Matcher m = Pattern.compile("registerBroom\\(\"([a-z0-9_]+)\"")
                .matcher(Files.readString(REGISTRY));
        List<String> names = new java.util.ArrayList<>();
        while (m.find()) {
            names.add(m.group(1));
        }
        assertFalse(names.isEmpty(), "expected BroomItemRegistry to register brooms by name");
        return names;
    }
}
