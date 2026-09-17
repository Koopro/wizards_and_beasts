package at.koopro.wizardsandbeasts.animagus;

import at.koopro.wizardsandbeasts.ability.AnimagusForms;
import at.koopro.wizardsandbeasts.form.SizeProfile;
import at.koopro.wizardsandbeasts.form.SizeProfileRegistry;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.entity.EntityDimensions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Binds the two places that describe how tall an Animagus is.
 *
 * <p>The mod carries two Animagus vocabularies. {@code SizeProfileRegistry} is the one the game
 * actually collides and looks through — {@code PlayerBoxOverrides} reads it, on both sides, for
 * every form. The {@code animagus_forms/*.json} definitions are the data-driven half, and their
 * {@code hitbox.eye_height} is the number a designer edits. Nothing enforced that the two agreed,
 * and {@code AnimagusHitbox.eyeHeight} had no reader at all: the field was loaded, validated,
 * synced to every client, and consulted by nothing.
 *
 * <p>Rather than have the runtime consult a datapack that only covers two of the six selectable
 * forms, the profile stays authoritative and this test makes the drift impossible to land. If the
 * two disagree, the eye height a designer wrote in JSON is not the one the camera uses.
 *
 * <p><b>Width and height are deliberately not asserted here.</b> They have already drifted —
 * {@code dog.json} is {@code 0.8 x 0.9} where its profile is {@code 0.60 x 0.85} — and picking a
 * winner resizes a live collision box. That is a form-system decision, documented on
 * {@code AnimagusCapabilityService}, not something to settle inside an eye-height test.
 */
class AnimagusEyeHeightParityTest {

    private static final Path FORMS = Path.of("src/main/resources/data/wizards_and_beasts/animagus_forms");

    @Test
    void everySelectableFormDeclaresItsOwnEyeHeight() {
        for (String formId : AnimagusForms.IDS) {
            SizeProfile profile = SizeProfileRegistry.get(formId);
            assertNotNull(profile, formId + " is selectable but has no size profile");

            float derived = profile.hitboxHeight() * SizeProfile.VANILLA_EYE_RATIO;
            assertNotEqualsApprox(derived, profile.eyeHeight(),
                    formId + " still leans on vanilla's height * 0.85. An animal's eyes are not a "
                            + "fixed fraction of its back — declare the value.");
        }
    }

    @Test
    void declaredEyeHeightMatchesTheDatapackWhereBothExist() {
        List<String> checked = new ArrayList<>();

        for (String formId : AnimagusForms.IDS) {
            JsonObject hitbox = readHitbox(formId);
            if (hitbox == null) {
                continue; // No definition — the vocabulary gap AnimagusFormLoader warns about.
            }
            SizeProfile profile = SizeProfileRegistry.get(formId);
            assertNotNull(profile, formId);

            assertEquals(hitbox.get("eye_height").getAsFloat(), profile.eyeHeight(), 1.0e-4f,
                    formId + ": the datapack and the size profile disagree about eye height, and the "
                            + "profile is what the camera uses — so the JSON is a lie.");
            checked.add(formId);
        }

        assertFalse(checked.isEmpty(),
                "No selectable form resolved to a definition. Either the binding broke or every "
                        + "animagus_forms file was renamed — see AnimagusFormLoader#reportVocabularyGap.");
    }

    /**
     * Pins the ratio {@link SizeProfile#VANILLA_EYE_RATIO} copies, so a vanilla change to
     * {@code EntityDimensions.defaultEyeHeight} cannot leave the constant quietly stale.
     */
    @Test
    void vanillaEyeRatioStillMatchesEntityDimensions() {
        EntityDimensions vanilla = EntityDimensions.scalable(1.0f, 2.0f);
        assertEquals(2.0f * SizeProfile.VANILLA_EYE_RATIO, vanilla.eyeHeight(), 1.0e-5f);
    }

    /** The {@code hitbox} block of a selectable form's definition, or null if it has none. */
    private static JsonObject readHitbox(String storedFormId) {
        String beast = AnimagusFormBinding.toFormKey(storedFormId)
                .map(net.minecraft.resources.Identifier::getPath)
                .orElse(null);
        if (beast == null) {
            return null;
        }
        Path file = FORMS.resolve(beast + ".json");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("hitbox");
        } catch (IOException e) {
            throw new AssertionError("Could not read " + file, e);
        }
    }

    private static void assertNotEqualsApprox(float unwanted, float actual, String message) {
        assertTrue(Math.abs(unwanted - actual) > 1.0e-4f, message);
    }
}
