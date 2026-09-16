package at.koopro.wizardsandbeasts.creature;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every creature definition ships a finished, internally consistent GeckoLib rig.
 *
 * <p>This replaced {@code RigMarkerConsistencyTest} when the art backlog it tracked reached zero on
 * 2026-09-16. That test kept the {@code "PLACEHOLDER box rig"} marker honest in both directions and
 * asked, in its own javadoc, to be retired once no rig carried the marker. The regression it was
 * really guarding against is still possible — {@code tools/creature_gen.py} re-emits the marker into
 * the definition and the animation of anything it regenerates — so the marker check survives here as
 * an absence check.
 *
 * <p>Its shape heuristic did not survive. A generated box rig was "cube-less {@code root}, one cube per
 * other bone, nine cubes or fewer", and that stopped being a signature once finished rigs could be
 * simple: the Ashwinder, Flobberworm, Boggart and Lethifold are chains that match it exactly.
 *
 * <p>What replaces it is the checks {@code AlphaRosterAssetTest} already ran on its 21 creatures,
 * run on all of them: the three assets exist, the clips the locomotion class binds and the clips the
 * definition declares exist, no clip animates a bone the rig lacks (GeckoLib drops those tracks
 * without a word), and the declared sheet matches the skin. Pure file I/O, no Minecraft bootstrap.
 */
class CreatureRigGuardTest {

    private static final Gson GSON = new Gson();

    private static final String MARKER = "PLACEHOLDER box rig";

    private static final Path ASSETS = Path.of("src", "main", "resources", "assets", "wizards_and_beasts");
    private static final Path CREATURES = Path.of("src", "main", "resources", "data", "wizards_and_beasts", "creatures");
    private static final Path RIGS = ASSETS.resolve(Path.of("geckolib", "models", "entity"));
    private static final Path ANIMS = ASSETS.resolve(Path.of("geckolib", "animations", "entity"));
    private static final Path SKINS = ASSETS.resolve(Path.of("textures", "entity"));

    /** The clip each locomotion class's movement controller binds besides {@code idle}. */
    private static final Map<String, String> MOVEMENT_CLIP = Map.of(
            "GROUND", "walk", "FLYING", "fly", "AQUATIC", "swim");

    @Test
    void noPlaceholderMarkerRemains() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Path directory : List.of(CREATURES, ANIMS, RIGS)) {
            try (var files = Files.list(directory)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                    if (Files.readString(file).contains(MARKER)) {
                        problems.add(file + " carries the placeholder marker — was tools/creature_gen.py run over "
                                + "a finished rig? Regenerate it with its tools/<id>_model.py instead");
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void everyCreatureShipsAConsistentRig() throws IOException {
        List<String> problems = new ArrayList<>();
        List<Path> definitions;
        try (var files = Files.list(CREATURES)) {
            definitions = files.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        assertFalse(definitions.isEmpty(), "expected creature definitions on disk");

        for (Path path : definitions) {
            JsonObject definition = GSON.fromJson(Files.readString(path), JsonObject.class);
            String id = path.getFileName().toString().replace(".json", "");
            String model = definition.get("model").getAsString();
            String asset = model.substring(model.lastIndexOf('/') + 1);

            Path rig = RIGS.resolve(asset + ".geo.json");
            Path anim = ANIMS.resolve(asset + ".animation.json");
            Path skin = SKINS.resolve(asset + ".png");
            List<Path> missing = new ArrayList<>();
            for (Path p : List.of(rig, anim, skin)) {
                if (!Files.exists(p)) {
                    missing.add(p);
                }
            }
            if (!missing.isEmpty()) {
                problems.add(id + ": missing " + missing);
                continue;
            }

            JsonObject geometry = GSON.fromJson(Files.readString(rig), JsonObject.class)
                    .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
            Set<String> bones = new TreeSet<>();
            for (var bone : geometry.getAsJsonArray("bones")) {
                bones.add(bone.getAsJsonObject().get("name").getAsString());
            }

            JsonObject animations = GSON.fromJson(Files.readString(anim), JsonObject.class)
                    .getAsJsonObject("animations");
            Set<String> clips = new TreeSet<>();
            Set<String> dangling = new TreeSet<>();
            for (var entry : animations.entrySet()) {
                clips.add(entry.getKey().substring(entry.getKey().lastIndexOf('.') + 1));
                JsonObject clipBones = entry.getValue().getAsJsonObject().getAsJsonObject("bones");
                if (clipBones != null) {
                    for (String bone : clipBones.keySet()) {
                        if (!bones.contains(bone)) {
                            dangling.add(bone);
                        }
                    }
                }
            }

            Set<String> required = new TreeSet<>();
            required.add("idle");
            String movement = MOVEMENT_CLIP.get(definition.get("locomotion").getAsString());
            if (movement != null) {
                required.add(movement);
            }
            if (definition.has("clips")) {
                definition.getAsJsonArray("clips").forEach(c -> required.add(c.getAsString()));
            }
            required.removeAll(clips);
            if (!required.isEmpty()) {
                problems.add(id + ": " + anim + " lacks clips " + required);
            }
            if (!dangling.isEmpty()) {
                problems.add(id + ": animations drive bones the rig does not have " + dangling);
            }

            BufferedImage png = ImageIO.read(skin.toFile());
            JsonObject description = geometry.getAsJsonObject("description");
            int declaredW = description.get("texture_width").getAsInt();
            int declaredH = description.get("texture_height").getAsInt();
            if (png == null || png.getWidth() != declaredW || png.getHeight() != declaredH) {
                problems.add(id + ": rig declares a " + declaredW + "x" + declaredH + " sheet but the skin is "
                        + (png == null ? "unreadable" : png.getWidth() + "x" + png.getHeight()));
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }
}
