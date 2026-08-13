package at.koopro.wizardsandbeasts.client.form.geo;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rig table has to describe assets that are really on disk.
 *
 * <p>This is the test that earns its keep. Asking GeckoLib for a clip its {@code .animation.json}
 * does not define throws inside the render pass — mid-frame, for whoever is transformed — and a
 * missing {@code .geo.json} or texture fails the same way. Both are one typo away at all times, and
 * neither is visible until someone transforms in-game.
 */
class PlayerFormRigTest {

    private static final Path ASSETS = Path.of("src", "main", "resources", "assets", "wizards_and_beasts");

    private static Path geo(PlayerFormRig rig) {
        return ASSETS.resolve(Path.of("geckolib", "models", "entity", rig.asset() + ".geo.json"));
    }

    private static Path animations(PlayerFormRig rig) {
        return ASSETS.resolve(Path.of("geckolib", "animations", "entity", rig.asset() + ".animation.json"));
    }

    private static Path texture(PlayerFormRig rig) {
        return ASSETS.resolve(Path.of("textures", "entity", rig.asset() + ".png"));
    }

    private static Set<String> clipNames(PlayerFormRig rig) throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(animations(rig))).getAsJsonObject();
        return new HashSet<>(root.getAsJsonObject("animations").keySet());
    }

    private static List<PlayerFormRig> allRigs() {
        return PlayerFormRig.riggedFormIds().stream()
                .map(PlayerFormRig::forForm)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Test
    void everyRigHasItsGeometryAnimationsAndTextureOnDisk() {
        for (String formId : PlayerFormRig.riggedFormIds()) {
            PlayerFormRig rig = PlayerFormRig.forForm(formId);
            assertNotNull(rig);
            assertTrue(Files.exists(geo(rig)), formId + " -> missing geometry " + geo(rig));
            assertTrue(Files.exists(animations(rig)), formId + " -> missing animations " + animations(rig));
            assertTrue(Files.exists(texture(rig)), formId + " -> missing texture " + texture(rig));
        }
    }

    @Test
    void everyDeclaredClipExistsInItsAnimationFile() throws IOException {
        for (PlayerFormRig rig : allRigs()) {
            Set<String> defined = clipNames(rig);

            assertTrue(defined.contains(rig.idleClip()),
                    rig.asset() + " declares idle clip '" + rig.idleClip()
                            + "' which its animation file does not define. Defined: " + defined);

            if (rig.movementClip() != null) {
                assertTrue(defined.contains(rig.movementClip()),
                        rig.asset() + " declares movement clip '" + rig.movementClip()
                                + "' which its animation file does not define. Defined: " + defined);
            }
        }
    }

    /**
     * A rig with a movement clip available but not declared is a silent downgrade — the form would
     * slide around playing its idle. Catches the reverse of the test above.
     */
    @Test
    void noRigLeavesAnAvailableMovementClipUnused() throws IOException {
        for (PlayerFormRig rig : allRigs()) {
            if (rig.movementClip() != null) {
                continue;
            }
            Set<String> unusedMovement = new HashSet<>(clipNames(rig));
            unusedMovement.removeIf(name -> name.equals(rig.idleClip()));
            assertTrue(unusedMovement.isEmpty(),
                    rig.asset() + " has movement clips " + unusedMovement
                            + " that the rig table never plays; declare one as the movement clip");
        }
    }

    @Test
    void geometryIdentifierMatchesTheAssetName() throws IOException {
        for (PlayerFormRig rig : allRigs()) {
            JsonObject root = JsonParser.parseString(Files.readString(geo(rig))).getAsJsonObject();
            String identifier = root.getAsJsonArray("minecraft:geometry")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("description")
                    .get("identifier").getAsString();
            assertEquals("geometry." + rig.asset(), identifier,
                    geo(rig) + " declares a geometry identifier that does not match its file name");
        }
    }

    @Test
    void aRigWithNoMovementClipFallsBackToIdle() {
        PlayerFormRig goblin = PlayerFormRig.forForm("goblin_default");
        assertNotNull(goblin);
        assertNull(goblin.movementClip(), "goblin_teller ships an idle and nothing else");
        assertEquals(goblin.idleClip(), goblin.clipFor(true),
                "asking for a clip the file does not define throws mid-render; idle is the safe answer");
        assertEquals(goblin.idleClip(), goblin.clipFor(false));
    }

    @Test
    void aRigWithAMovementClipUsesItWhileMoving() {
        PlayerFormRig werewolf = PlayerFormRig.forForm("werewolf_wolf");
        assertNotNull(werewolf);
        assertEquals("animation.werewolf.walk", werewolf.clipFor(true));
        assertEquals("animation.werewolf.idle", werewolf.clipFor(false));
    }

    @Test
    void unriggedFormsResolveToNullSoTheLegacyPathStillRuns() {
        assertNull(PlayerFormRig.forForm("house_elf_default"),
                "house-elf has no rig authored yet and must fall through to the legacy model");
        assertNull(PlayerFormRig.forForm("veela_harpy"));
        assertNull(PlayerFormRig.forForm("human_default"));
        assertFalse(PlayerFormRig.hasRig("animagus_cat"),
                "Animagus forms borrow real vanilla models, which beat any rig we would author");
    }

    /** Texture paths are the one asset GeckoLib does not expand, so the full path must be right. */
    @Test
    void textureIdentifierIsWrittenOutInFull() {
        PlayerFormRig werewolf = PlayerFormRig.forForm("werewolf_wolf");
        assertNotNull(werewolf);
        assertEquals("wizards_and_beasts:textures/entity/werewolf.png",
                werewolf.textureResource().toString());
        assertEquals("wizards_and_beasts:entity/werewolf", werewolf.modelResource().toString());
        assertEquals("wizards_and_beasts:entity/werewolf", werewolf.animationResource().toString());
    }
}
