package at.koopro.wizardsandbeasts.broom;

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
 * What the feather is worth, and what it costs.
 *
 * <p>The stabiliser is the half worth pinning hardest: it writes into the same field
 * {@code BroomDefinition}'s codec range-checks, so a feather that pushed it past 0.5 would put a
 * broom into a state no authored definition can describe — and nothing in game would say so.
 */
class SnidgetFeatherTest {

    private static final Path FIREBOLT = Path.of("src", "main", "resources", "data",
            "wizards_and_beasts", "recipe", "firebolt.json");
    private static final Path SUPREME = Path.of("src", "main", "resources", "data",
            "wizards_and_beasts", "recipe", "firebolt_supreme.json");
    private static final Path NIFFLER_TAG = Path.of("src", "main", "resources", "data",
            "wizards_and_beasts", "tags", "item", "niffler_shiny.json");

    @Test
    void theBriefsEightPercentIsWhatShipped() {
        assertEquals(0.08f, SnidgetFeather.SPEED_BONUS, 1e-6f);
        assertEquals(108.0f, SnidgetFeather.applySpeedBonus(100.0f), 1e-3f);
    }

    @Test
    void theSpeedBonusIsAlwaysAGainAndScalesWithTheBroom() {
        // Multiplicative, so a server that halved broom speed gets a proportionate feather rather
        // than a flat bonus that quietly became enormous.
        for (float base : new float[] {0.5f, 1.15f, 2.625f}) {
            assertTrue(SnidgetFeather.applySpeedBonus(base) > base, "no gain at " + base);
        }
        assertTrue(SnidgetFeather.applySpeedBonus(2.0f) - 2.0f
                        > SnidgetFeather.applySpeedBonus(1.0f) - 1.0f,
                "a faster broom should gain more in absolute terms");
    }

    @Test
    void stabiliseNeverLeavesTheAuthoredCodecRange() {
        // BroomDefinition range-checks lerpFactor to [0.05, 0.5]. The feather must stay inside it.
        for (float authored = 0.05f; authored <= 0.5f; authored += 0.01f) {
            float stabilised = SnidgetFeather.stabilise(authored);
            assertTrue(stabilised >= authored,
                    "stabilising must not increase drift; " + authored + " -> " + stabilised);
            assertTrue(stabilised <= SnidgetFeather.MAX_LERP_FACTOR + 1e-5f,
                    "stabilised past the codec ceiling: " + stabilised);
        }
    }

    @Test
    void aTwitchyBroomGainsMoreFromTheFeatherThanATightOne() {
        // The feather is meant to make a broom easier to fly, not to make the best broom better.
        float looseGain = SnidgetFeather.stabilise(0.10f) - 0.10f;
        float tightGain = SnidgetFeather.stabilise(0.40f) - 0.40f;
        assertTrue(looseGain > tightGain,
                "a school broom should gain more than a Firebolt; got " + looseGain + " vs " + tightGain);
    }

    @Test
    void aBroomAtTheCeilingGainsNothingRatherThanBreaking() {
        assertEquals(SnidgetFeather.MAX_LERP_FACTOR,
                SnidgetFeather.stabilise(SnidgetFeather.MAX_LERP_FACTOR), 1e-5f);
    }

    @Test
    void aFeatherIsAboutTwentyMinutesAloft() {
        assertEquals(64, SnidgetFeather.DURABILITY, "the brief asked for component durability 64");
        int minutes = SnidgetFeather.DURABILITY * SnidgetFeather.TICKS_PER_DURABILITY / 20 / 60;
        assertTrue(minutes >= 10 && minutes <= 40,
                "\"consumes durability slowly\" should be tens of minutes; got " + minutes + " min");
    }

    @Test
    void wearHappensOnceEveryIntervalAndNeverOnTheFirstTick() {
        assertFalse(SnidgetFeather.wearsThisTick(0), "mounting must not cost a point");
        assertFalse(SnidgetFeather.wearsThisTick(SnidgetFeather.TICKS_PER_DURABILITY - 1));
        assertTrue(SnidgetFeather.wearsThisTick(SnidgetFeather.TICKS_PER_DURABILITY));
        assertTrue(SnidgetFeather.wearsThisTick(SnidgetFeather.TICKS_PER_DURABILITY * 3));

        int wears = 0;
        for (int tick = 1; tick <= SnidgetFeather.TICKS_PER_DURABILITY * 4; tick++) {
            if (SnidgetFeather.wearsThisTick(tick)) {
                wears++;
            }
        }
        assertEquals(4, wears, "exactly one point per interval");
    }

    @Test
    void bothFireboltTierBroomsAreTailedWithTheFeather() throws IOException {
        for (Path recipe : new Path[] {FIREBOLT, SUPREME}) {
            try (Reader reader = Files.newBufferedReader(recipe)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                assertEquals("wizards_and_beasts:golden_snidget_feather",
                        json.getAsJsonObject("key").get("T").getAsString(),
                        recipe + " should use the feather as its tail");
            }
        }
    }

    @Test
    void nifflersWantIt() throws IOException {
        try (Reader reader = Files.newBufferedReader(NIFFLER_TAG)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            boolean listed = false;
            for (var element : json.getAsJsonArray("values")) {
                listed |= "wizards_and_beasts:golden_snidget_feather".equals(element.getAsString());
            }
            assertTrue(listed, "a Niffler should be able to smell the shiniest thing in the mod");
        }
    }
}
