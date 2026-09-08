package at.koopro.wizardsandbeasts.wand.bench;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wandmaker's bench rig, and the three ways it can quietly stop being a workshop.
 *
 * <p>Same reasoning as {@code CauldronRigTest}: a clip that names a bone which does not exist is
 * ignored by GeckoLib without a log line, and a missing clip plays nothing. Neither throws. The one
 * bench-specific trap is the spinning blank — see {@link #theBlankActuallyCompletesATurn()}.
 */
class BenchRigTest {

    /**
     * `_rig`, not the bare block name. {@code DefaultedBlockGeoModel} derives model, animation and
     * texture paths from one id, and the bare name resolves the texture to the flat block sprite that
     * the JSON model and the block item still use.
     */
    private static final String RIG = "wandmakers_bench_rig";

    private static final Path GEO = Path.of(
            "src/main/resources/assets/wizards_and_beasts/geckolib/models/block/" + RIG + ".geo.json");
    private static final Path ANIM = Path.of(
            "src/main/resources/assets/wizards_and_beasts/geckolib/animations/block/" + RIG + ".animation.json");
    private static final Path SHEET = Path.of(
            "src/main/resources/assets/wizards_and_beasts/textures/block/" + RIG + ".png");

    private static JsonObject geometry() throws IOException {
        return JsonParser.parseString(Files.readString(GEO)).getAsJsonObject()
                .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
    }

    private static JsonObject clips() throws IOException {
        return JsonParser.parseString(Files.readString(ANIM)).getAsJsonObject()
                .getAsJsonObject("animations");
    }

    private static Set<String> boneNames() throws IOException {
        Set<String> names = new LinkedHashSet<>();
        for (var bone : geometry().getAsJsonArray("bones")) {
            names.add(bone.getAsJsonObject().get("name").getAsString());
        }
        return names;
    }

    @Test
    void bothClipsTheBlockEntityAsksForExist() throws IOException {
        // WandmakersBenchBlockEntity.registerControllers names exactly these two.
        JsonObject clips = clips();
        for (String clip : new String[]{"animation.wandmakers_bench.idle",
                                        "animation.wandmakers_bench.working"}) {
            assertTrue(clips.has(clip), "missing clip: " + clip);
        }
        assertEquals(2, clips.size(), "clips nothing plays; either wire them or delete them");
    }

    @Test
    void everyBoneAClipDrivesActuallyExists() throws IOException {
        Set<String> bones = boneNames();
        List<String> orphans = new ArrayList<>();
        for (var clip : clips().entrySet()) {
            for (String bone : clip.getValue().getAsJsonObject().getAsJsonObject("bones").keySet()) {
                if (!bones.contains(bone)) {
                    orphans.add(clip.getKey() + " drives '" + bone + "', which is not a bone");
                }
            }
        }
        assertEquals(List.of(), orphans, "clips driving names that are not bones");
    }

    @Test
    void theBlankActuallyCompletesATurn() throws IOException {
        // The trap: GeckoLib interpolates the SHORTEST arc between two rotation keyframes, so a clip
        // running 0 -> 360 in one step is indistinguishable from one that never moves. The lathe would
        // look switched off while the treadle pumped. A midpoint keyframe is what forces the long way
        // round, and this asserts one is there.
        JsonObject rotation = clips().getAsJsonObject("animation.wandmakers_bench.working")
                .getAsJsonObject("bones").getAsJsonObject("blank").getAsJsonObject("rotation");
        assertTrue(rotation.size() >= 3,
                "a spin needs an intermediate keyframe or it interpolates to a standstill: " + rotation);

        List<Double> steps = new ArrayList<>();
        double previous = Double.NaN;
        for (var keyframe : rotation.entrySet()) {
            double x = keyframe.getValue().getAsJsonArray().get(0).getAsDouble();
            if (!Double.isNaN(previous)) {
                steps.add(Math.abs(x - previous));
            }
            previous = x;
        }
        for (double step : steps) {
            assertTrue(step > 0 && step < 360,
                    "each hop must be a real, unambiguous arc; got " + step + " from " + rotation);
        }
    }

    @Test
    void theIdleClipIsCalmerThanTheWorkingOne() throws IOException {
        // Idle plays on every bench in the world, forever. If it ever ends up the busier of the two,
        // that is a mistake worth catching rather than a style opinion.
        double idle = clips().getAsJsonObject("animation.wandmakers_bench.idle")
                .get("animation_length").getAsDouble();
        double working = clips().getAsJsonObject("animation.wandmakers_bench.working")
                .get("animation_length").getAsDouble();
        assertTrue(idle > working, "idle loop (" + idle + "s) should be slower than working (" + working + "s)");
    }

    @Test
    void theSheetIsBigEnoughForTheUvsTheGeometryClaims() throws IOException {
        JsonObject geometry = geometry();
        int width = geometry.getAsJsonObject("description").get("texture_width").getAsInt();
        int height = geometry.getAsJsonObject("description").get("texture_height").getAsInt();
        List<String> overflow = new ArrayList<>();
        for (var bone : geometry.getAsJsonArray("bones")) {
            JsonObject asObject = bone.getAsJsonObject();
            if (!asObject.has("cubes")) {
                continue;
            }
            for (var cube : asObject.getAsJsonArray("cubes")) {
                JsonArray uv = cube.getAsJsonObject().getAsJsonArray("uv");
                JsonArray size = cube.getAsJsonObject().getAsJsonArray("size");
                int w = (int) Math.ceil(size.get(0).getAsDouble());
                int h = (int) Math.ceil(size.get(1).getAsDouble());
                int d = (int) Math.ceil(size.get(2).getAsDouble());
                if (uv.get(0).getAsInt() + 2 * d + 2 * w > width
                        || uv.get(1).getAsInt() + d + h > height) {
                    overflow.add(asObject.get("name").getAsString());
                }
            }
        }
        assertEquals(List.of(), overflow, "UV islands outside the texture");
    }

    @Test
    void theRigIsReachableUnderTheNameTheRendererAsksFor() throws IOException {
        assertTrue(Files.exists(GEO), "geo at the defaulted path");
        assertTrue(Files.exists(ANIM), "animations at the defaulted path");
        assertTrue(Files.exists(SHEET), "sheet at the defaulted path");
        assertEquals("geometry.wandmakers_bench",
                geometry().getAsJsonObject("description").get("identifier").getAsString());
    }

    @Test
    void theRigDoesNotStealTheFlatBlockTexture() {
        // The whole reason for the _rig suffix. If this file ever went missing, the item in the hand
        // and the bench on the ground would stop matching.
        assertTrue(Files.exists(Path.of(
                        "src/main/resources/assets/wizards_and_beasts/textures/block/wandmakers_bench.png")),
                "the flat block texture the item model uses must still be there");
    }
}
