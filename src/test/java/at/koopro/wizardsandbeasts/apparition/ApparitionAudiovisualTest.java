package at.koopro.wizardsandbeasts.apparition;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Apparition's audible half, pinned where it can be pinned.
 *
 * <p>Modelled on {@code FlooAudiovisualTest} and for the same reason: this mod ships <b>no {@code .ogg}
 * files at all</b>, so a registered {@code SoundEvent} with no {@code sounds.json} entry is not a quiet
 * sound, it is a silent one, and nothing at runtime complains.
 *
 * <p>The variant check is the one that earns its place. The server has always resolved which crack an
 * observer hears — {@link ApparitionCrackVariant} — and for a long time nothing on the client played any of
 * them, so the enum, the proficiency-scaled radius and the disguise-safe derivation behind it were all
 * inaudible. Deriving the expected sound names from the enum itself means a fourth variant cannot be added
 * without either giving it a crack or failing here.
 */
class ApparitionAudiovisualTest {

    private static final Path SOUNDS_JSON =
            Path.of("src/main/resources/assets/wizards_and_beasts/sounds.json");
    private static final Path EN_US =
            Path.of("src/main/resources/assets/wizards_and_beasts/lang/en_us.json");

    /** The tear, which is not keyed to a variant: a splinch sounds like a splinch whoever does it. */
    private static final String SPLINCH_SOUND = "apparition_splinch";
    /** The gathering before the crack, played at two pitches from one event. */
    private static final String WINDUP_SOUND = "apparition_windup";

    private static JsonObject read(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    /** Every sound event Apparition registers, by registry path. */
    private static List<String> apparitionSounds() {
        List<String> names = new java.util.ArrayList<>();
        for (ApparitionCrackVariant variant : ApparitionCrackVariant.values()) {
            names.add(soundNameFor(variant));
        }
        names.add(SPLINCH_SOUND);
        names.add(WINDUP_SOUND);
        return names;
    }

    /** The naming contract {@code ApparitionResolutionFx} relies on to map a variant onto a sound. */
    private static String soundNameFor(ApparitionCrackVariant variant) {
        return "apparition_crack_" + variant.getSerializedName();
    }

    @Test
    void everyCrackVariantHasASoundToPlay() throws IOException {
        JsonObject sounds = read(SOUNDS_JSON);
        for (ApparitionCrackVariant variant : ApparitionCrackVariant.values()) {
            assertTrue(sounds.has(soundNameFor(variant)),
                    "crack variant " + variant + " resolves to sound \"" + soundNameFor(variant)
                            + "\", which has no sounds.json entry — the server would decide on a crack "
                            + "nobody can hear");
        }
    }

    @Test
    void everyApparitionSoundResolvesToSomethingVanillaShips() throws IOException {
        JsonObject sounds = read(SOUNDS_JSON);
        for (String name : apparitionSounds()) {
            JsonObject entry = sounds.getAsJsonObject(name);
            assertNotNull(entry, name + " has no sounds.json entry");
            var layers = entry.getAsJsonArray("sounds");
            assertNotNull(layers, name + " has no sounds array");
            assertTrue(!layers.isEmpty(), name + " has an empty sounds array");
            for (var layer : layers) {
                String sample = layer.getAsJsonObject().get("name").getAsString();
                assertTrue(sample.startsWith("minecraft:"),
                        name + " layer \"" + sample + "\" is not a vanilla sample, and this mod ships no "
                                + "audio files of its own");
            }
        }
    }

    /**
     * The crack must not be the ender pearl.
     *
     * <p>Apparition used to borrow {@code entity.enderman.teleport} outright, which made a wizard folding
     * space and a thrown pearl the same event to anyone with their eyes shut. The design pillar names this
     * explicitly, and a sample is the easiest place for it to quietly come back.
     */
    @Test
    void noApparitionSoundBorrowsTheEnderTeleport() throws IOException {
        JsonObject sounds = read(SOUNDS_JSON);
        for (String name : apparitionSounds()) {
            for (var layer : sounds.getAsJsonObject(name).getAsJsonArray("sounds")) {
                String sample = layer.getAsJsonObject().get("name").getAsString();
                assertTrue(!sample.contains("enderman") && !sample.contains("endermen"),
                        name + " plays \"" + sample + "\" — Apparition is explicitly not an ender pearl");
            }
        }
    }

    @Test
    void everyApparitionSoundHasASubtitle() throws IOException {
        JsonObject sounds = read(SOUNDS_JSON);
        JsonObject lang = read(EN_US);
        for (String name : apparitionSounds()) {
            JsonObject entry = sounds.getAsJsonObject(name);
            assertTrue(entry.has("subtitle"), name + " has no subtitle key");
            String key = entry.get("subtitle").getAsString();
            assertTrue(lang.has(key),
                    name + " names subtitle \"" + key + "\", which is not in en_us.json — players with "
                            + "subtitles on would see the raw key");
        }
    }
}
