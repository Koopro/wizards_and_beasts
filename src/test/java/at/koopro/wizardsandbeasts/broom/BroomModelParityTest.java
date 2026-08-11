package at.koopro.wizardsandbeasts.broom;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Holds the master broom rig against the things that reference it by name.
 *
 * <p>Every failure this catches compiles cleanly and only shows up in a running game as an
 * invisible part, a dead animation or a missing-texture broom — GeckoLib resolves bones,
 * clips and textures by string at load time, so nothing upstream of the client notices.
 */
class BroomModelParityTest {

    private static final Gson GSON = new Gson();
    private static final Path ASSETS = Path.of("src", "main", "resources", "assets", "wizards_and_beasts");
    private static final Path GEO = ASSETS.resolve(Path.of("geckolib", "models", "entity", "broom.geo.json"));
    private static final Path ANIM = ASSETS.resolve(Path.of("geckolib", "animations", "entity", "broom.animation.json"));
    private static final Path SKIN = ASSETS.resolve(Path.of("textures", "entity", "broom.png"));
    private static final Path ENTITY_SRC =
            Path.of("src", "main", "java", "at", "koopro", "wizardsandbeasts", "entity", "broom", "BroomEntity.java");

    /** Every slot in the enum must have at least one variant bone, or that slot cannot be selected. */
    @Test
    void everySlotHasVariantBonesInTheRig() throws IOException {
        Set<String> bones = boneNames();
        for (BroomSlot slot : BroomSlot.values()) {
            boolean any = bones.stream().anyMatch(b -> b.startsWith(slot.bonePrefix()));
            assertTrue(any, slot + " has no " + slot.bonePrefix() + "* bones in broom.geo.json");
        }
    }

    /** Each slot's container bone must exist: the animations key off it, not off the variants. */
    @Test
    void everySlotHasItsContainerBone() throws IOException {
        Set<String> bones = boneNames();
        for (BroomSlot slot : BroomSlot.values()) {
            assertTrue(bones.contains(slot.slotId()),
                    "missing container bone '" + slot.slotId() + "' — animations target it so one clip "
                            + "covers every variant of the slot");
        }
    }

    /**
     * {@code BroomSlot.variants()} must name exactly the variant bones the rig ships.
     *
     * <p>This is the seam between a Java list and a Python generator, and it fails silently in the
     * worst direction: the renderer shows one variant by hiding every other one it knows about, so a
     * bone the list has never heard of is never hidden and draws on top of whatever was selected.
     * A missing bone is the milder failure — a variant that simply cannot be chosen.
     */
    @Test
    void slotVariantListsMatchTheRig() throws IOException {
        Set<String> bones = boneNames();
        for (BroomSlot slot : BroomSlot.values()) {
            Set<String> declared = new HashSet<>();
            slot.variants().forEach(v -> declared.add(slot.boneName(v)));

            Set<String> inRig = new HashSet<>();
            bones.stream()
                    .filter(b -> b.startsWith(slot.bonePrefix()))
                    .forEach(inRig::add);

            assertEquals(inRig, declared,
                    slot + ": BroomSlot.variants() and broom.geo.json disagree. Bones only in the "
                            + "rig are never hidden and will draw over the selected variant; bones "
                            + "only in the list cannot be selected.");
        }
    }

    /** The default silhouette must be drawable, or an unconfigured broom renders nothing. */
    @Test
    void defaultVariantBonesExist() throws IOException {
        Set<String> bones = boneNames();
        BroomSlot.defaults().forEach((slot, id) ->
                assertTrue(bones.contains(slot.boneName(id.getPath())),
                        "default variant " + id + " for " + slot + " has no bone "
                                + slot.boneName(id.getPath())));
    }

    /** Bones the renderer and the FX pass reach for by literal name. */
    @Test
    void contractBonesSurvive() throws IOException {
        Set<String> bones = boneNames();
        assertTrue(bones.contains("broom_body"), "BroomRenderer applies tilt to broom_body by name");
        assertTrue(bones.contains("root"), "the shipped clips animate root");
        for (String fx : new String[]{"fx_tip", "fx_tail", "fx_mount"}) {
            assertTrue(bones.contains(fx), "missing FX anchor " + fx);
        }
    }

    /** Every animation BroomEntity names must exist, and every clip must target real bones. */
    @Test
    void animationsResolveAgainstEntityAndRig() throws IOException {
        JsonObject clips = GSON.fromJson(Files.readString(ANIM), JsonObject.class)
                .getAsJsonObject("animations");
        Set<String> bones = boneNames();

        Matcher m = Pattern.compile("\"(animation\\.broom\\.[a-z_]+)\"")
                .matcher(Files.readString(ENTITY_SRC));
        int referenced = 0;
        while (m.find()) {
            referenced++;
            assertTrue(clips.has(m.group(1)),
                    "BroomEntity plays " + m.group(1) + " but broom.animation.json has no such clip");
        }
        assertTrue(referenced > 0, "expected BroomEntity to reference animations by name");

        for (String clip : clips.keySet()) {
            JsonObject targets = clips.getAsJsonObject(clip).getAsJsonObject("bones");
            if (targets == null) continue;
            for (String bone : targets.keySet()) {
                assertTrue(bones.contains(bone),
                        clip + " animates '" + bone + "', which is not a bone in broom.geo.json");
            }
        }
    }

    /** The rig's declared sheet size must match the PNG, and no island may fall off it. */
    @Test
    void everyCubeIslandFitsTheDeclaredSheet() throws IOException {
        JsonObject geometry = geometry();
        JsonObject desc = geometry.getAsJsonObject("description");
        int w = desc.get("texture_width").getAsInt();
        int h = desc.get("texture_height").getAsInt();

        assertTrue(Files.exists(SKIN), "broom.geo.json has no texture at " + SKIN);
        var png = javax.imageio.ImageIO.read(SKIN.toFile());
        assertEquals(w, png.getWidth(), "declared texture_width does not match broom.png");
        assertEquals(h, png.getHeight(), "declared texture_height does not match broom.png");

        geometry.getAsJsonArray("bones").forEach(el -> {
            JsonObject bone = el.getAsJsonObject();
            if (!bone.has("cubes")) return;
            bone.getAsJsonArray("cubes").forEach(ce -> {
                JsonObject cube = ce.getAsJsonObject();
                var size = cube.getAsJsonArray("size");
                var uv = cube.getAsJsonArray("uv");
                int cw = ceil(size.get(0).getAsDouble());
                int ch = ceil(size.get(1).getAsDouble());
                int cd = ceil(size.get(2).getAsDouble());
                int u = uv.get(0).getAsInt();
                int v = uv.get(1).getAsInt();
                assertTrue(u + 2 * cd + 2 * cw <= w && v + cd + ch <= h,
                        bone.get("name").getAsString() + " has a box-UV island running off the "
                                + w + "x" + h + " sheet at uv [" + u + ", " + v + "]");
            });
        });
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static int ceil(double v) {
        return (int) Math.round(v + 0.4999);
    }

    private static JsonObject geometry() throws IOException {
        return GSON.fromJson(Files.readString(GEO), JsonObject.class)
                .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
    }

    private static Set<String> boneNames() throws IOException {
        Set<String> names = new HashSet<>();
        geometry().getAsJsonArray("bones").forEach(
                el -> names.add(el.getAsJsonObject().get("name").getAsString()));
        return names;
    }
}
