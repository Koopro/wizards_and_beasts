package at.koopro.wizardsandbeasts.dittany;

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
 * What a dose is worth, and the seam the rewrite must not break.
 *
 * <p>{@link #splinchingIsStillCuredByTheItemTagNotTheEffectTag} is the one that earns its keep.
 * Dittany's splinch cure lives in a completely different place — {@code SplinchDamageHandler} listens
 * for the use-item finish event against the {@code cures_splinch} <em>item</em> tag — so a rewrite
 * that turned self-application into an instant click would have silently deleted the one thing
 * Dittany is actually famous for, with nothing failing.
 */
class DittanyTest {

    private static final Path MENDS_TAG = Path.of("src", "main", "resources", "data",
            "wizards_and_beasts", "tags", "mob_effect", "dittany_mends.json");
    private static final Path SPLINCH_TAG = Path.of("src", "generated", "resources", "data",
            "wizards_and_beasts", "tags", "item", "cures_splinch.json");

    @Test
    void theBriefsNumbersAreWhatShipped() {
        assertEquals(4.0f, Dittany.BASE_HEAL, 1e-6f);
        assertEquals(2.0f, Dittany.WOUND_BONUS, 1e-6f);
        assertTrue(Dittany.COOLDOWN_TICKS / 20 >= 8 && Dittany.COOLDOWN_TICKS / 20 <= 10,
                "the brief asks for 8-10s; got " + Dittany.COOLDOWN_TICKS / 20 + "s");
    }

    @Test
    void aWoundIsWorthMoreThanABruise() {
        assertTrue(Dittany.WOUND_BONUS > 0.0f,
                "the bonus is the whole reason this stopped being a flat heal");
        assertEquals(Dittany.BASE_HEAL + Dittany.WOUND_BONUS, 6.0f, 1e-6f,
                "a wounded patient should get the old flat six, and an unhurt one less");
    }

    @Test
    void itIsWeakerThanTheFlatHealItReplaced() {
        // The old item gave 6 unconditionally on a 5s cooldown. If the new one matched that on an
        // ordinary target it would be a straight buff rather than a redesign.
        assertTrue(Dittany.BASE_HEAL < 6.0f, "the ordinary case must be weaker than the old flat six");
        assertTrue(Dittany.COOLDOWN_TICKS > 100, "and the cooldown longer than the old five seconds");
    }

    @Test
    void theMendTagIsWoundsRatherThanEveryDebuff() throws IOException {
        assertTrue(Files.exists(MENDS_TAG), MENDS_TAG + " missing");
        try (Reader reader = Files.newBufferedReader(MENDS_TAG)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            assertTrue(json.has("replace") && !json.get("replace").getAsBoolean(),
                    "replace:true would drop anything a pack added");
            String values = json.getAsJsonArray("values").toString();
            assertTrue(values.contains("sectumsempra_bleed"),
                    "the bleeding curse is the obvious case: " + values);
            // Dittany is for injuries, not a cure-all. A tag full of debuffs would make it one.
            assertFalse(values.contains("minecraft:poison"), values);
            assertFalse(values.contains("minecraft:wither"), values);
            assertFalse(values.contains("dementor_chill"),
                    "despair is chocolate's job, not Dittany's: " + values);
        }
    }

    @Test
    void splinchingIsStillCuredByTheItemTagNotTheEffectTag() throws IOException {
        // Two separate mechanisms, and they must stay separate: SplinchDamageHandler keys off the
        // item tag and the use-item finish event, which is why self-application is still a
        // use-over-time rather than an instant click.
        assertTrue(Files.exists(SPLINCH_TAG), SPLINCH_TAG + " missing");
        try (Reader reader = Files.newBufferedReader(SPLINCH_TAG)) {
            String values = JsonParser.parseReader(reader).getAsJsonObject()
                    .getAsJsonArray("values").toString();
            assertTrue(values.contains("wizards_and_beasts:dittany"),
                    "Dittany must stay the splinch cure: " + values);
        }
        try (Reader reader = Files.newBufferedReader(MENDS_TAG)) {
            String values = JsonParser.parseReader(reader).getAsJsonObject()
                    .getAsJsonArray("values").toString();
            assertFalse(values.contains("splinched"),
                    "splinching is cured through the item tag; duplicating it here would double-cure");
        }
    }
}
