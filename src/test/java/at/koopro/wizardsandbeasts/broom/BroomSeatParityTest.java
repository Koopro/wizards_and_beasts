package at.koopro.wizardsandbeasts.broom;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Holds each broom's authored seat against the shaft it actually draws.
 *
 * <p>The seat is a number in JSON and the shaft is a cube in a geometry file, and nothing but this
 * test connects them. Widening a shaft variant without re-seating the brooms that use it produces a
 * rider sunk into the handle; narrowing one produces a rider hovering above it. Neither throws,
 * neither logs, and both look like a modelling mistake rather than a data one.
 *
 * <p>The arithmetic is the same two numbers {@link BroomSeat} documents: the top surface of the
 * shaft chain's {@code _mid} segment — the piece that passes under the rider at {@code z ≈ 0} — minus
 * the 0.75 blocks a rendered humanoid's hip pivot sits above its own position.
 */
class BroomSeatParityTest {

    private static final Gson GSON = new Gson();
    private static final Path GEO = Path.of("src", "main", "resources", "assets", "wizards_and_beasts",
            "geckolib", "models", "entity", "broom.geo.json");
    private static final Path DEFINITIONS = Path.of("src", "main", "resources", "data",
            "wizards_and_beasts", "broom_definitions");

    /** Height of a rendered humanoid's hip pivot above its own position. */
    private static final double HIP_HEIGHT = 0.75;
    /** One model unit. Seats land on unit boundaries, so exact comparison is the right tolerance. */
    private static final double EPSILON = 1.0e-9;

    @Test
    void everyBroomSitsOnTopOfItsOwnShaft() throws IOException {
        Map<String, Double> shaftTops = shaftTopsByVariant();
        assertFalse(shaftTops.isEmpty(), "no shaft chains found in broom.geo.json");

        try (var files = Files.list(DEFINITIONS)) {
            var jsons = files.filter(f -> f.toString().endsWith(".json")).sorted().toList();
            assertFalse(jsons.isEmpty(), "no broom definitions found");

            for (Path file : jsons) {
                String name = file.getFileName().toString();
                JsonObject json = GSON.fromJson(Files.readString(file), JsonObject.class);

                JsonObject slots = json.getAsJsonObject("model_slots");
                assertNotNull(slots, name + " has no model_slots");
                String variant = slots.get("shaft").getAsString().split(":")[1];

                Double top = shaftTops.get(variant);
                assertNotNull(top, name + " selects shaft '" + variant + "', which has no _mid segment");

                JsonArray offset = json.getAsJsonArray("passengerOffset");
                assertNotNull(offset, name + " does not author passengerOffset. Every shipped broom "
                        + "states its seat explicitly, so the number is visible rather than inherited.");

                double expected = top - HIP_HEIGHT;
                assertEquals(expected, offset.get(1).getAsDouble(), EPSILON,
                        name + " seats its rider at " + offset.get(1).getAsDouble() + ", but its '"
                                + variant + "' shaft has its top surface at " + top + " blocks, so the "
                                + "seat should be " + expected + ". A rider sitting below that is inside "
                                + "the handle; above it, hovering over it.");
            }
        }
    }

    /**
     * The two brooms the acceptance criterion names must genuinely differ.
     *
     * <p>Asserted separately from the arithmetic above, because that check would still pass if every
     * shaft in the rig were the same thickness — which is exactly the state the per-broom pass was
     * written to get out of.
     */
    @Test
    void aHeavyShaftSeatsHigherThanANeedle() throws IOException {
        Map<String, Double> tops = shaftTopsByVariant();
        Double heavy = tops.get("heavy_oak");
        Double racing = tops.get("racing");
        assertNotNull(heavy, "no heavy_oak shaft in the rig");
        assertNotNull(racing, "no racing shaft in the rig");
        assertTrue(heavy > racing,
                "the Oakshaft's shaft must be thicker than the Firebolt's, or one seat height would "
                        + "serve both and the per-broom seat would be pointless");
    }

    /** Top surface, in blocks, of each shaft variant's {@code _mid} segment. */
    private static Map<String, Double> shaftTopsByVariant() throws IOException {
        JsonObject geometry = GSON.fromJson(Files.readString(GEO), JsonObject.class)
                .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();

        Map<String, Double> tops = new HashMap<>();
        geometry.getAsJsonArray("bones").forEach(element -> {
            JsonObject bone = element.getAsJsonObject();
            String boneName = bone.get("name").getAsString();
            if (!boneName.startsWith("shaft_") || !boneName.endsWith("_mid")) return;
            if (!bone.has("cubes")) return;

            JsonObject cube = bone.getAsJsonArray("cubes").get(0).getAsJsonObject();
            double top = cube.getAsJsonArray("origin").get(1).getAsDouble()
                    + cube.getAsJsonArray("size").get(1).getAsDouble();
            tops.put(boneName.substring("shaft_".length(), boneName.length() - "_mid".length()),
                    top / 16.0);
        });
        return tops;
    }
}
