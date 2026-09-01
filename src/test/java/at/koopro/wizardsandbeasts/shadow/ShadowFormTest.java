package at.koopro.wizardsandbeasts.shadow;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shadow Form's bargain, and the pool the essence leaves behind.
 *
 * <p>The light tax is the half worth pinning: it is a single multiplier that a well-meaning tweak
 * could invert, and there is no way to notice from in-game that it went the wrong way except by
 * being unexpectedly hard to kill.
 */
class ShadowFormTest {

    private static final Path DAMAGE_TYPE = Path.of("src", "main", "resources", "data",
            "wizards_and_beasts", "damage_type", "light_magic.json");
    private static final Path DAMAGE_TAG = Path.of("src", "main", "resources", "data",
            "wizards_and_beasts", "tags", "damage_type", "light_magic.json");
    private static final Path BREW = Path.of("src", "main", "resources", "data",
            "wizards_and_beasts", "brews", "shadow_form.json");
    private static final Path RECIPE = Path.of("src", "main", "resources", "data",
            "wizards_and_beasts", "brewing_recipes", "shadow_form.json");

    @Test
    void lightMagicHurtsExactlyTwentyPercentMore() {
        assertEquals(0.20f, ShadowForm.LIGHT_VULNERABILITY, 1e-6f);
        assertEquals(12.0f, ShadowForm.amplify(10.0f), 1e-4f);
    }

    @Test
    void theTaxIsAlwaysACostAndNeverAResistance() {
        for (float blow : new float[] {0.5f, 1.0f, 7.5f, 100.0f}) {
            assertTrue(ShadowForm.amplify(blow) > blow,
                    "amplify(" + blow + ") must hurt more, not less");
        }
        assertEquals(0.0f, ShadowForm.amplify(0.0f), 1e-6f, "nothing should stay nothing");
    }

    @Test
    void thePoolIsTheSizeAndLengthTheBriefAskedFor() {
        assertEquals(4.0, ShadowZones.RADIUS, 1e-6);
        assertEquals(12, ShadowZones.LIFETIME_TICKS / 20);
    }

    @Test
    void hidebehindsNoticeAPoolFromFurtherAwayThanTheyStalk() {
        // HidebehindStalkGoal ranges 16 blocks. If the pool did not out-reach that, throwing one
        // could never pull a stalker off you, which is the whole point of the attraction.
        assertTrue(ShadowZones.ATTRACTION_RADIUS > 16.0,
                "a pool must out-reach the stalk range; got " + ShadowZones.ATTRACTION_RADIUS);
        assertTrue(ShadowZones.ATTRACTION_RADIUS > ShadowZones.RADIUS,
                "a Hidebehind has to notice the pool before it is standing in it");
    }

    @Test
    void theDamageTypeAndItsTagBothExistAndAgree() throws IOException {
        assertTrue(Files.exists(DAMAGE_TYPE), DAMAGE_TYPE + " missing");
        try (Reader reader = Files.newBufferedReader(DAMAGE_TAG)) {
            JsonObject tag = JsonParser.parseReader(reader).getAsJsonObject();
            assertTrue(tag.has("replace") && !tag.get("replace").getAsBoolean(),
                    "replace:true would drop anything a pack added to what counts as light");
            boolean listed = false;
            for (var element : tag.getAsJsonArray("values")) {
                listed |= "wizards_and_beasts:light_magic".equals(element.getAsString());
            }
            assertTrue(listed, "the tag must contain the damage type this mod ships");
        }
    }

    @Test
    void theBrewIsMadeOfTheTwoThingsTheBriefNamed() throws IOException {
        try (Reader reader = Files.newBufferedReader(RECIPE)) {
            JsonObject recipe = JsonParser.parseReader(reader).getAsJsonObject();
            String ingredients = recipe.getAsJsonArray("ingredients").toString();
            assertTrue(ingredients.contains("wizards_and_beasts:hidebehind_shadow_essence"), ingredients);
            assertTrue(ingredients.contains("wizards_and_beasts:demiguise_hair"), ingredients);
            assertEquals("wizards_and_beasts:shadow_form",
                    recipe.get("outputBrewId").getAsString());
        }
    }

    @Test
    void theBrewIsShortLivedAndCarriesOnlyItsOwnEffect() throws IOException {
        try (Reader reader = Files.newBufferedReader(BREW)) {
            JsonObject brew = JsonParser.parseReader(reader).getAsJsonObject();
            var effects = brew.getAsJsonArray("effects");
            assertEquals(1, effects.size(),
                    "speed rides on the effect; a second entry here could be cleansed off separately");
            JsonObject only = effects.get(0).getAsJsonObject();
            assertEquals("wizards_and_beasts:shadow_form", only.get("id").getAsString());
            int seconds = only.get("duration").getAsInt() / 20;
            assertTrue(seconds > 0 && seconds <= 60,
                    "\"short-duration\" should mean under a minute; got " + seconds + "s");
        }
    }
}
