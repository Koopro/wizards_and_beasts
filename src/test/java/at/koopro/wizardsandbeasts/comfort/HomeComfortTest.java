package at.koopro.wizardsandbeasts.comfort;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a slice of tart is worth.
 *
 * <p>{@link #shorteningIsIdempotentPerApplicationNotCompounding} is the one that matters. The
 * fear-shortening works by refusing an effect and re-applying a scaled copy, and the copy fires the
 * same event — so without the handler's re-entrancy guard every fear effect would collapse to one
 * tick instead of losing a third. That failure looks like a working comfort, only better, which is
 * exactly the kind nobody reports.
 */
class HomeComfortTest {

    private static final Path FEAR_TAG = Path.of("src", "main", "resources", "data",
            "wizards_and_beasts", "tags", "mob_effect", "fear_and_despair.json");

    @Test
    void theBriefsNumbersAreWhatShipped() {
        assertEquals(90, HomeComfort.DURATION_TICKS / 20);
        assertEquals(0.7f, HomeComfort.FEAR_SCALE, 1e-6f, "a 30% reduction");
        assertEquals(2.0f, HomeComfort.TOTAL_HEAL, 1e-6f, "one heart, in half-hearts");
    }

    @Test
    void theHealAddsUpToExactlyOneHeartOverTheDuration() {
        int beats = HomeComfort.DURATION_TICKS / HomeComfort.HEAL_INTERVAL;
        assertEquals(HomeComfort.TOTAL_HEAL, HomeComfort.HEAL_PER_BEAT * beats, 1e-4f,
                "the trickle must total one heart, not drift above or below it");
    }

    @Test
    void theHealIsSlowerThanSimplyEatingSomething() {
        // "Very weak regen" is the brief. If this ever out-healed food it would stop being comfort
        // food and start being a potion.
        float heartsPerSecond = HomeComfort.TOTAL_HEAL / (HomeComfort.DURATION_TICKS / 20.0f);
        assertTrue(heartsPerSecond < 0.1f,
                "healing " + heartsPerSecond + " half-hearts a second is not a weak regen");
    }

    @Test
    void shorteningTakesExactlyAThird() {
        assertEquals(70, HomeComfort.shorten(100));
        assertEquals(140, HomeComfort.shorten(200));
    }

    @Test
    void shorteningNeverReachesZero() {
        // A fear effect shortened out of existence is a cure, not a comfort — and that is the
        // Chocolate Frog's job.
        for (int base = 1; base <= 10; base++) {
            assertTrue(HomeComfort.shorten(base) >= 1,
                    "shorten(" + base + ") must leave at least a tick");
        }
        assertEquals(0, HomeComfort.shorten(0));
    }

    @Test
    void shorteningIsIdempotentPerApplicationNotCompounding() {
        // What the handler's re-entrancy guard is protecting. Applied twice, a 200-tick fright would
        // land at 98 rather than 140 — still "working", just wrong, and invisible in play.
        int once = HomeComfort.shorten(200);
        int twice = HomeComfort.shorten(once);
        assertTrue(twice < once,
                "compounding really does shrink it further, which is why the guard exists");
        assertEquals(140, once, "one application is the correct answer");
    }

    @Test
    void vanillaScalingAgreesWithOurArithmetic() {
        // The handler uses MobEffectInstance#withScaledDuration rather than shorten(); if the two
        // ever disagreed, the tooltip would be lying about the number.
        MobEffectInstance fright = new MobEffectInstance(MobEffects.BLINDNESS, 200, 0);
        assertEquals(HomeComfort.shorten(200),
                fright.withScaledDuration(HomeComfort.FEAR_SCALE).getDuration(),
                "vanilla's scaler and our tooltip must produce the same number");
    }

    @Test
    void theFearTagNamesTheDreadEffectsAndNotEveryAilment() throws IOException {
        assertTrue(Files.exists(FEAR_TAG), FEAR_TAG + " missing");
        try (Reader reader = Files.newBufferedReader(FEAR_TAG)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            assertTrue(json.has("replace") && !json.get("replace").getAsBoolean(),
                    "replace:true would drop anything a pack added");
            String values = json.getAsJsonArray("values").toString();
            assertTrue(values.contains("dementor_chill"), "a Dementor's chill is the point: " + values);
            assertTrue(values.contains("soul_drained"), values);
            // Comfort food should not be quietly shortening poison or a curse's damage.
            assertTrue(!values.contains("minecraft:poison"), "tart is not an antidote: " + values);
            assertTrue(!values.contains("minecraft:wither"), values);
        }
    }
}
