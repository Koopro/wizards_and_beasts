package at.koopro.wizardsandbeasts.wand;

import at.koopro.wizardsandbeasts.wand.customization.WandModule;
import at.koopro.wizardsandbeasts.wand.customization.WandModuleRegistry;
import at.koopro.wizardsandbeasts.wand.customization.WandSlot;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The modular wand's connection contract, measured on the shipped rig (written by
 * {@code tools/wand_model.py}).
 *
 * <p>Any handle is combined with any shaft and any tip, so what has to hold is not that some
 * particular wand looks right but that every part stays inside its slot's band and meets the next
 * part on the shared plane: handle y 0..11, shaft 11..23, tip from 23, one spell attachment at
 * (0, 27.5, 0) that no variant owns. Measured with bone rest rotations applied, because the curled
 * parts (Bellatrix, the hook, the claws) are chains of rotated bones.
 *
 * <p>And the Blockbench rules: axis-aligned cubes (no cube rotation), whole-number sizes (GeckoLib
 * floors box-UV sizes), every island on the sheet.
 */
class WandModelContractTest {

    private static final Path ITEM_GEO = Path.of("src", "main", "resources", "assets",
            "wizards_and_beasts", "geckolib", "models", "item");
    private static final Path WAND_ANIM = Path.of("src", "main", "resources", "assets",
            "wizards_and_beasts", "geckolib", "animations", "item", "wand.animation.json");

    private static final double HANDLE_TOP = 11.0;
    private static final double SHAFT_TOP = 23.0;
    private static final double[] ATTACHMENT = {0.0, 27.5, 0.0};
    private static final double EPS = 1e-4;

    private static final Map<String, JsonObject> BONES = new LinkedHashMap<>();
    private static final Map<String, List<String>> CHILDREN = new HashMap<>();
    private static int texW;
    private static int texH;

    @BeforeAll
    static void load() throws IOException {
        WandModuleRegistry.bootstrap();
        JsonObject geo = geometry("wand");
        texW = geo.getAsJsonObject("description").get("texture_width").getAsInt();
        texH = geo.getAsJsonObject("description").get("texture_height").getAsInt();
        for (JsonElement e : geo.getAsJsonArray("bones")) {
            JsonObject bone = e.getAsJsonObject();
            String name = bone.get("name").getAsString();
            BONES.put(name, bone);
            if (bone.has("parent")) {
                CHILDREN.computeIfAbsent(bone.get("parent").getAsString(), k -> new ArrayList<>()).add(name);
            }
        }
    }

    private static JsonObject geometry(String model) throws IOException {
        return JsonParser.parseString(Files.readString(ITEM_GEO.resolve(model + ".geo.json"))).getAsJsonObject()
                .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
    }

    @Test
    void everyRegisteredModuleIsAVariantBoneOfItsSlot() {
        for (WandSlot slot : WandSlot.values()) {
            List<WandModule> modules = WandModuleRegistry.getAllForSlot(slot);
            assertFalse(modules.isEmpty(), slot + " has no modules");
            for (WandModule module : modules) {
                JsonObject bone = BONES.get(module.boneName());
                assertNotNull(bone, module.id() + ": wand.geo.json has no bone " + module.boneName());
                assertEquals(slot.slotId(), bone.get("parent").getAsString(),
                        module.boneName() + " must hang directly off the '" + slot.slotId() + "' bone");
            }
            // And the other way: geometry under a slot that no module can select is dead weight.
            for (String variant : CHILDREN.getOrDefault(slot.slotId(), List.of())) {
                assertTrue(modules.stream().anyMatch(m -> m.boneName().equals(variant)),
                        variant + " is on the rig but no module selects it");
            }
        }
    }

    @Test
    void cubesAreBlockbenchSafeAndOnTheSheet() {
        BONES.forEach((name, bone) -> {
            if (!bone.has("cubes")) {
                return;
            }
            for (JsonElement e : bone.getAsJsonArray("cubes")) {
                JsonObject cube = e.getAsJsonObject();
                assertFalse(cube.has("rotation") || cube.has("pivot"), name + ": a rotated cube -- turn a bone instead");
                assertTrue(cube.get("uv").isJsonArray(), name + ": per-face UV");
                double[] size = vec(cube.getAsJsonArray("size"));
                for (double s : size) {
                    assertTrue(s >= 1 && s == Math.rint(s), name + ": cube size " + s + " is not a whole number >= 1");
                }
                double[] uv = vec(cube.getAsJsonArray("uv"));
                assertTrue(uv[0] + 2 * size[2] + 2 * size[0] <= texW && uv[1] + size[2] + size[1] <= texH,
                        name + ": box-UV island runs off the " + texW + "x" + texH + " sheet");
            }
        });
    }

    @Test
    void everyVariantStaysInItsBandAndMeetsThePlanes() {
        for (WandModule module : WandModuleRegistry.getAllForSlot(WandSlot.HANDLE)) {
            Bounds b = posed(module.boneName());
            assertTrue(b.maxY <= HANDLE_TOP + EPS, module.boneName() + " reaches past y 11");
            Bounds top = posedTouching(module.boneName(), HANDLE_TOP, true);
            assertTrue(top.minX <= -0.75 + EPS && top.maxX >= 0.75 - EPS && top.minZ <= -0.75 + EPS && top.maxZ >= 0.75 - EPS,
                    module.boneName() + ": top face does not cover the shaft's 1.5 base");
        }
        for (WandModule module : WandModuleRegistry.getAllForSlot(WandSlot.SHAFT)) {
            Bounds b = posed(module.boneName());
            assertTrue(b.minY >= HANDLE_TOP - EPS && b.maxY <= SHAFT_TOP + EPS, module.boneName() + " leaves y 11..23");
            Bounds base = posedTouching(module.boneName(), HANDLE_TOP, false);
            assertTrue(Math.abs(base.minX + 0.75) < EPS && Math.abs(base.maxX - 0.75) < EPS
                            && Math.abs(base.minZ + 0.75) < EPS && Math.abs(base.maxZ - 0.75) < EPS,
                    module.boneName() + ": base is not the 1.5 section on the axis");
            Bounds top = posedTouching(module.boneName(), SHAFT_TOP, true);
            assertTrue(top.minX >= -0.75 - EPS && top.maxX <= 0.75 + EPS && top.minX <= -0.5 + EPS && top.maxX >= 0.5 - EPS
                            && top.minZ >= -0.75 - EPS && top.maxZ <= 0.75 + EPS && top.minZ <= -0.5 + EPS && top.maxZ >= 0.5 - EPS,
                    module.boneName() + ": top is off the axis or outside 1.0..1.5");
        }
        for (WandModule module : WandModuleRegistry.getAllForSlot(WandSlot.TIP)) {
            Bounds b = posed(module.boneName());
            assertTrue(b.minY >= SHAFT_TOP - EPS, module.boneName() + " reaches below y 23");
            Bounds base = posedTouching(module.boneName(), SHAFT_TOP, false);
            assertTrue(base.minX <= -0.5 + EPS && base.maxX >= 0.5 - EPS, module.boneName() + ": no 1.0 neck on the joint");
        }
    }

    @Test
    void theSpellAttachmentIsOwnedByNoVariantInEitherWand() throws IOException {
        for (String model : new String[]{"wand", "elder_wand"}) {
            JsonObject attachment = null;
            for (JsonElement e : geometry(model).getAsJsonArray("bones")) {
                if (e.getAsJsonObject().get("name").getAsString().equals("wand_spell_attachment")) {
                    attachment = e.getAsJsonObject();
                }
            }
            assertNotNull(attachment, model + " has no wand_spell_attachment");
            assertEquals("wand_root", attachment.get("parent").getAsString(), model + ": attachment must hang off wand_root");
            assertArrayEquals(ATTACHMENT, vec(attachment.getAsJsonArray("pivot")), EPS, model + ": attachment moved");
            assertFalse(attachment.has("rotation") || attachment.has("cubes"), model + ": attachment carries geometry");
        }
    }

    @Test
    void theClipsWandItemPlaysExistAndAnimateRealBones() throws IOException {
        JsonObject clips = JsonParser.parseString(Files.readString(WAND_ANIM)).getAsJsonObject().getAsJsonObject("animations");
        for (String clip : new String[]{"animation.wand.cast", "animation.wand.charge"}) {
            assertTrue(clips.has(clip), "wand.animation.json lacks " + clip);
            for (String bone : clips.getAsJsonObject(clip).getAsJsonObject("bones").keySet()) {
                assertTrue(BONES.containsKey(bone), clip + " animates missing bone " + bone);
            }
        }
    }

    // ── posed measurement (GeckoLib's bone sense, as tools/beast_preview.py models it) ─────────────

    private record Bounds(double minX, double maxX, double minY, double maxY, double minZ, double maxZ) {
        static Bounds of(List<double[]> pts) {
            double[] lo = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
            double[] hi = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
            for (double[] p : pts) {
                for (int i = 0; i < 3; i++) {
                    lo[i] = Math.min(lo[i], p[i]);
                    hi[i] = Math.max(hi[i], p[i]);
                }
            }
            return new Bounds(lo[0], hi[0], lo[1], hi[1], lo[2], hi[2]);
        }
    }

    /** Corners of every cube in the variant's subtree, rest rotations applied. */
    private static List<List<double[]>> cubesOf(String variant) {
        List<List<double[]>> out = new ArrayList<>();
        collect(variant, out);
        return out;
    }

    private static void collect(String name, List<List<double[]>> out) {
        JsonObject bone = BONES.get(name);
        if (bone.has("cubes")) {
            for (JsonElement e : bone.getAsJsonArray("cubes")) {
                JsonObject cube = e.getAsJsonObject();
                double[] o = vec(cube.getAsJsonArray("origin"));
                double[] s = vec(cube.getAsJsonArray("size"));
                double g = cube.has("inflate") ? cube.get("inflate").getAsDouble() : 0.0;
                List<double[]> corners = new ArrayList<>();
                for (int i = 0; i < 8; i++) {
                    double[] p = {
                            (i & 1) == 0 ? o[0] - g : o[0] + s[0] + g,
                            (i & 2) == 0 ? o[1] - g : o[1] + s[1] + g,
                            (i & 4) == 0 ? o[2] - g : o[2] + s[2] + g};
                    corners.add(toModel(name, p));
                }
                out.add(corners);
            }
        }
        for (String child : CHILDREN.getOrDefault(name, List.of())) {
            collect(child, out);
        }
    }

    /** A point in {@code bone}'s frame carried up through every ancestor's pivot rotation. */
    private static double[] toModel(String boneName, double[] p) {
        double[] q = p.clone();
        for (String name = boneName; name != null; ) {
            JsonObject bone = BONES.get(name);
            if (bone.has("rotation")) {
                double[] r = vec(bone.getAsJsonArray("rotation"));
                double[] pivot = vec(bone.getAsJsonArray("pivot"));
                double[] d = {q[0] - pivot[0], q[1] - pivot[1], q[2] - pivot[2]};
                d = rotX(d, -r[0]);
                d = rotY(d, r[1]);
                d = rotZ(d, -r[2]);
                q = new double[]{d[0] + pivot[0], d[1] + pivot[1], d[2] + pivot[2]};
            }
            name = bone.has("parent") ? bone.get("parent").getAsString() : null;
        }
        return q;
    }

    private static Bounds posed(String variant) {
        List<double[]> all = new ArrayList<>();
        cubesOf(variant).forEach(all::addAll);
        return Bounds.of(all);
    }

    /** Bounds of the cubes whose top (or bottom) sits on {@code plane}. */
    private static Bounds posedTouching(String variant, double plane, boolean top) {
        List<double[]> all = new ArrayList<>();
        for (List<double[]> cube : cubesOf(variant)) {
            Bounds b = Bounds.of(cube);
            if (Math.abs((top ? b.maxY : b.minY) - plane) < EPS) {
                all.addAll(cube);
            }
        }
        assertFalse(all.isEmpty(), variant + ": nothing sits on the y " + plane + " plane");
        return Bounds.of(all);
    }

    private static double[] rotX(double[] v, double deg) {
        double c = Math.cos(Math.toRadians(deg)), s = Math.sin(Math.toRadians(deg));
        return new double[]{v[0], c * v[1] - s * v[2], s * v[1] + c * v[2]};
    }

    private static double[] rotY(double[] v, double deg) {
        double c = Math.cos(Math.toRadians(deg)), s = Math.sin(Math.toRadians(deg));
        return new double[]{c * v[0] + s * v[2], v[1], -s * v[0] + c * v[2]};
    }

    private static double[] rotZ(double[] v, double deg) {
        double c = Math.cos(Math.toRadians(deg)), s = Math.sin(Math.toRadians(deg));
        return new double[]{c * v[0] - s * v[1], s * v[0] + c * v[1], v[2]};
    }

    private static double[] vec(JsonArray a) {
        double[] out = new double[a.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = a.get(i).getAsDouble();
        }
        return out;
    }
}
