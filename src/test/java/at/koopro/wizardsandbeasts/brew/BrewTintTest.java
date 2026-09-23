package at.koopro.wizardsandbeasts.brew;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three pieces that make one bottle model wear fourteen colours, and the fact that they agree.
 *
 * <p>They are in three different languages and three different places — a Python art generator writes
 * the {@code tintindex}, a hand-written JSON declares the tint source, and a Java record computes the
 * colour — so nothing but a test can hold them together. Break any one and the failure is silent: the
 * bottle simply renders grey, or renders purple, and no log line says why.
 */
class BrewTintTest {

    private static final Path MODEL =
            Path.of("src/main/resources/assets/wizards_and_beasts/models/item/brew.json");
    private static final Path DEFINITION =
            Path.of("src/main/resources/assets/wizards_and_beasts/items/brew.json");
    private static final Path INVENTORY_MODEL =
            Path.of("src/main/resources/assets/wizards_and_beasts/models/item/brew_inventory.json");

    /** Must match {@code BrewTintSource.ID}; hard-coded so a rename cannot quietly satisfy both sides. */
    private static final String TINT_TYPE = "wizards_and_beasts:brew";

    private static JsonObject read(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    @Test
    void exactlyOneElementIsTintable() throws IOException {
        // One box, the liquid. If the glass or the cork were tintable too, a dark brew would come with
        // a black cork and the bottle would stop reading as glass.
        JsonArray elements = read(MODEL).getAsJsonArray("elements");
        long tinted = 0;
        for (var element : elements) {
            JsonObject faces = element.getAsJsonObject().getAsJsonObject("faces");
            boolean any = faces.entrySet().stream()
                    .anyMatch(face -> face.getValue().getAsJsonObject().has("tintindex"));
            if (any) {
                tinted++;
            }
        }
        assertEquals(1, tinted, "expected exactly one tintable element in the brew model");
    }

    @Test
    void everyFaceOfTheTintedElementIsTinted() throws IOException {
        // A partly-tinted box shows its untinted faces in the raw greyscale the generator painted,
        // which reads as a white patch on one side of the bottle.
        JsonArray elements = read(MODEL).getAsJsonArray("elements");
        for (var element : elements) {
            JsonObject faces = element.getAsJsonObject().getAsJsonObject("faces");
            long tinted = faces.entrySet().stream()
                    .filter(face -> face.getValue().getAsJsonObject().has("tintindex"))
                    .count();
            assertTrue(tinted == 0 || tinted == faces.size(),
                    "element is tinted on " + tinted + " of " + faces.size() + " faces");
        }
    }

    @Test
    void theTintIndexIsZeroBecauseThatIsTheOnlyTintDeclared() throws IOException {
        JsonArray elements = read(MODEL).getAsJsonArray("elements");
        for (var element : elements) {
            JsonObject faces = element.getAsJsonObject().getAsJsonObject("faces");
            for (var face : faces.entrySet()) {
                JsonObject value = face.getValue().getAsJsonObject();
                if (value.has("tintindex")) {
                    assertEquals(0, value.get("tintindex").getAsInt(),
                            "tintindex must index into the tints array, which has one entry");
                }
            }
        }
    }

    /**
     * The definition is a slot/hand split: the flat icon where the bottle is looked at, the tinted
     * cuboid in hand. Both branches carry the brew tint, or one of them renders every brew purple.
     */
    private static JsonObject branch(boolean inSlot) throws IOException {
        JsonObject model = read(DEFINITION).getAsJsonObject("model");
        assertEquals("minecraft:select", model.get("type").getAsString());
        assertEquals("minecraft:display_context", model.get("property").getAsString());
        return inSlot
                ? model.getAsJsonArray("cases").get(0).getAsJsonObject().getAsJsonObject("model")
                : model.getAsJsonObject("fallback");
    }

    private static void assertBrewTint(JsonObject tint) {
        assertEquals(TINT_TYPE, tint.get("type").getAsString());
        assertTrue(tint.has("default"), "the source needs a fallback for an unbottled or unsynced stack");
        // RGB_COLOR_CODEC takes a packed RGB, not ARGB. A value with an alpha byte set would be
        // rejected at load and the whole item definition would fail to parse.
        int fallback = tint.get("default").getAsInt();
        assertTrue(fallback >= 0 && fallback <= 0xFFFFFF,
                "fallback must fit in 24 bits, got " + Integer.toHexString(fallback));
    }

    @Test
    void theHandBranchTintsTheCuboid() throws IOException {
        JsonObject hand = branch(false);
        assertEquals("wizards_and_beasts:item/brew", hand.get("model").getAsString());
        JsonArray tints = hand.getAsJsonArray("tints");
        assertEquals(1, tints.size(), "one tint entry, matching the cuboid's tintindex 0");
        assertBrewTint(tints.get(0).getAsJsonObject());
    }

    @Test
    void theSlotBranchTintsOnlyTheLiquidLayer() throws IOException {
        // The icon is glass on layer0 and a greyscale liquid mask on layer1. The brew tint has to sit
        // at index 1, and index 0 must be white, or the glass and cork take the brew's colour too.
        JsonObject slot = branch(true);
        assertEquals("wizards_and_beasts:item/brew_inventory", slot.get("model").getAsString());
        JsonArray tints = slot.getAsJsonArray("tints");
        assertEquals(2, tints.size(), "one tint per icon layer");
        JsonObject glass = tints.get(0).getAsJsonObject();
        assertEquals("minecraft:constant", glass.get("type").getAsString());
        assertEquals(-1, glass.get("value").getAsInt(), "layer0 (glass) stays untinted");
        assertBrewTint(tints.get(1).getAsJsonObject());

        JsonObject textures = read(INVENTORY_MODEL).getAsJsonObject("textures");
        assertEquals("wizards_and_beasts:item/brew", textures.get("layer0").getAsString());
        assertEquals("wizards_and_beasts:item/brew_liquid", textures.get("layer1").getAsString());
    }

    @Test
    void theGeneratedDefinitionDoesNotWinTheMerge() {
        // src/main overrides src/generated at processResources. This asserts the hand-written file is
        // where it needs to be for that to happen — if it ever moved, the generated tint-less copy
        // would take over and every bottle would silently go back to being purple.
        assertTrue(Files.exists(DEFINITION), "the hand-written definition must live under src/main");
        assertFalse(Files.notExists(MODEL), "the tinted model must live under src/main too");
    }
}
