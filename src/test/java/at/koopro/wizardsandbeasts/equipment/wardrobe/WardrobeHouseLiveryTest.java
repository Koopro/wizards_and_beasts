package at.koopro.wizardsandbeasts.equipment.wardrobe;

import at.koopro.wizardsandbeasts.house.House;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A plain scarf is grey and white until the Sorting Hat has spoken, then wears the wearer's house (owner,
 * 2026-11-25). A scarf stitched in a house's colours by choice keeps them on anyone.
 */
class WardrobeHouseLiveryTest {

    private static final Path LIVERIES = Path.of("src", "main", "resources", "data", "wizards_and_beasts",
            "wizards_and_beasts", "livery");
    private static final Path SCARF_SHEETS = Path.of("src", "main", "resources", "assets", "wizards_and_beasts",
            "textures", "armor", "scarf");

    private static final Identifier PLAIN = id("school_plain");

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("wizards_and_beasts", path);
    }

    @Test
    void anUnsortedWearerKeepsThePlainScarf() {
        assertEquals(Optional.of(PLAIN), Wardrobe.wornLivery("scarf", PLAIN, Optional.empty(), null));
        // No sheet ships for the plain livery, so the renderer falls back to the cut's grey-and-white base.
        assertTrue(!Files.exists(SCARF_SHEETS.resolve(PLAIN.getPath() + ".png")),
                "a school_plain scarf sheet would replace the grey-and-white base the owner asked for");
    }

    @Test
    void aSortedWearerTurnsAPlainScarfTheirHouse() {
        for (House house : House.values()) {
            assertEquals(Optional.of(house.livery()), Wardrobe.wornLivery("scarf", PLAIN, Optional.empty(), house));
            // Stamping a cut's own default removes the component, but a stack stamped before that rule still reads plain.
            assertEquals(Optional.of(house.livery()), Wardrobe.wornLivery("scarf", PLAIN, Optional.of(PLAIN), house));
        }
    }

    @Test
    void aScarfStitchedInAHouseKeepsItOnAnyone() {
        Identifier lion = House.GRYFFINDOR.livery();
        assertEquals(Optional.of(lion), Wardrobe.wornLivery("scarf", PLAIN, Optional.of(lion), House.SLYTHERIN));
        assertEquals(Optional.of(lion), Wardrobe.wornLivery("scarf", PLAIN, Optional.of(lion), null));
    }

    @Test
    void onlyTheScarfFollowsTheHouse() {
        // Student robes share the plain livery but were not part of the ruling; they draw as stitched.
        assertEquals(Optional.of(PLAIN), Wardrobe.wornLivery("student_robe", PLAIN, Optional.empty(), House.RAVENCLAW));
    }

    @Test
    void everyHouseLiveryAdmitsTheScarfAndShipsItsSheet() throws IOException {
        for (House house : House.values()) {
            String livery = house.livery().getPath();
            Path json = LIVERIES.resolve(livery + ".json");
            assertTrue(Files.exists(json), house + ": no livery " + json);
            JsonArray cuts = JsonParser.parseString(Files.readString(json)).getAsJsonObject().getAsJsonArray("cuts");
            assertTrue(cuts.toString().contains("\"wizards_and_beasts:scarf\""),
                    house + ": livery " + livery + " does not admit the scarf");
            assertTrue(Files.exists(SCARF_SHEETS.resolve(livery + ".png")),
                    house + ": no scarf sheet for " + livery);
            assertEquals(Wardrobe.liveryTexture("scarf", PLAIN, Optional.of(house.livery())),
                    id("textures/armor/scarf/" + livery + ".png"));
        }
    }
}
