package at.koopro.wizardsandbeasts.equipment.wardrobe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wardrobe's data, sheets and rigs agree (WARDROBE_RENAISSANCE Phase C).
 *
 * <p>A livery that names a cut with no sheet draws the missing texture on every viewer's screen,
 * and a rank or cloth bone with the wrong name or parent is silently never toggled or moved. Both
 * fail on a path no headless test reaches, so this reads the shipped files directly.
 */
class WardrobeAssetTest {

    private static final String MOD = "wizards_and_beasts";
    private static final Path ASSETS = Path.of("src", "main", "resources", "assets", MOD);
    private static final Path LIVERIES = Path.of("src", "main", "resources", "data", MOD, MOD, "livery");

    /** Cut item → (model name, default livery). Mirrors each item's {@code defaultLivery()}. */
    private static final Map<String, String[]> CUTS = Map.ofEntries(
            Map.entry("leather_belt", new String[]{"leather_belt", "leather_brown"}),
            Map.entry("duty_belt", new String[]{"duty_belt", "auror_office"}),
            Map.entry("scarf", new String[]{"scarf", "school_plain"}),
            Map.entry("gloves", new String[]{"gloves", "dragonhide"}),
            Map.entry("professor_hat", new String[]{"professor_hat", "faculty"}),
            Map.entry("travel_hat", new String[]{"travel_hat", "umber"}),
            robe(
"student_robe_chest", "student_robe", "school_plain"),
            robe("auror_robe_chest", "auror_robe", "auror_office"),
            robe("death_eater_robe_chest", "death_eater_robe", "dark_order"),
            robe("wizard_robe_chest", "wizard_robe", "slate"),
            robe("scholar_gown_chest", "scholar_gown", "faculty"),
            robe("travel_coat_chest", "travel_coat", "canvas_umber"),
            robe("healer_coat_chest", "healer_coat", "ward_green"),
            robe("ministry_robe_chest", "ministry_robe", "ministry_plum"),
            robe("master_mantle_chest", "master_mantle", "master_crimson"),
            robe("school_cape", "school_cape", "school_plain"),
            robe("travel_cloak", "travel_cloak", "umber"));

    /** Accessories: neither a skirt nor a cape, so no motion bones to check. */
    private static final java.util.Set<String> ACCESSORIES =
            java.util.Set.of("leather_belt", "duty_belt", "scarf", "gloves", "professor_hat", "travel_hat");

    private static Map.Entry<String, String[]> robe(String item, String model, String livery) {
        return Map.entry(item, new String[]{model, livery});
    }

    /** Cuts worn on the back: a cape, not a robe, so their bones are the cape's. */
    private static final java.util.Set<String> CAPES = java.util.Set.of("school_cape", "travel_cloak");

    private static final Pattern RANK_BONE = Pattern.compile("rank([1-3])(ArmR|ArmL)?");

    /** The skirt's top, the bottom of a hanging hand. A sleeve piece reaching below this sits in the skirt. */
    private static final double SKIRT_TOP = 12.0;

    @Test
    void everyLiveryDecodesAndNamesOnlyKnownCuts() throws IOException {
        Map<String, LiveryDefinition> liveries = liveries();
        assertTrue(liveries.size() >= CUTS.size(), "fewer liveries than cuts");
        liveries.forEach((name, livery) -> {
            assertTrue(!livery.cuts().isEmpty(), name + " admits no cut");
            for (Identifier cut : livery.cuts()) {
                assertEquals(MOD, cut.getNamespace(), name + " names another mod's item " + cut);
                assertTrue(CUTS.containsKey(cut.getPath()), name + " names " + cut + ", which is not a wardrobe cut");
            }
        });
    }

    @Test
    void everyCutsDefaultLiveryExistsAndAdmitsIt() throws IOException {
        Map<String, LiveryDefinition> liveries = liveries();
        CUTS.forEach((item, cut) -> {
            LiveryDefinition livery = liveries.get(cut[1]);
            assertNotNull(livery, item + "'s default livery " + cut[1] + " has no file");
            assertTrue(livery.admits(Identifier.fromNamespaceAndPath(MOD, item)),
                    cut[1] + " does not admit its own cut " + item);
        });
    }

    @Test
    void everyLiveryHasASheetOnEveryCutItAdmitsTheSameSizeAsTheModel() throws IOException {
        for (Map.Entry<String, LiveryDefinition> entry : liveries().entrySet()) {
            for (Identifier cutId : entry.getValue().cuts()) {
                String[] cut = CUTS.get(cutId.getPath());
                Identifier sheet = Wardrobe.liveryTexture(cut[0],
                        Identifier.fromNamespaceAndPath(MOD, cut[1]),
                        Optional.of(Identifier.fromNamespaceAndPath(MOD, entry.getKey())));
                Path png = sheet == null
                        ? ASSETS.resolve("textures/armor/" + cut[0] + ".png")
                        : ASSETS.resolve(sheet.getPath());
                assertTrue(Files.exists(png), entry.getKey() + " on " + cutId + ": missing " + png);
                BufferedImage image = ImageIO.read(png.toFile());
                JsonObject description = geometry(cut[0]).getAsJsonObject("description");
                assertEquals(description.get("texture_width").getAsInt(), image.getWidth(), png.toString());
                assertEquals(description.get("texture_height").getAsInt(), image.getHeight(), png.toString());
            }
        }
    }

    @Test
    void everyCutCarriesTheClothBonesUnderTheBody() throws IOException {
        for (Map.Entry<String, String[]> entry : CUTS.entrySet()) {
            String[] cut = entry.getValue();
            Map<String, String> parents = parents(cut[0]);
            if (ACCESSORIES.contains(entry.getKey())) {
                continue;
            }
            if (CAPES.contains(entry.getKey())) {
                assertEquals("armorBody", parents.get("capeRoot"), cut[0] + ": capeRoot must hang off armorBody");
                assertEquals("capeRoot", parents.get("capeHem"), cut[0] + ": capeHem must hang off capeRoot");
                continue;
            }
            assertEquals("armorBody", parents.get("robeSkirt"), cut[0] + ": robeSkirt must hang off armorBody");
            for (String bone : List.of("clothLegR", "clothLegL", "clothHipFront")) {
                assertEquals("robeSkirt", parents.get(bone), cut[0] + ": " + bone + " must hang off robeSkirt");
            }
        }
    }

    @Test
    void rankBonesAreNamedForTheRendererAndHangFromTheirLimb() throws IOException {
        for (String[] cut : CUTS.values()) {
            for (Map.Entry<String, String> bone : parents(cut[0]).entrySet()) {
                if (!bone.getKey().startsWith("rank")) {
                    continue;
                }
                var match = RANK_BONE.matcher(bone.getKey());
                assertTrue(match.matches(), cut[0] + ": " + bone.getKey() + " is not a name the renderer toggles");
                String expected = match.group(2) == null ? "armorBody"
                        : match.group(2).equals("ArmR") ? "armorRightArm" : "armorLeftArm";
                assertEquals(expected, bone.getValue(), cut[0] + ": " + bone.getKey());
            }
        }
    }

    /**
     * The overlap rule (lessons, 2026-11-04): a cuff or bell that hangs below the hand sits inside
     * the skirt at rest. The sleeve shell itself may dip by its inflate; nothing on an arm lower.
     */
    @Test
    void nothingOnAnArmReachesIntoTheSkirt() throws IOException {
        for (String[] cut : CUTS.values()) {
            Map<String, String> parents = parents(cut[0]);
            for (JsonElement element : bones(cut[0])) {
                JsonObject bone = element.getAsJsonObject();
                String name = bone.get("name").getAsString();
                String limb = name.startsWith("armor") ? name : parents.get(name);
                if (!"armorRightArm".equals(limb) && !"armorLeftArm".equals(limb) || !bone.has("cubes")) {
                    continue;
                }
                for (JsonElement c : bone.getAsJsonArray("cubes")) {
                    JsonObject cube = c.getAsJsonObject();
                    double inflate = cube.has("inflate") ? cube.get("inflate").getAsDouble() : 0;
                    double bottom = cube.getAsJsonArray("origin").get(1).getAsDouble() - inflate;
                    assertTrue(bottom >= SKIRT_TOP - 0.5,
                            cut[0] + ": a cube on " + name + " reaches down to y " + bottom);
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------

    private static Map<String, LiveryDefinition> liveries() throws IOException {
        Map<String, LiveryDefinition> out = new HashMap<>();
        try (Stream<Path> files = Files.list(LIVERIES)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                String name = file.getFileName().toString().replace(".json", "");
                var result = LiveryDefinition.CODEC.parse(JsonOps.INSTANCE, read(file));
                assertTrue(result.isSuccess(), name + ": " + result.error().map(Object::toString).orElse(""));
                out.put(name, result.getOrThrow());
            }
        }
        return out;
    }

    private static JsonObject geometry(String model) throws IOException {
        return read(ASSETS.resolve("geckolib/models/armor/" + model + ".geo.json")).getAsJsonObject()
                .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
    }

    private static JsonArray bones(String model) throws IOException {
        return geometry(model).getAsJsonArray("bones");
    }

    private static Map<String, String> parents(String model) throws IOException {
        Map<String, String> out = new HashMap<>();
        for (JsonElement element : bones(model)) {
            JsonObject bone = element.getAsJsonObject();
            out.put(bone.get("name").getAsString(), bone.has("parent") ? bone.get("parent").getAsString() : null);
        }
        return out;
    }

    private static JsonElement read(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path));
    }
}
