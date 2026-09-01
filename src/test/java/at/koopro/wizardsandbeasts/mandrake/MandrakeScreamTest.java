package at.koopro.wizardsandbeasts.mandrake;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shape of the scream, and the shape of the way out of it.
 *
 * <p>The AoE itself needs a level and is not reachable here; what is reachable is the relationship
 * between the two cries and the contents of the tag that spares people — and the tag is the half
 * most likely to be silently broken, because an item renamed out from under it fails as a no-op
 * rather than as an error.
 */
class MandrakeScreamTest {

    private static final Path MUFFLES_TAG = Path.of("src", "main", "resources", "data",
            "wizards_and_beasts", "tags", "item", "muffles_screams.json");

    @Test
    void theBriefsEightBlockRadiusIsWhatShipped() {
        assertEquals(8.0, MandrakeScream.ADULT_RADIUS, 0.0001);
    }

    @Test
    void aSeedlingIsStrictlyLessDangerousThanAGrownOne() {
        // A thrown Baby Mandrake that matched the harvest would make the harvest pointless: you would
        // simply throw one instead of ever pulling one deliberately.
        assertTrue(MandrakeScream.BABY_RADIUS < MandrakeScream.ADULT_RADIUS,
                "a baby must not out-reach a grown Mandrake");
        assertTrue(MandrakeScream.BABY_EFFECT_TICKS < MandrakeScream.ADULT_EFFECT_TICKS,
                "a baby must not out-last a grown Mandrake");
    }

    @Test
    void bothCriesActuallyDoSomething() {
        assertTrue(MandrakeScream.BABY_RADIUS > 0.0);
        assertTrue(MandrakeScream.BABY_EFFECT_TICKS > 0);
        assertTrue(MandrakeScream.ADULT_EFFECT_TICKS >= 20,
                "an effect shorter than a second would not register as having happened");
    }

    @Test
    void theProtectionTagNamesTheItemsTheBriefAskedFor() throws IOException {
        List<String> values = tagValues();
        assertTrue(values.contains("wizards_and_beasts:earmuffs"),
                "earmuffs are the canonical protection; got " + values);
        assertTrue(values.contains("wizards_and_beasts:blindfold"),
                "the brief lists the Blindfold too; got " + values);
    }

    @Test
    void theProtectionTagIsAdditiveSoPacksCanExtendIt() throws IOException {
        try (Reader reader = Files.newBufferedReader(MUFFLES_TAG)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            assertTrue(json.has("replace") && !json.get("replace").getAsBoolean(),
                    "replace:true would silently drop anything another pack added");
        }
    }

    private static List<String> tagValues() throws IOException {
        assertTrue(Files.exists(MUFFLES_TAG), MUFFLES_TAG + " missing");
        List<String> values = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(MUFFLES_TAG)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            json.getAsJsonArray("values").forEach(e -> values.add(e.getAsString()));
        }
        return values;
    }
}
