package at.koopro.wizardsandbeasts.wrackspurt;

import at.koopro.wizardsandbeasts.client.render.outline.EntityOutlines;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.ARGB;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the sight is worth, and what it is allowed to reveal.
 *
 * <p>{@link #theOutlineColourSurvivesBeingPacked} exists because {@code EntityOutlines} fails
 * <em>silently</em> on a colour with alpha 0 — the outline simply never appears, with no error and
 * nothing in the log. That is a bug you could stare at for an hour.
 */
class WrackspurtTest {

    private static final Path REVEALS = Path.of("src", "main", "resources", "data",
            "wizards_and_beasts", "tags", "mob_effect", "wrackspurt_reveals.json");

    @Test
    void theBriefsNumbersAreWhatShipped() {
        assertEquals(45, Wrackspurt.SIGHT_TICKS / 20);
        assertEquals(1, Wrackspurt.LEVITATION_TICKS / 20);
        assertEquals(5, Wrackspurt.CONFUSION_TICKS / 20);
        assertEquals(16.0, Wrackspurt.RANGE, 1e-6);
    }

    @Test
    void theOutlineColourSurvivesBeingPacked() {
        int packed = ARGB.opaque(Wrackspurt.OUTLINE_RGB);
        assertNotEquals(EntityOutlines.NO_OUTLINE, packed,
                "a colour that reads back as NO_OUTLINE draws nothing and says nothing");
        assertTrue((packed >>> 24) != 0, "the alpha byte has to be set or the outline never appears");
    }

    @Test
    void theBobIsAWhimsyNotAMovementAbility() {
        assertTrue(Wrackspurt.LEVITATION_TICKS <= 40,
                "levitation long enough to travel on would make this a mobility item");
        assertTrue(Wrackspurt.LEVITATION_TICKS > 0);
    }

    @Test
    void theSightAlwaysOutlastsTheMuddleItCosts() {
        // If the confusion ran longer than the sight, the plum would be a debuff you happen to see
        // through rather than a tool with an aftertaste.
        assertTrue(Wrackspurt.SIGHT_TICKS > Wrackspurt.CONFUSION_TICKS,
                "the payoff must outlast the price");
    }

    @Test
    void theRangeIsShortEnoughToStayAToolRatherThanARadar() {
        assertTrue(Wrackspurt.RANGE > 0 && Wrackspurt.RANGE <= 32.0,
                "sixteen blocks is noticing somebody; a chunk-wide sweep is a radar");
    }

    @Test
    void theRevealTagCoversInvisibilityAndTheModsOwnConcealments() throws IOException {
        assertTrue(Files.exists(REVEALS), REVEALS + " missing");
        try (Reader reader = Files.newBufferedReader(REVEALS)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            assertTrue(json.has("replace") && !json.get("replace").getAsBoolean(),
                    "replace:true would drop anything a pack wanted revealed");
            String values = json.getAsJsonArray("values").toString();
            assertTrue(values.contains("minecraft:invisibility"), values);
            assertTrue(values.contains("camouflage"),
                    "the Demiguise trick is exactly what this should catch: " + values);
            assertTrue(values.contains("shadow_form"), values);
        }
    }
}
