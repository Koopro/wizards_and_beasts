package at.koopro.wizardsandbeasts.butterbeer;

import at.koopro.wizardsandbeasts.Config;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Butterbeer's numbers, and the two promises the item makes about tone.
 *
 * <p>{@link #theAntiSpamWindowOutlastsEveryEffectItGrants} is the one that earns its keep: if the
 * window ever slipped under Warmth's ninety seconds, a patient player could chain Warmth for ever by
 * waiting for it to lapse, and nothing in game would look wrong while they did it.
 */
class ButterbeerTest {

    private static final Path SOURCE_TAG = Path.of("src", "main", "resources", "data",
            "wizards_and_beasts", "tags", "block", "butterbeer_source.json");

    @Test
    void theBriefsFoodValuesAreWhatShipped() {
        assertEquals(2, Butterbeer.NUTRITION);
        assertEquals(0.4f, Butterbeer.SATURATION, 1e-6f);
    }

    @Test
    void theBriefsDurationsAreWhatShipped() {
        assertEquals(90, Butterbeer.WARMTH_TICKS / 20);
        assertEquals(160, Butterbeer.REGENERATION_TICKS, "the existing Regeneration I was to be kept");
        assertEquals(45, Butterbeer.MELLOW_TICKS / 20);
        assertEquals(120, Butterbeer.EFFECT_WINDOW_TICKS / 20);
        assertEquals(30, Butterbeer.GULP_WINDOW_TICKS / 20);
    }

    @Test
    void theAntiSpamWindowOutlastsEveryEffectItGrants() {
        // Otherwise a patient drinker chains the longest effect indefinitely by waiting it out.
        int longest = Math.max(Butterbeer.WARMTH_TICKS,
                Math.max(Butterbeer.MELLOW_TICKS, Butterbeer.REGENERATION_TICKS));
        assertTrue(Butterbeer.EFFECT_WINDOW_TICKS > longest,
                "window " + Butterbeer.EFFECT_WINDOW_TICKS + " must outlast the longest effect " + longest);
    }

    @Test
    void theGulpWindowSitsInsideTheEffectWindow() {
        // A gulp is a subset of "too soon": you cannot be gulping and yet be due a full pour.
        assertTrue(Butterbeer.GULP_WINDOW_TICKS < Butterbeer.EFFECT_WINDOW_TICKS,
                "a fast second mug must always also be a refill-only mug");
    }

    @Test
    void theQueasinessIsOffUnlessAServerAsksForIt() {
        // The item has to be usable on a server full of children by default.
        assertFalse(Config.butterbeerGulpNausea,
                "butterbeerGulpNausea must default to false");
    }

    @Test
    void theQueasinessIsBriefEvenWhenSwitchedOn() {
        assertTrue(Butterbeer.GULP_NAUSEA_TICKS <= 200,
                "five seconds of wooziness is a nudge; anything longer is a punishment");
        assertTrue(Butterbeer.GULP_NAUSEA_TICKS > 0);
    }

    @Test
    void aMugCanActuallyBeRefilledSomewhere() throws IOException {
        // An empty tag would make the mug a souvenir rather than a container.
        assertTrue(Files.exists(SOURCE_TAG), SOURCE_TAG + " missing");
        try (Reader reader = Files.newBufferedReader(SOURCE_TAG)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            assertTrue(json.has("replace") && !json.get("replace").getAsBoolean(),
                    "replace:true would drop any tap a pack added");
            assertTrue(json.getAsJsonArray("values").size() > 0,
                    "ship at least one block that fills a mug");
        }
    }

    @Test
    void brewingCauldronsAreNotAlsoTaps() throws IOException {
        // The copper and pewter cauldrons are a brewer's kit; a tavern tap is the brass one.
        try (Reader reader = Files.newBufferedReader(SOURCE_TAG)) {
            String values = JsonParser.parseReader(reader).getAsJsonObject()
                    .getAsJsonArray("values").toString();
            assertFalse(values.contains("wizarding_copper_cauldron"), values);
            assertFalse(values.contains("pewter_cauldron"), values);
        }
    }
}
