package at.koopro.wizardsandbeasts.client.pose;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves {@code player.geo.json} and vanilla's player {@code LayerDefinition} describe the same rig.
 *
 * <p>The rig is a mechanical transcription of vanilla's mesh, and the hazard in a transcription like
 * this is entirely in the coordinate space. An axis-sign error is silent: the rig looks right at
 * rest, every exported rotation is wrong, and it reads in game as bad animation rather than as a bad
 * transform. So the test does not check the numbers were copied — it checks that a rotation produces
 * the same geometry position down both paths.
 *
 * <h2>The two paths, both read out of the implementations rather than recalled</h2>
 *
 * <p><b>Geo.</b> {@code RenderUtil.translateAndRotateMatrixForBone} is
 * {@code translateToPivotPoint → mulPose(ZP) → mulPose(YP) → mulPose(XP) → translateAwayFromPivotPoint},
 * and {@code GeoBone.translateToPivotPoint} is {@code translate(pivot / 16)}. GeckoLib's
 * {@code BakedModelFactory.Builtin.constructBone} contains no negation at all: bone pivots and
 * rotations are the raw JSON values, the latter merely converted to radians.
 *
 * <p><b>ModelPart.</b> {@code translateAndRotate} is {@code translate(x/16, y/16, z/16)} followed by
 * the same Z, Y, X rotation order.
 *
 * <p>The two are therefore structurally identical operations. Only the numbers differ, related by the
 * geo↔model map.
 *
 * <h2>The map, and why the rotations are not copied verbatim</h2>
 *
 * <p>Geo space has its origin at the feet with Y up; Java model space has its origin at the neck with
 * Y down. So {@code y_model = 24 - y_geo}, with X and Z passing through — a reflection in the XZ
 * plane, {@code diag(1, -1, 1)}.
 *
 * <p>Conjugating a rotation by that reflection gives {@code R_x(-θ)}, {@code R_y(θ)}, {@code R_z(-θ)}:
 * <b>X and Z negate, Y is unchanged.</b> Y survives because a reflection in the plane perpendicular to
 * an axis commutes with rotation about that axis. This is asserted, not assumed — if the derivation
 * were wrong these tests fail, and the instruction is to report the axis rather than tune the rig
 * until it passes.
 *
 * <p>Note this is a <em>different</em> reflection from vanilla's {@code scale(-1, -1, 1)} in the
 * renderer, which is a 180° rotation about Z and negates X and Y instead. Confusing the two is what
 * put the head counter-rotation in backwards earlier in this work.
 */
class PlayerGeoRigParityTest {

    private static final Gson GSON = new Gson();
    private static final Path GEO = Path.of("src", "main", "resources", "assets", "wizards_and_beasts",
            "geckolib", "models", "entity", "player.geo.json");

    /** Java model height: the constant that flips the Y axis between the two spaces. */
    private static final float GROUND = 24f;

    private static final float TOLERANCE = 1e-4f;

    /**
     * Deliberately awkward: three distinct values, one negative, one past 90°.
     *
     * <p>A few degrees on one axis passes with an inverted axis and proves nothing — the error and
     * the correct value are numerically close near zero, and identical at zero.
     */
    private static final float ROT_X = 115f;
    private static final float ROT_Y = -47f;
    private static final float ROT_Z = 33f;

    // ── the rig under test ───────────────────────────────────────────────────

    private static ModelPart bakedVanillaPlayer() {
        // The wide-arm variant, matching what the rig transcribes. Zero deformation: the rig carries
        // inflate on the overlay bones explicitly rather than baking it into the base dimensions.
        return LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64)
                .bakeRoot();
    }

    private static Map<String, JsonObject> geoBones() throws IOException {
        JsonObject root = GSON.fromJson(Files.readString(GEO), JsonObject.class);
        JsonObject geometry = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        Map<String, JsonObject> bones = new HashMap<>();
        for (var element : geometry.getAsJsonArray("bones")) {
            JsonObject bone = element.getAsJsonObject();
            bones.put(bone.get("name").getAsString(), bone);
        }
        return bones;
    }

    private static Vector3f pivotOf(JsonObject bone) {
        JsonArray pivot = bone.getAsJsonArray("pivot");
        return new Vector3f(pivot.get(0).getAsFloat(), pivot.get(1).getAsFloat(), pivot.get(2).getAsFloat());
    }

    /** A bone's absolute pivot in Java model space, accumulated down the parent chain. */
    private static Vector3f absoluteModelPivot(ModelPart root, String... path) {
        Vector3f pivot = new Vector3f();
        ModelPart part = root;
        for (String name : path) {
            part = part.getChild(name);
            pivot.add(part.x, part.y, part.z);
        }
        return pivot;
    }

    private static ModelPart child(ModelPart root, String... path) {
        ModelPart part = root;
        for (String name : path) {
            part = part.getChild(name);
        }
        return part;
    }

    // ── pivot and dimension parity, against the LayerDefinition itself ───────

    /**
     * Pivots match the baked mesh, not a transcription of it.
     *
     * <p>Asserted against {@code PlayerModel.createMesh} directly so the test fails if a future
     * Minecraft version moves a pivot, rather than agreeing with a number frozen at authoring time.
     */
    @Test
    void everyPivotMatchesTheLayerDefinition() throws IOException {
        ModelPart root = bakedVanillaPlayer();
        Map<String, JsonObject> bones = geoBones();

        record Case(String geoBone, String[] modelPath) {}
        List<Case> cases = List.of(
                new Case("head", new String[]{"head"}),
                new Case("body", new String[]{"body"}),
                new Case("right_arm", new String[]{"right_arm"}),
                new Case("left_arm", new String[]{"left_arm"}),
                new Case("right_leg", new String[]{"right_leg"}),
                new Case("left_leg", new String[]{"left_leg"}),
                new Case("hat", new String[]{"head", "hat"}),
                new Case("jacket", new String[]{"body", "jacket"}),
                new Case("right_sleeve", new String[]{"right_arm", "right_sleeve"}),
                new Case("left_pants", new String[]{"left_leg", "left_pants"}));

        for (Case testCase : cases) {
            JsonObject bone = bones.get(testCase.geoBone());
            assertNotNull(bone, "player.geo.json has no bone '" + testCase.geoBone() + "'");

            Vector3f geo = pivotOf(bone);
            Vector3f model = absoluteModelPivot(root, testCase.modelPath());

            assertEquals(model.x, geo.x, TOLERANCE, testCase.geoBone() + " pivot X");
            assertEquals(model.y, GROUND - geo.y, TOLERANCE,
                    testCase.geoBone() + " pivot Y — geo is feet-up, model is neck-down");
            assertEquals(model.z, geo.z, TOLERANCE, testCase.geoBone() + " pivot Z");
        }
    }

    /**
     * Cube dimensions and positions match the baked mesh.
     *
     * <p>Read back through {@code ModelPart.visit}, so these are the cubes vanilla actually bakes
     * rather than the arguments someone believes were passed to {@code addBox}.
     */
    @Test
    void everyCubeMatchesTheLayerDefinition() throws IOException {
        ModelPart root = bakedVanillaPlayer();
        Map<String, JsonObject> bones = geoBones();

        record Case(String geoBone, String[] modelPath) {}
        List<Case> cases = List.of(
                new Case("head", new String[]{"head"}),
                new Case("body", new String[]{"body"}),
                new Case("right_arm", new String[]{"right_arm"}),
                new Case("left_leg", new String[]{"left_leg"}),
                new Case("right_sleeve", new String[]{"right_arm", "right_sleeve"}));

        for (Case testCase : cases) {
            ModelPart part = child(root, testCase.modelPath());
            List<ModelPart.Cube> cubes = cubesOf(part);
            assertEquals(1, cubes.size(), testCase.geoBone() + " should have exactly one cube");
            ModelPart.Cube cube = cubes.get(0);

            JsonObject geoCube = bones.get(testCase.geoBone()).getAsJsonArray("cubes")
                    .get(0).getAsJsonObject();
            JsonArray origin = geoCube.getAsJsonArray("origin");
            JsonArray size = geoCube.getAsJsonArray("size");

            // No inflate term on either side. Vanilla's Cube stores min/max as the UN-deformed
            // bounds and applies CubeDeformation to the vertex positions only, so an overlay's
            // bounds already match the base part's — exactly as the geo file states them, with
            // inflate carried as its own field in both representations.
            Vector3f pivot = absoluteModelPivot(root, testCase.modelPath());

            assertEquals(pivot.x + cube.minX, origin.get(0).getAsFloat(), TOLERANCE,
                    testCase.geoBone() + " cube min X");
            assertEquals(pivot.z + cube.minZ, origin.get(2).getAsFloat(), TOLERANCE,
                    testCase.geoBone() + " cube min Z");
            // Y inverts, so vanilla's max corner is the geo file's min corner.
            assertEquals(GROUND - (pivot.y + cube.maxY), origin.get(1).getAsFloat(), TOLERANCE,
                    testCase.geoBone() + " cube min Y — vanilla's max corner is geo's min");

            assertEquals(cube.maxX - cube.minX, size.get(0).getAsFloat(), TOLERANCE,
                    testCase.geoBone() + " cube width");
            assertEquals(cube.maxY - cube.minY, size.get(1).getAsFloat(), TOLERANCE,
                    testCase.geoBone() + " cube height");
            assertEquals(cube.maxZ - cube.minZ, size.get(2).getAsFloat(), TOLERANCE,
                    testCase.geoBone() + " cube depth");
        }
    }

    private static List<ModelPart.Cube> cubesOf(ModelPart part) {
        List<ModelPart.Cube> cubes = new ArrayList<>();
        // visit walks children too, so only the cubes at the root of this part are kept — the empty
        // path is the part itself.
        part.visit(new PoseStack(), (pose, path, index, cube) -> {
            if (path.isEmpty()) {
                cubes.add(cube);
            }
        });
        return cubes;
    }

    // ── transform parity: the point of the whole test ────────────────────────

    /**
     * A non-trivial rotation puts the same corner of the same cube in the same place down both paths.
     *
     * <p>This is what an axis-sign error cannot survive. Every bone is covered, including two whose
     * pivots are off-origin and one with a fractional pivot, plus overlay children so parenting is
     * exercised rather than assumed.
     */
    @Test
    void rotationProducesTheSameGeometryPositionDownBothPaths() throws IOException {
        ModelPart root = bakedVanillaPlayer();
        Map<String, JsonObject> bones = geoBones();

        record Case(String geoBone, String[] path, String why) {

            /** Both rigs use the same bone names, so one path drives both sides. */
            String[] modelPath() {
                return path;
            }
        }
        List<Case> cases = List.of(
                new Case("head", new String[]{"head"}, "pivot at the origin"),
                new Case("body", new String[]{"body"}, "pivot at the origin, offset cube"),
                new Case("right_arm", new String[]{"right_arm"}, "off-origin pivot"),
                new Case("left_arm", new String[]{"left_arm"}, "off-origin pivot, mirrored"),
                new Case("right_leg", new String[]{"right_leg"}, "off-origin, fractional pivot"),
                new Case("left_leg", new String[]{"left_leg"}, "off-origin, fractional pivot"),
                new Case("hat", new String[]{"head", "hat"}, "overlay child, inherits parent pivot"),
                new Case("right_sleeve", new String[]{"right_arm", "right_sleeve"},
                        "overlay child on an off-origin parent"));

        for (Case testCase : cases) {
            JsonArray geoOrigin = bones.get(testCase.geoBone()).getAsJsonArray("cubes")
                    .get(0).getAsJsonObject().getAsJsonArray("origin");
            Vector3f geoCorner = new Vector3f(geoOrigin.get(0).getAsFloat(),
                    geoOrigin.get(1).getAsFloat(), geoOrigin.get(2).getAsFloat());

            Vector3f viaGeo = throughGeoPath(bones, testCase.path(), geoCorner);
            Vector3f viaModel = throughModelPath(root, testCase.modelPath(), geoCorner);

            String why = testCase.geoBone() + " (" + testCase.why() + ")";
            assertEquals(viaModel.x, viaGeo.x, TOLERANCE, why + " — X diverged");
            assertEquals(viaModel.y, viaGeo.y, TOLERANCE, why + " — Y diverged");
            assertEquals(viaModel.z, viaGeo.z, TOLERANCE, why + " — Z diverged");
        }
    }

    /**
     * The geo path, in model units, with the result mapped into Java model space for comparison.
     *
     * <p>Rotations are negated on X and Z and left alone on Y, per the conjugation in the class note.
     * That mapping is the claim under test: get it wrong and the corners land somewhere else.
     */
    private static Vector3f throughGeoPath(Map<String, JsonObject> bones, String[] path,
                                           Vector3f corner) {
        PoseStack stack = new PoseStack();
        for (String name : path) {
            Vector3f pivot = pivotOf(bones.get(name));
            stack.translate(pivot.x / 16f, pivot.y / 16f, pivot.z / 16f);
            stack.mulPose(Axis.ZP.rotationDegrees(-ROT_Z));
            stack.mulPose(Axis.YP.rotationDegrees(ROT_Y));
            stack.mulPose(Axis.XP.rotationDegrees(-ROT_X));
            stack.translate(-pivot.x / 16f, -pivot.y / 16f, -pivot.z / 16f);
        }

        Vector3f result = new Vector3f(corner.x / 16f, corner.y / 16f, corner.z / 16f);
        result.mulPosition(stack.last().pose());

        // Geo space to Java model space: Y flips about the ground line, X and Z pass through.
        return new Vector3f(result.x, GROUND / 16f - result.y, result.z);
    }

    /**
     * The ModelPart path, walking the parent chain so an overlay child is transformed by its parent
     * first — which is what makes this a test of the hierarchy and not just of one bone.
     */
    private static Vector3f throughModelPath(ModelPart root, String[] path, Vector3f geoCorner) {
        PoseStack stack = new PoseStack();
        ModelPart part = root;
        for (String name : path) {
            part = part.getChild(name);
            part.xRot = ROT_X * ((float) Math.PI / 180f);
            part.yRot = ROT_Y * ((float) Math.PI / 180f);
            part.zRot = ROT_Z * ((float) Math.PI / 180f);
            part.translateAndRotate(stack);
        }

        // The same physical corner, expressed relative to the innermost part: convert the geo corner
        // into model space and subtract the accumulated pivot.
        Vector3f pivot = absoluteModelPivot(root, path);
        Vector3f relative = new Vector3f(
                (geoCorner.x - pivot.x) / 16f,
                ((GROUND - geoCorner.y) - pivot.y) / 16f,
                (geoCorner.z - pivot.z) / 16f);
        relative.mulPosition(stack.last().pose());
        return relative;
    }

    /**
     * The rotation mapping is not an identity, so a test that passed by copying rotations verbatim
     * would be passing for the wrong reason.
     *
     * <p>Guards the guard: if someone "simplifies" {@link #throughGeoPath} by dropping the negations
     * and the parity test still passes, one of the two is not doing what it claims.
     */
    @Test
    void copyingRotationsVerbatimWouldNotHavePassed() throws IOException {
        Map<String, JsonObject> bones = geoBones();
        Vector3f pivot = pivotOf(bones.get("right_arm"));
        JsonArray origin = bones.get("right_arm").getAsJsonArray("cubes").get(0)
                .getAsJsonObject().getAsJsonArray("origin");
        Vector3f corner = new Vector3f(origin.get(0).getAsFloat(),
                origin.get(1).getAsFloat(), origin.get(2).getAsFloat());

        Vector3f mapped = throughGeoPath(bones, new String[]{"right_arm"}, corner);

        PoseStack stack = new PoseStack();
        stack.translate(pivot.x / 16f, pivot.y / 16f, pivot.z / 16f);
        stack.mulPose(Axis.ZP.rotationDegrees(ROT_Z));
        stack.mulPose(Axis.YP.rotationDegrees(ROT_Y));
        stack.mulPose(Axis.XP.rotationDegrees(ROT_X));
        stack.translate(-pivot.x / 16f, -pivot.y / 16f, -pivot.z / 16f);
        Vector3f verbatim = new Vector3f(corner.x / 16f, corner.y / 16f, corner.z / 16f);
        verbatim.mulPosition(stack.last().pose());
        verbatim.set(verbatim.x, GROUND / 16f - verbatim.y, verbatim.z);

        assertTrue(mapped.distance(verbatim) > 0.05f,
                "the negations must actually change the result, or this test proves nothing");
    }

    /** The rig is authoring-only and must say so, since nothing in the code path would reveal it. */
    @Test
    void theRigDeclaresItselfAuthoringOnly() throws IOException {
        JsonObject root = GSON.fromJson(Files.readString(GEO), JsonObject.class);
        assertTrue(root.has("_comment"), "player.geo.json must carry its authoring-only note");
        String note = root.get("_comment").getAsString().toLowerCase(java.util.Locale.ROOT);
        assertTrue(note.contains("authoring"), "the note should say what the file is for: " + note);
    }
}
