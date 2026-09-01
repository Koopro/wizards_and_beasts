package at.koopro.wizardsandbeasts.pumpkinjuice;

import at.koopro.wizardsandbeasts.butterbeer.Butterbeer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pumpkin juice's numbers, and the two things that keep it from being milk.
 *
 * <p>{@link #tenPercentOfASmallStudyAwardIsNotSilentlyZero} is the one that earns its keep: brew
 * points are ones and twos, and a naive {@code round(points * 1.1)} would make the advertised bonus
 * fire literally never on the exact activity the tooltip names.
 */
class PumpkinJuiceTest {

    private static final Path CANNOT_CLEAR = Path.of("src", "main", "resources", "data",
            "wizards_and_beasts", "tags", "mob_effect", "pumpkin_juice_cannot_clear.json");

    @Test
    void theBriefsValuesAreWhatShipped() {
        assertEquals(4, PumpkinJuice.NUTRITION);
        assertEquals(0.6f, PumpkinJuice.SATURATION, 1e-6f);
        assertEquals(60, PumpkinJuice.COMFORT_TICKS / 20);
        assertEquals(10, PumpkinJuice.TRAIL_TICKS / 20);
        assertEquals(15, PumpkinJuice.COOLDOWN_TICKS / 20);
        assertEquals(0.10f, HogwartsComfort.BONUS, 1e-6f);
    }

    @Test
    void theTrailIsShorterThanTheEffectItRidesOn() {
        // The client derives "am I trailing" from the effect's remaining duration, so a trail as long
        // as the buff would never switch off and one longer would never start.
        assertTrue(PumpkinJuice.TRAIL_TICKS < PumpkinJuice.COMFORT_TICKS,
                "the trail must be a prefix of the effect, not the whole of it");
    }

    @Test
    void onlyMinorHarmfulThingsAreClearable() {
        assertTrue(PumpkinJuice.isClearable(new MobEffectInstance(MobEffects.SLOWNESS, 100, 0)),
                "a stray Slowness is exactly what this is for");

        assertFalse(PumpkinJuice.isClearable(new MobEffectInstance(MobEffects.SLOWNESS, 100, 1)),
                "amplifier 1 is beyond a glass of juice");
        assertFalse(PumpkinJuice.isClearable(new MobEffectInstance(MobEffects.SPEED, 100, 0)),
                "clearing your own buffs would make this a trap");
    }

    @Test
    void noAmplifierAboveZeroIsEverClearable() {
        for (int amplifier = 1; amplifier <= 5; amplifier++) {
            assertFalse(PumpkinJuice.isClearable(new MobEffectInstance(MobEffects.WEAKNESS, 100, amplifier)),
                    "amplifier " + amplifier + " must be out of reach");
        }
    }

    @Test
    void combatExperienceGainsTheBonusEvenOnSmallDrops() {
        // Rounds up, so a one-point mob is visibly worth more rather than the bonus vanishing.
        assertTrue(HogwartsComfort.scaleCombat(1) > 1, "a small kill must still show the bonus");
        assertEquals(11, HogwartsComfort.scaleCombat(10));
        assertEquals(0, HogwartsComfort.scaleCombat(0), "nothing stays nothing");
        assertEquals(-1, HogwartsComfort.scaleCombat(-1), "a negative drop is left alone");
    }

    @Test
    void tenPercentOfASmallStudyAwardIsNotSilentlyZero() {
        // One brew point, ten percent: a rounding implementation pays 0 forever. This pays 1 on a
        // tenth of rolls instead.
        assertEquals(2, HogwartsComfort.scaleStudy(1, 0.0f), "a winning roll must actually pay");
        assertEquals(1, HogwartsComfort.scaleStudy(1, 0.99f), "a losing roll pays the base");
    }

    @Test
    void theStudyBonusAveragesOutToTheAdvertisedTenPercent() {
        int points = 3;
        int trials = 10_000;
        long total = 0;
        for (int i = 0; i < trials; i++) {
            total += HogwartsComfort.scaleStudy(points, i / (float) trials);
        }
        double average = total / (double) trials;
        double expected = points * (1.0 + HogwartsComfort.BONUS);
        assertEquals(expected, average, 0.02,
                "the fractional chance should average to the advertised bonus");
    }

    @Test
    void theStudyBonusNeverPaysLessThanTheBaseAward() {
        for (int points = 1; points <= 20; points++) {
            for (float roll : new float[] {0.0f, 0.5f, 0.999f}) {
                assertTrue(HogwartsComfort.scaleStudy(points, roll) >= points,
                        "a bonus must never subtract; " + points + " at roll " + roll);
            }
        }
        assertEquals(0, HogwartsComfort.scaleStudy(0, 0.0f));
    }

    @Test
    void poisonAndTheSeriousCursesAreOffLimits() throws IOException {
        assertTrue(Files.exists(CANNOT_CLEAR), CANNOT_CLEAR + " missing");
        try (Reader reader = Files.newBufferedReader(CANNOT_CLEAR)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            assertTrue(json.has("replace") && !json.get("replace").getAsBoolean(),
                    "replace:true would drop anything a pack protected");
            String values = json.getAsJsonArray("values").toString();
            assertTrue(values.contains("minecraft:poison"), "the brief says not poisons: " + values);
            assertTrue(values.contains("minecraft:wither"), values);
            assertTrue(values.contains("cruciatus_pain"), "the Unforgivables are not an ailment: " + values);
        }
    }

    @Test
    void theTwoDrinksStayDistinct() {
        // Same shelf, opposite moods. If these ever converged the player would stop caring which one
        // they packed, which is the one thing the brief was explicit about.
        assertTrue(PumpkinJuice.NUTRITION > Butterbeer.NUTRITION,
                "the school drink is the more filling of the two");
        assertTrue(PumpkinJuice.COOLDOWN_TICKS > 0,
                "pumpkin juice uses a real cooldown; Butterbeer uses an effect window");
    }
}
