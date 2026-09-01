package at.koopro.wizardsandbeasts.chocolate;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The frog's odds, and what chocolate is allowed to fix.
 *
 * <p>{@link #escapeStaysAWhimsyRatherThanATax} is the one worth keeping honest: fifteen percent is
 * low enough to read as a joke and high enough to actually happen, and it is exactly the sort of
 * number that gets "balanced" upward until eating chocolate becomes a coin flip.
 */
class ChocolateFrogTest {

    private static final Path CURES = Path.of("src", "main", "resources", "data",
            "wizards_and_beasts", "tags", "mob_effect", "chocolate_frog_cures.json");

    @Test
    void theBriefsValuesAreWhatShipped() {
        assertEquals(3, ChocolateFrog.NUTRITION, "still edible, unchanged");
        assertEquals(0.45f, ChocolateFrog.SATURATION, 1e-6f);
        assertEquals(30, ChocolateFrog.WARD_TICKS / 20);
        assertEquals(0.15f, ChocolateFrog.ESCAPE_CHANCE, 1e-6f);
        assertEquals(5, ChocolateFrog.ESCAPE_MIN_TICKS / 20);
        assertEquals(8, ChocolateFrog.ESCAPE_MAX_TICKS / 20);
    }

    @Test
    void escapeStaysAWhimsyRatherThanATax() {
        assertTrue(ChocolateFrog.ESCAPE_CHANCE > 0.0f && ChocolateFrog.ESCAPE_CHANCE <= 0.25f,
                "a frog that gets away more than a quarter of the time is a tax, not a joke");
        // Most of a stack should survive being eaten normally.
        assertTrue(Math.pow(1.0 - ChocolateFrog.ESCAPE_CHANCE, 4) > 0.4,
                "four frogs in a row should usually all be eaten");
    }

    @Test
    void escapeRollHonoursTheAdvertisedRate() {
        RandomSource random = RandomSource.create(20260827L);
        int escapes = 0;
        int trials = 20_000;
        for (int i = 0; i < trials; i++) {
            if (ChocolateFrog.escapes(random)) {
                escapes++;
            }
        }
        double rate = escapes / (double) trials;
        assertEquals(ChocolateFrog.ESCAPE_CHANCE, rate, 0.01,
                "observed escape rate " + rate + " should match the tooltip");
    }

    @Test
    void everyEscapeeLivesInsideTheBriefedWindow() {
        RandomSource random = RandomSource.create(1L);
        for (int i = 0; i < 2_000; i++) {
            int life = ChocolateFrog.escapeLifetime(random);
            assertTrue(life >= ChocolateFrog.ESCAPE_MIN_TICKS && life <= ChocolateFrog.ESCAPE_MAX_TICKS,
                    "lifetime " + life + " outside 5-8s");
        }
    }

    @Test
    void bothEndsOfTheLifetimeRangeAreReachable() {
        // An off-by-one in the bound would quietly make every frog live the minimum.
        RandomSource random = RandomSource.create(7L);
        boolean sawMin = false;
        boolean sawMax = false;
        for (int i = 0; i < 5_000; i++) {
            int life = ChocolateFrog.escapeLifetime(random);
            sawMin |= life == ChocolateFrog.ESCAPE_MIN_TICKS;
            sawMax |= life == ChocolateFrog.ESCAPE_MAX_TICKS;
        }
        assertTrue(sawMin, "the shortest hop should be reachable");
        assertTrue(sawMax, "the longest hop should be reachable");
    }

    @Test
    void theWardOutlastsTheThingItWardsAgainst() {
        // A ward shorter than a Dementor's aura reapplication window would let the chill straight
        // back in and make eating chocolate feel like it did nothing.
        assertTrue(ChocolateFrog.WARD_TICKS >= 200,
                "the ward must outlast a typical aura pulse; got " + ChocolateFrog.WARD_TICKS);
    }

    @Test
    void chocolateAnswersDespairAndTheDarkCreatureAilments() throws IOException {
        assertTrue(Files.exists(CURES), CURES + " missing");
        try (Reader reader = Files.newBufferedReader(CURES)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            assertTrue(json.has("replace") && !json.get("replace").getAsBoolean(),
                    "replace:true would drop anything a pack added");
            String values = json.getAsJsonArray("values").toString();
            assertTrue(values.contains("dementor_chill"), "the Dementor chill is the point: " + values);
            assertTrue(values.contains("soul_drained"), values);
            assertTrue(values.contains("minecraft:wither"), "dark creatures inflict Wither: " + values);
            assertTrue(values.contains("minecraft:mining_fatigue"), values);
        }
    }
}
