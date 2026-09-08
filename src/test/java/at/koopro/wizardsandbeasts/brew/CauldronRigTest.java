package at.koopro.wizardsandbeasts.brew;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cauldron rig, and the three ways it can silently stop working.
 *
 * <p>This replaces {@code CauldronVisualTest}'s two {@code tintindex} assertions. Those pinned a
 * tintable face on a JSON block model that no longer renders — the placed block is
 * {@code RenderShape.INVISIBLE} now and drawn by GeckoLib, which is what finally gave the pot an
 * inside and therefore an empty state.
 *
 * <p>Everything asserted here fails <em>quietly</em> in game. A missing clip plays nothing and the pot
 * freezes on whatever it last showed; a misnamed bone is ignored by GeckoLib without a log line, so a
 * liquid meant to be scaled to zero simply stays at full size and two surfaces z-fight in the same
 * plane. Neither throws, neither logs. Only a test catches them — and one of them, the steam puffs
 * authored as cubes inside one bone rather than as bones, was in fact written and caught here.
 */
class CauldronRigTest {

    private static final Path GEO = Path.of(
            "src/main/resources/assets/wizards_and_beasts/geckolib/models/block/wizarding_cauldron.geo.json");
    private static final Path ANIM = Path.of(
            "src/main/resources/assets/wizards_and_beasts/geckolib/animations/block/wizarding_cauldron.animation.json");
    private static final String CLIP_PREFIX = "animation.wizarding_cauldron.";

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
    void everyVisualStateHasAClipToPlay() throws IOException {
        // CauldronBlockEntity builds the clip name from the visual's serialized name. A state with no
        // clip does not error — GeckoLib finds nothing and the pot holds its previous look forever.
        JsonObject clips = clips();
        List<String> missing = new ArrayList<>();
        for (CauldronVisual visual : CauldronVisual.values()) {
            String clip = CLIP_PREFIX + visual.getSerializedName();
            if (!clips.has(clip)) {
                missing.add(clip);
            }
        }
        assertEquals(List.of(), missing, "visual states with no animation");
    }

    @Test
    void thereAreNoClipsThatNoStateCanReach() throws IOException {
        Set<String> reachable = new HashSet<>();
        for (CauldronVisual visual : CauldronVisual.values()) {
            reachable.add(CLIP_PREFIX + visual.getSerializedName());
        }
        List<String> orphans = clips().keySet().stream().filter(name -> !reachable.contains(name)).toList();
        assertEquals(List.of(), orphans, "clips nothing plays; either wire them or delete them");
    }

    @Test
    void everyBoneAClipDrivesActuallyExists() throws IOException {
        // The one that bit: three steam puffs authored as cubes inside a single `steam` bone. The
        // clips addressed them by cube name, GeckoLib matched nothing, and the puffs sat at full size
        // in every state including an empty pot — with no error anywhere.
        Set<String> bones = boneNames();
        List<String> orphans = new ArrayList<>();
        for (var clip : clips().entrySet()) {
            JsonObject driven = clip.getValue().getAsJsonObject().getAsJsonObject("bones");
            for (String bone : driven.keySet()) {
                if (!bones.contains(bone)) {
                    orphans.add(clip.getKey() + " drives '" + bone + "', which is not a bone");
                }
            }
        }
        assertEquals(List.of(), orphans, "clips driving names that are not bones");
    }

    @Test
    void thereIsOneLiquidBonePerLiquidBearingVisual() throws IOException {
        // EMPTY is the state with no liquid at all, so it gets no bone. Every other visual needs one
        // surface to call its own, or two states would share a look.
        Set<String> liquidBones = new LinkedHashSet<>();
        for (String bone : boneNames()) {
            if (bone.startsWith("liquid_")) {
                liquidBones.add(bone);
            }
        }
        List<String> missing = new ArrayList<>();
        for (CauldronVisual visual : CauldronVisual.values()) {
            if (!visual.hasLiquid()) {
                continue;
            }
            String bone = "liquid_" + visual.getSerializedName().toLowerCase(Locale.ROOT);
            if (!liquidBones.contains(bone)) {
                missing.add(bone);
            }
        }
        assertEquals(List.of(), missing, "liquid-bearing visuals with no surface bone");

        // And no spares: a liquid bone no visual can select is a surface nothing will ever show.
        long liquidBearing = java.util.Arrays.stream(CauldronVisual.values())
                .filter(CauldronVisual::hasLiquid).count();
        assertEquals(liquidBearing, liquidBones.size(), "liquid bones: " + liquidBones);
    }

    @Test
    void everyClipSetsEveryLiquidBoneExplicitly() throws IOException {
        // Not tidiness. Scale is the only thing hiding a liquid surface, and GeckoLib leaves a bone
        // where the last clip put it. A clip that names only its own liquid would let the previous
        // state's surface stay at full scale, and both would render in the same plane.
        Set<String> liquidBones = new LinkedHashSet<>();
        for (String bone : boneNames()) {
            if (bone.startsWith("liquid_")) {
                liquidBones.add(bone);
            }
        }
        List<String> gaps = new ArrayList<>();
        for (var clip : clips().entrySet()) {
            JsonObject driven = clip.getValue().getAsJsonObject().getAsJsonObject("bones");
            for (String bone : liquidBones) {
                if (!driven.has(bone)) {
                    gaps.add(clip.getKey() + " does not set " + bone);
                } else if (!driven.getAsJsonObject(bone).has("scale")) {
                    gaps.add(clip.getKey() + " sets " + bone + " without a scale");
                }
            }
        }
        assertEquals(List.of(), gaps, "clips that leave a liquid bone to the previous state");
    }

    @Test
    void theEmptyClipShowsNoLiquidAtAll() throws IOException {
        // The whole point of the rebuild. If this passes and the pot still looks full, the problem is
        // elsewhere; if it fails, every cauldron in the world looks full again.
        JsonObject driven = clips().getAsJsonObject(CLIP_PREFIX + CauldronVisual.EMPTY.getSerializedName())
                .getAsJsonObject("bones");
        List<String> showing = new ArrayList<>();
        for (String bone : driven.keySet()) {
            if (!bone.startsWith("liquid_")) {
                continue;
            }
            JsonObject scale = driven.getAsJsonObject(bone).getAsJsonObject("scale");
            for (var keyframe : scale.entrySet()) {
                JsonArray value = keyframe.getValue().getAsJsonArray();
                boolean allZero = value.get(0).getAsDouble() == 0
                        && value.get(1).getAsDouble() == 0
                        && value.get(2).getAsDouble() == 0;
                if (!allZero) {
                    showing.add(bone + " at t=" + keyframe.getKey() + " is " + value);
                }
            }
        }
        assertEquals(List.of(), showing, "an empty cauldron must have nothing in it");
    }

    @Test
    void nothingCapsThePot() throws IOException {
        // This exact bug has now shipped twice. The original JSON model stretched its `#top` texture
        // across the opening, which is why a cauldron always looked full and why the tint was
        // invisible from any standing angle. The first version of this rig then put a 13x1x13 rim
        // *slab* over the same opening and sealed the liquid under it again — despite the datagen
        // model carrying a comment saying "a slab would cap the pot and hide the brew".
        //
        // The rule: no structural cube may cross the pot's mouth above the liquid. Steam is exempt
        // and is checked nowhere near here — it is meant to rise out of the pot, and it is scaled to
        // zero unless the brew is actually hot.
        double liquidTop = Double.NEGATIVE_INFINITY;
        for (var bone : geometry().getAsJsonArray("bones")) {
            JsonObject asObject = bone.getAsJsonObject();
            if (!asObject.get("name").getAsString().startsWith("liquid_") || !asObject.has("cubes")) {
                continue;
            }
            for (var cube : asObject.getAsJsonArray("cubes")) {
                JsonArray origin = cube.getAsJsonObject().getAsJsonArray("origin");
                JsonArray size = cube.getAsJsonObject().getAsJsonArray("size");
                liquidTop = Math.max(liquidTop, origin.get(1).getAsDouble() + size.get(1).getAsDouble());
            }
        }
        assertTrue(liquidTop > Double.NEGATIVE_INFINITY, "no liquid bones at all");

        List<String> lids = new ArrayList<>();
        for (var bone : geometry().getAsJsonArray("bones")) {
            JsonObject asObject = bone.getAsJsonObject();
            String name = asObject.get("name").getAsString();
            if (!asObject.has("cubes") || name.startsWith("liquid_") || name.startsWith("steam")) {
                continue;
            }
            for (var cube : asObject.getAsJsonArray("cubes")) {
                JsonArray origin = cube.getAsJsonObject().getAsJsonArray("origin");
                JsonArray size = cube.getAsJsonObject().getAsJsonArray("size");
                double x0 = origin.get(0).getAsDouble();
                double y0 = origin.get(1).getAsDouble();
                double z0 = origin.get(2).getAsDouble();
                double x1 = x0 + size.get(0).getAsDouble();
                double y1 = y0 + size.get(1).getAsDouble();
                double z1 = z0 + size.get(2).getAsDouble();
                // Overlaps the open column, and sits at or above the liquid: that is a lid.
                boolean overTheMouth = x0 < 0 && x1 > 0 && z0 < 0 && z1 > 0;
                if (overTheMouth && y1 > liquidTop && y0 >= liquidTop - 0.001) {
                    lids.add(name + " spans the pot's mouth at y " + y0 + ".." + y1);
                }
            }
        }
        assertEquals(List.of(), lids, "a cauldron you cannot see into is the bug this rig exists to fix");
    }

    @Test
    void theLiquidSitsHighEnoughToBeSeenOverTheRim() throws IOException {
        // A surface low in the pot is hidden behind the rim from every angle except directly
        // overhead — the same reason the tinted `#top` face never worked. The first rig put it at
        // y 9 of 16, which is roughly halfway down and read as an empty pot from standing height.
        double liquidTop = Double.NEGATIVE_INFINITY;
        double rimTop = Double.NEGATIVE_INFINITY;
        for (var bone : geometry().getAsJsonArray("bones")) {
            JsonObject asObject = bone.getAsJsonObject();
            String name = asObject.get("name").getAsString();
            if (!asObject.has("cubes")) {
                continue;
            }
            for (var cube : asObject.getAsJsonArray("cubes")) {
                JsonArray origin = cube.getAsJsonObject().getAsJsonArray("origin");
                JsonArray size = cube.getAsJsonObject().getAsJsonArray("size");
                double top = origin.get(1).getAsDouble() + size.get(1).getAsDouble();
                if (name.startsWith("liquid_")) {
                    liquidTop = Math.max(liquidTop, top);
                } else if (name.equals("rim") || name.equals("pot")) {
                    rimTop = Math.max(rimTop, top);
                }
            }
        }
        // Within two texels of the lip. Deeper than that and the rim eats it from a standing view.
        assertTrue(rimTop - liquidTop <= 2.0,
                "liquid top " + liquidTop + " sits " + (rimTop - liquidTop) + " below the rim at " + rimTop);
    }

    @Test
    void theSheetIsBigEnoughForTheUvsTheGeometryClaims() throws IOException {
        // A cube whose island runs off the sheet samples transparent pixels and renders as a hole.
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
                int islandW = 2 * d + 2 * w;
                int islandH = d + h;
                if (uv.get(0).getAsInt() + islandW > width || uv.get(1).getAsInt() + islandH > height) {
                    overflow.add(asObject.get("name").getAsString() + " island runs off the sheet");
                }
            }
        }
        assertEquals(List.of(), overflow, "UV islands outside the texture");
    }

    @Test
    void allThreeMetalSheetsExist() {
        // One rig, three sheets, chosen by CauldronGeoModel from the blockstate. A missing sheet is a
        // magenta checkerboard cauldron.
        List<String> missing = new ArrayList<>();
        for (String metal : new String[]{"pewter", "brass", "wizarding_copper"}) {
            Path sheet = Path.of("src/main/resources/assets/wizards_and_beasts/textures/block/"
                    + "wizarding_cauldron_" + metal + ".png");
            if (!Files.exists(sheet)) {
                missing.add(sheet.toString());
            }
        }
        assertEquals(List.of(), missing, "metal sheets the renderer asks for but nothing ships");
    }

    @Test
    void theRigIsReachableUnderTheNameTheModelAsksFor() throws IOException {
        // DefaultedBlockGeoModel("wizarding_cauldron") resolves exactly these two paths. If either
        // moved, the block would render nothing at all and the pot would simply be missing.
        assertTrue(Files.exists(GEO), "geo at the defaulted path");
        assertTrue(Files.exists(ANIM), "animations at the defaulted path");
        assertEquals("geometry.wizarding_cauldron",
                geometry().getAsJsonObject("description").get("identifier").getAsString());
    }
}
