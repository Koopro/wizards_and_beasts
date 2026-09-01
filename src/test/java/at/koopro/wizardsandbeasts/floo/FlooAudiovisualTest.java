package at.koopro.wizardsandbeasts.floo;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Floo Network's audiovisual identity, pinned where it can be pinned.
 *
 * <p>Particles and light emission need a world, so what is checked here is the part that is data: the
 * one emerald constant, and the fact that every Floo sound event this mod registers actually resolves
 * to something in {@code sounds.json}.
 *
 * <p>That second check is worth more than it looks. This mod ships <b>no {@code .ogg} files at all</b>
 * — every sound is a vanilla sample re-pitched in {@code sounds.json} — so a registered
 * {@code SoundEvent} with no entry there is not a quiet sound, it is a silent one, and nothing at
 * runtime complains. The failure mode is a feature that simply has no audio and no error to say so.
 */
class FlooAudiovisualTest {

    private static final Path SOUNDS_JSON =
            Path.of("src/main/resources/assets/wizards_and_beasts/sounds.json");
    private static final Path EN_US =
            Path.of("src/main/resources/assets/wizards_and_beasts/lang/en_us.json");

    /** Every Floo sound event registered in {@code ModSounds}, by its registry path. */
    private static final List<String> FLOO_SOUNDS = List.of(
            "floo_whoosh",
            "floo_ignite",
            "floo_land",
            "floo_travel_loop",
            "floo_arrival",
            "floo_fail_sputter",
            "floo_sealed");

    private static JsonObject read(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void everyFlooSoundHasAnEntryInSoundsJson() throws IOException {
        JsonObject sounds = read(SOUNDS_JSON);
        for (String name : FLOO_SOUNDS) {
            assertTrue(sounds.has(name),
                    name + " is registered as a SoundEvent but has no sounds.json entry — it would "
                            + "play silently, with nothing at runtime to say so");
        }
    }

    @Test
    void everyFlooSoundResolvesToARealVanillaSample() throws IOException {
        JsonObject sounds = read(SOUNDS_JSON);
        for (String name : FLOO_SOUNDS) {
            var entry = sounds.getAsJsonObject(name);
            var layers = entry.getAsJsonArray("sounds");
            assertNotNull(layers, name + " has no sounds array");
            assertTrue(!layers.isEmpty(), name + " has an empty sounds array");
            for (var layer : layers) {
                String sample = layer.getAsJsonObject().get("name").getAsString();
                // A mod-namespaced sample would need an .ogg this repo does not contain. Every layer
                // has to point at something the vanilla jar already ships.
                assertTrue(sample.startsWith("minecraft:"),
                        name + " layer \"" + sample + "\" is not a vanilla sample, and this mod ships "
                                + "no audio files of its own");
            }
        }
    }

    @Test
    void everyFlooSoundHasASubtitle() throws IOException {
        JsonObject sounds = read(SOUNDS_JSON);
        JsonObject lang = read(EN_US);
        for (String name : FLOO_SOUNDS) {
            var entry = sounds.getAsJsonObject(name);
            assertTrue(entry.has("subtitle"), name + " has no subtitle key");
            String key = entry.get("subtitle").getAsString();
            assertTrue(lang.has(key),
                    name + " names subtitle \"" + key + "\", which is not in en_us.json — players "
                            + "with subtitles on would see the raw key");
        }
    }

    @Test
    void departureAndArrivalDoNotShareASound() throws IOException {
        // They used to. The whoosh played both when somebody left and when somebody arrived, so a
        // room could not tell the two apart by ear — which is most of what a bystander has to go on.
        JsonObject sounds = read(SOUNDS_JSON);
        String whoosh = sounds.getAsJsonObject("floo_whoosh").getAsJsonArray("sounds").toString();
        String arrival = sounds.getAsJsonObject("floo_arrival").getAsJsonArray("sounds").toString();
        assertTrue(!whoosh.equals(arrival),
                "departure and arrival must not resolve to the same layers");
    }

    @Test
    void thereIsExactlyOneFlooGreen() {
        // The colour was copied to seven call sites before FlooCues existed. The constant is the
        // point; this pins its value so a change is deliberate rather than incidental.
        assertEquals(0xFF21B342, FlooCues.EMERALD);
    }

    @Test
    void theEmeraldIsFullyOpaque() {
        // A DustParticleOptions colour with a zero alpha byte is the silent-failure shape this repo
        // has hit before on outlines: it reads as "no colour" and draws nothing at all.
        assertEquals(0xFF, (FlooCues.EMERALD >>> 24) & 0xFF,
                "the emerald must carry a full alpha byte");
    }
}
