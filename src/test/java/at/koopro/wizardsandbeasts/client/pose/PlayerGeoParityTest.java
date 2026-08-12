package at.koopro.wizardsandbeasts.client.pose;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Holds {@code player.geo.json} to the vanilla player it is a stand-in for.
 *
 * <p>The geo is never rendered — the pose layer writes to vanilla {@code ModelPart}s at render time.
 * It exists so keyframe clips can be authored in Blockbench against the real rig. That makes drift
 * here uniquely quiet: a wrong pivot produces a clip that looks right while it is being authored and
 * swings about the wrong point in game, with nothing in between to notice.
 *
 * <p>The expected values are written out as literals rather than derived, on purpose. Deriving them
 * from the same conversion the generator uses would make this test agree with the file no matter
 * what either of them said. They are transcribed from {@code HumanoidModel.createMesh} and
 * {@code PlayerModel.createMesh} in 1.21.11, converted to Bedrock geo space by hand:
 * {@code bedrockY = 24 - javaY}, cube origins absolute rather than pivot-relative.
 */
class PlayerGeoParityTest {

    private static final Gson GSON = new Gson();
    private static final Path GEO = Path.of("src", "main", "resources", "assets", "wizards_and_beasts",
            "geckolib", "models", "entity", "player.geo.json");

    /** Vanilla's pivots, in Bedrock geo space. */
    private static final Map<String, float[]> EXPECTED_PIVOTS = new HashMap<>();

    static {
        // The whole-avatar transform. At the feet, because BODY resolves against the PoseStack and
        // the PoseStack's origin there is the entity position — not vanilla's neck-joint root.
        EXPECTED_PIVOTS.put("root", new float[]{0f, 0f, 0f});
        EXPECTED_PIVOTS.put("head", new float[]{0f, 24f, 0f});
        EXPECTED_PIVOTS.put("body", new float[]{0f, 24f, 0f});
        EXPECTED_PIVOTS.put("right_arm", new float[]{-5f, 22f, 0f});
        EXPECTED_PIVOTS.put("left_arm", new float[]{5f, 22f, 0f});
        EXPECTED_PIVOTS.put("right_leg", new float[]{-1.9f, 12f, 0f});
        EXPECTED_PIVOTS.put("left_leg", new float[]{1.9f, 12f, 0f});
    }

    @Test
    void pivotsMatchTheVanillaPlayerMesh() throws IOException {
        Map<String, JsonObject> bones = bones();
        EXPECTED_PIVOTS.forEach((name, expected) -> {
            JsonObject bone = bones.get(name);
            assertNotNull(bone, "player.geo.json has no bone '" + name + "'");
            JsonArray pivot = bone.getAsJsonArray("pivot");
            for (int axis = 0; axis < 3; axis++) {
                assertEquals(expected[axis], pivot.get(axis).getAsFloat(), 1e-4f,
                        name + " pivot axis " + axis + " does not match vanilla");
            }
        });
    }

    /**
     * Every part a pose pass can address must exist as a bone.
     *
     * <p>{@code CHEST} maps to the bone named {@code body} and {@code BODY} to {@code root} — vanilla's
     * names, kept so the rig reads the way the model it stands in for does. The mismatch lives here
     * and nowhere else.
     */
    @Test
    void everyAddressablePartHasABone() throws IOException {
        Set<String> bones = bones().keySet();
        for (PlayerModelPart part : PlayerModelPart.values()) {
            String bone = switch (part) {
                case BODY -> "root";
                case CHEST -> "body";
                default -> part.name().toLowerCase(java.util.Locale.ROOT);
            };
            assertTrue(bones.contains(bone),
                    "PlayerModelPart." + part + " has no bone '" + bone + "' in player.geo.json");
        }
    }

    /**
     * Arms and legs hang off {@code root}, never off {@code body}.
     *
     * <p>Reparenting them under the torso is the tidy-looking mistake: vanilla parents all six to the
     * root, so a clip authored against a torso-parented rig would have the torso's rotation applied
     * to it twice once the pose reached the real model.
     */
    @Test
    void limbsParentToRootNotToTheTorso() throws IOException {
        Map<String, JsonObject> bones = bones();
        for (String limb : new String[]{"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"}) {
            assertEquals("root", bones.get(limb).get("parent").getAsString(),
                    limb + " must parent to root — vanilla parents all six to the model root");
        }
    }

    /** The root carries the whole avatar, so geometry on it would draw a box the game never draws. */
    @Test
    void rootCarriesNoGeometry() throws IOException {
        assertFalse(bones().get("root").has("cubes"), "root is the virtual BODY part and must be empty");
    }

    /**
     * Feet on the ground, crown of the head at 32.
     *
     * <p>Properties of the player rather than of the conversion, so a flipped Y sign fails here even
     * if every pivot happened to be transcribed to match it.
     */
    @Test
    void modelStandsOnTheGroundAtFullHeight() throws IOException {
        float lowest = Float.MAX_VALUE;
        float highest = -Float.MAX_VALUE;
        for (JsonObject bone : bones().values()) {
            if (!bone.has("cubes")) {
                continue;
            }
            for (var element : bone.getAsJsonArray("cubes")) {
                JsonObject cube = element.getAsJsonObject();
                float y = cube.getAsJsonArray("origin").get(1).getAsFloat();
                float height = cube.getAsJsonArray("size").get(1).getAsFloat();
                lowest = Math.min(lowest, y);
                highest = Math.max(highest, y + height);
            }
        }
        assertEquals(0f, lowest, 1e-4f, "model does not stand on the ground");
        assertEquals(32f, highest, 1e-4f, "head is not at full player height");
    }

    /** Left and right must mirror, or authoring a splay as ±angle is wrong on one side. */
    @Test
    void limbsMirrorAcrossTheCentreLine() throws IOException {
        Map<String, JsonObject> bones = bones();
        for (String[] pair : new String[][]{{"right_arm", "left_arm"}, {"right_leg", "left_leg"}}) {
            JsonArray right = bones.get(pair[0]).getAsJsonArray("pivot");
            JsonArray left = bones.get(pair[1]).getAsJsonArray("pivot");
            assertEquals(-right.get(0).getAsFloat(), left.get(0).getAsFloat(), 1e-4f,
                    pair[0] + "/" + pair[1] + " are not mirrored in X");
            assertEquals(right.get(1).getAsFloat(), left.get(1).getAsFloat(), 1e-4f, "differing height");
            assertEquals(right.get(2).getAsFloat(), left.get(2).getAsFloat(), 1e-4f, "differing depth");
        }
    }

    /** Box UV puts one model unit on one texel, so a fractional size unwraps between pixels. */
    @Test
    void everyCubeSizeIsIntegral() throws IOException {
        for (Map.Entry<String, JsonObject> entry : bones().entrySet()) {
            if (!entry.getValue().has("cubes")) {
                continue;
            }
            for (var element : entry.getValue().getAsJsonArray("cubes")) {
                for (var size : element.getAsJsonObject().getAsJsonArray("size")) {
                    float value = size.getAsFloat();
                    assertEquals(Math.rint(value), value, 1e-6f,
                            entry.getKey() + " has a fractional cube size");
                }
            }
        }
    }

    /** GeckoLib resolves the model by this identifier; a rename here is a silently missing rig. */
    @Test
    void identifierIsStable() throws IOException {
        assertEquals("geometry.player", geometry().getAsJsonObject("description")
                .get("identifier").getAsString());
    }

    private static JsonObject geometry() throws IOException {
        JsonObject root = GSON.fromJson(Files.readString(GEO), JsonObject.class);
        JsonArray geometries = root.getAsJsonArray("minecraft:geometry");
        assertEquals(1, geometries.size(),
                "one geometry per file — GeckoLib bakes the first and a second would never load");
        return geometries.get(0).getAsJsonObject();
    }

    private static Map<String, JsonObject> bones() throws IOException {
        Map<String, JsonObject> bones = new HashMap<>();
        Set<String> seen = new LinkedHashSet<>();
        for (var element : geometry().getAsJsonArray("bones")) {
            JsonObject bone = element.getAsJsonObject();
            String name = bone.get("name").getAsString();
            assertTrue(seen.add(name), "duplicate bone '" + name + "'");
            bones.put(name, bone);
        }
        return bones;
    }
}
