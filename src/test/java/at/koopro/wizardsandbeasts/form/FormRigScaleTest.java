package at.koopro.wizardsandbeasts.form;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A rig-drawn form must render at the height of the box it collides with.
 *
 * <p>{@code modelScale} means two different things depending on how a form is drawn, and nothing in
 * the type says which. For a form drawn as a scaled <em>player</em> model it is a fraction of a
 * 1.8-block human. For a form drawn from a GeckoLib rig it is a unit conversion —
 * {@code hitboxHeight / rigHeight} — because the rig is authored to its own mob's box, not to a human.
 *
 * <p>Three of the five rig-drawn forms carried the human-fraction reading against rigs that are not
 * 1.8 blocks tall, so they rendered at the wrong size inside a correct hitbox: the werewolf 15% over
 * its box with its head outside it, the centaur 18% under and sunk into the floor. Nothing catches
 * that at runtime; it just looks slightly wrong forever. So it is checked here against the rigs
 * themselves, which is the only source of truth for how tall a rig actually is.
 */
class FormRigScaleTest {

    private static final Path GEO_DIR = Path.of("src", "main", "resources", "assets",
            "wizards_and_beasts", "geckolib", "models", "entity");

    /**
     * Form id → the rig asset it draws, mirroring {@code PlayerFormRig.BY_FORM_ID}, and the size
     * profile that form resolves to (they are not always the same id — {@code merfolk_water} uses
     * profile {@code merpeople_water}, which {@code FormRegistry} states explicitly).
     */
    private static final Map<String, String[]> RIG_FORMS = new LinkedHashMap<>();

    static {
        RIG_FORMS.put("werewolf_wolf", new String[]{"werewolf", "werewolf_wolf"});
        RIG_FORMS.put("centaur_default", new String[]{"centaur", "centaur_default"});
        RIG_FORMS.put("goblin_default", new String[]{"goblin_teller", "goblin_default"});
        RIG_FORMS.put("obscurial_dark", new String[]{"obscurus", "obscurial_dark"});
    }

    /** Height of a GeckoLib rig in blocks, from the extent of its cubes. 16 model units per block. */
    private static double rigHeightBlocks(String asset) throws IOException {
        Path path = GEO_DIR.resolve(asset + ".geo.json");
        assertTrue(Files.exists(path), "rig " + asset + ".geo.json is missing");
        JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        JsonArray bones = root.getAsJsonArray("minecraft:geometry").get(0)
                .getAsJsonObject().getAsJsonArray("bones");

        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (int i = 0; i < bones.size(); i++) {
            JsonObject bone = bones.get(i).getAsJsonObject();
            if (!bone.has("cubes")) {
                continue;
            }
            JsonArray cubes = bone.getAsJsonArray("cubes");
            for (int c = 0; c < cubes.size(); c++) {
                JsonObject cube = cubes.get(c).getAsJsonObject();
                double originY = cube.getAsJsonArray("origin").get(1).getAsDouble();
                double sizeY = cube.getAsJsonArray("size").get(1).getAsDouble();
                min = Math.min(min, originY);
                max = Math.max(max, originY + sizeY);
            }
        }
        assertTrue(max > min, "rig " + asset + " has no cubes to measure");
        return (max - min) / 16.0;
    }

    @Test
    void everyRigDrawnFormRendersAtTheHeightOfItsHitbox() throws IOException {
        for (Map.Entry<String, String[]> entry : RIG_FORMS.entrySet()) {
            String formId = entry.getKey();
            String asset = entry.getValue()[0];
            String profileId = entry.getValue()[1];

            SizeProfile profile = SizeProfileRegistry.get(profileId);
            assertTrue(profile != null, formId + " resolves to no size profile (" + profileId + ")");

            double rigHeight = rigHeightBlocks(asset);
            double rendered = rigHeight * profile.modelScale();
            double box = profile.hitboxHeight();

            // 5%: rigs are hand-sculpted and a couple of model units either way is not a defect.
            assertEquals(box, rendered, box * 0.05,
                    String.format("%s renders %.2f blocks inside a %.2f-block hitbox — modelScale "
                                    + "should be hitboxHeight/rigHeight = %.3f, not %.3f. modelScale is a "
                                    + "rig-unit conversion for rig-drawn forms, not a fraction of 1.8.",
                            formId, rendered, box, box / rigHeight, profile.modelScale()));
        }
    }
}
