package at.koopro.wizardsandbeasts.resources;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The measurable rules of {@code documentation/VISUAL_STYLE_GUIDE.md}, checked on the shipped files.
 *
 * <p>Each rule here exists because the mod once shipped the opposite and nobody saw it until it
 * was measured against vanilla (visual consistency audit, 2026-09-27): castle stone that was a
 * soft cloud, fourteen particle types sharing one blurred dot, 117 spells on two stand-in
 * colours, a dragon exactly as tall as a troll. Anything a picture is needed to judge stays in
 * the capture harness; this is the part a number can hold.
 */
class VisualStyleConformanceTest {

    private static final Path ASSETS = Path.of("src", "main", "resources", "assets", "wizards_and_beasts");
    private static final Path DATA = Path.of("src", "main", "resources", "data", "wizards_and_beasts");

    /** Surfaces a wall or floor is built from. They must carry vanilla-grade texture, not a wash. */
    private static final Pattern BUILDING_SURFACE =
            Pattern.compile("(stone|cobble|brick|planks|timber|wood|log|marble|tile|flag)");
    /** Mean neighbour luminance step; vanilla stone is ~0.04, the old castle stone 0.015-0.018. */
    private static final double MIN_SURFACE_NOISE = 0.022;
    /** Any fully opaque block face: above this it is a material, below it a flat colour field. */
    private static final double MIN_BLOCK_NOISE = 0.012;

    /** Skins with more colours than this are paintings, not pixel art. */
    private static final int MAX_ENTITY_COLOURS = 48;
    /**
     * Known debt, listed so the rule can hold for everything else: the Protego ward's skin, a
     * translucent effect whose soft alpha is deliberate (style guide, section 2).
     */
    private static final Set<String> ENTITY_COLOUR_DEBT = Set.of("protego_shield.png");

    /** The corpus stand-ins were written without alpha; an opaque #FFFFAA (Revelio) is a real choice. */
    private static final Set<Integer> STAND_IN_SPELL_COLOURS = Set.of(0x00FFFFAA, 0x00FF5555);

    @Test
    void buildingBlocksHaveMaterialNotAWash() throws IOException {
        List<String> failures = new ArrayList<>();
        try (Stream<Path> files = Files.list(ASSETS.resolve("textures/block"))) {
            for (Path png : files.filter(p -> p.toString().endsWith(".png")).sorted().toList()) {
                BufferedImage img = ImageIO.read(png.toFile());
                if (img.getWidth() < 16 || img.getHeight() < 16 || !opaqueTile(img)) {
                    continue;  // cut-outs (crops, torches, banners) are shapes, not surfaces
                }
                String name = png.getFileName().toString().replace(".png", "");
                double noise = neighbourNoise(img);
                boolean surface = BUILDING_SURFACE.matcher(name).find() && !name.contains("pillar");
                double floor = surface ? MIN_SURFACE_NOISE : MIN_BLOCK_NOISE;
                if (noise < floor) {
                    failures.add(String.format("%s: neighbour noise %.3f < %.3f", name, noise, floor));
                }
            }
        }
        assertTrue(failures.isEmpty(), "block faces too flat to read as Minecraft material "
                + "(regenerate through tools/location_textures.py):\n" + String.join("\n", failures));
    }

    @Test
    void everyParticleHasCrispSpritesOfItsOwn() throws IOException {
        List<String> failures = new ArrayList<>();
        Map<String, String> spellSprites = new HashMap<>();
        try (Stream<Path> files = Files.list(ASSETS.resolve("particles"))) {
            for (Path json : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String type = json.getFileName().toString().replace(".json", "");
                JsonArray textures = JsonParser.parseString(Files.readString(json)).getAsJsonObject()
                        .getAsJsonArray("textures");
                if (textures == null || textures.isEmpty()) {
                    failures.add(type + ": lists no sprites");
                    continue;
                }
                if (!type.startsWith("broom_trail_")) {
                    // Broom trails deliberately reuse the sprite set that matches their look.
                    String key = textures.toString();
                    String other = spellSprites.putIfAbsent(key, type);
                    if (other != null) {
                        failures.add(type + ": shares its sprites with " + other);
                    }
                }
                for (int i = 0; i < textures.size(); i++) {
                    String id = textures.get(i).getAsString();
                    Path png = ASSETS.resolve("textures/particle/" + id.substring(id.indexOf(':') + 1) + ".png");
                    if (!Files.exists(png)) {
                        failures.add(type + ": missing sprite " + png);
                        continue;
                    }
                    BufferedImage img = ImageIO.read(png.toFile());
                    if (img.getWidth() > 16 || img.getHeight() > 16) {
                        failures.add(type + ": " + id + " is larger than 16px");
                    }
                    if (!hardAlpha(img)) {
                        failures.add(type + ": " + id + " has soft alpha (particles are crisp pixel sprites)");
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    @Test
    void spellColoursAreRealAndOpaque() throws IOException {
        List<String> failures = new ArrayList<>();
        try (Stream<Path> files = Files.list(DATA.resolve("spells"))) {
            for (Path json : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                JsonObject spell = JsonParser.parseString(Files.readString(json)).getAsJsonObject();
                if (!spell.has("color")) {
                    continue;
                }
                int argb = spell.get("color").getAsInt();
                String name = json.getFileName().toString();
                if ((argb >>> 24) != 0xFF) {
                    failures.add(name + ": colour is not opaque ARGB");
                }
                if (STAND_IN_SPELL_COLOURS.contains(argb)) {
                    failures.add(name + ": still on a corpus stand-in colour");
                }
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    @Test
    void entitySkinsStayPixelArt() throws IOException {
        List<String> failures = new ArrayList<>();
        try (Stream<Path> files = Files.walk(ASSETS.resolve("textures/entity"))) {
            for (Path png : files.filter(p -> p.toString().endsWith(".png")).sorted().toList()) {
                String name = png.getFileName().toString();
                if (name.endsWith("_glowmask.png") || ENTITY_COLOUR_DEBT.contains(name)) {
                    continue;
                }
                BufferedImage img = ImageIO.read(png.toFile());
                Set<Integer> colours = new HashSet<>();
                int black = 0;
                for (int y = 0; y < img.getHeight(); y++) {
                    for (int x = 0; x < img.getWidth(); x++) {
                        int argb = img.getRGB(x, y);
                        if ((argb >>> 24) == 0) {
                            continue;
                        }
                        colours.add(argb & 0xFFFFFF);
                        if ((argb & 0xFFFFFF) == 0) {
                            black++;
                        }
                    }
                }
                if (colours.size() > MAX_ENTITY_COLOURS) {
                    failures.add(png + ": " + colours.size() + " colours (max " + MAX_ENTITY_COLOURS + ")");
                }
                if (black > 0) {
                    failures.add(png + ": " + black + " pure-black texels (darkest ink is never #000)");
                }
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    @Test
    void effectIconsShareVanillasCanvas() throws IOException {
        // Two effects once shipped 128x128 paintings in an 18x18 slot: seven times the pixel
        // density of the icons beside them.
        List<String> failures = new ArrayList<>();
        try (Stream<Path> files = Files.list(ASSETS.resolve("textures/mob_effect"))) {
            for (Path png : files.filter(p -> p.toString().endsWith(".png")).sorted().toList()) {
                BufferedImage img = ImageIO.read(png.toFile());
                if (img.getWidth() != 18 || img.getHeight() != 18) {
                    failures.add(png.getFileName() + ": " + img.getWidth() + "x" + img.getHeight() + " (vanilla is 18x18)");
                }
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    /** The seven parts every wandwood species ships as blocks. */
    private static final List<String> WOOD_PARTS = List.of("%s_log", "%s_log_top", "stripped_%s_log",
            "stripped_%s_log_top", "%s_planks", "%s_leaves", "%s_sapling");
    /** Planks mean vs the wand's heartwood (tint x 0.86, as the wand sheet renders it). */
    private static final double MAX_HEARTWOOD_DISTANCE = 32.0;
    /** The architectural gilt, the GUI's `GILT_DARK/GILT/GILT_LIGHT`. */
    private static final Set<Integer> GILT = Set.of(0x8F6522, 0xC9973A, 0xEBC874);

    @Test
    void woodFamilyIsCompleteAndMatchesItsWands() throws IOException {
        // One heartwood per species, shared by the wand and the blocks. Before the families pass,
        // holly planks were green-grey under an ivory holly wand and every stripped log the same beige.
        Path woods = DATA.resolve("wizards_and_beasts/wand_woods");
        List<String> failures = new ArrayList<>();
        try (Stream<Path> files = Files.list(woods)) {
            for (Path json : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String species = json.getFileName().toString().replace(".json", "");
                Path planks = ASSETS.resolve("textures/block/" + species + "_planks.png");
                if (!Files.exists(planks)) {
                    continue;  // a wand-only wood (vine) has no blocks
                }
                for (String part : WOOD_PARTS) {
                    if (!Files.exists(ASSETS.resolve("textures/block/" + part.formatted(species) + ".png"))) {
                        failures.add(species + ": missing " + part.formatted(species));
                    }
                }
                String hex = JsonParser.parseString(Files.readString(json)).getAsJsonObject()
                        .getAsJsonObject("appearance").get("tint").getAsString().replace("#", "");
                int tint = Integer.parseUnsignedInt(hex.substring(hex.length() - 6), 16);
                BufferedImage img = ImageIO.read(planks.toFile());
                double r = 0, g = 0, b = 0;
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        int c = img.getRGB(x, y);
                        r += (c >> 16) & 0xFF;
                        g += (c >> 8) & 0xFF;
                        b += c & 0xFF;
                    }
                }
                double dr = r / 256 - ((tint >> 16) & 0xFF) * 0.86;
                double dg = g / 256 - ((tint >> 8) & 0xFF) * 0.86;
                double db = b / 256 - (tint & 0xFF) * 0.86;
                double distance = Math.sqrt(dr * dr + dg * dg + db * db);
                if (distance > MAX_HEARTWOOD_DISTANCE) {
                    failures.add(String.format("%s: planks sit %.0f from the wand's heartwood (max %.0f) — "
                            + "regenerate through tools/wandwood_textures.py", species, distance, MAX_HEARTWOOD_DISTANCE));
                }
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    @Test
    void gildedBlocksShareOneGold() throws IOException {
        List<String> failures = new ArrayList<>();
        try (Stream<Path> files = Files.list(ASSETS.resolve("textures/block"))) {
            for (Path png : files.filter(p -> {
                String n = p.getFileName().toString();
                return n.endsWith(".png") && (n.contains("gold_trim") || n.contains("gilded"));
            }).sorted().toList()) {
                BufferedImage img = ImageIO.read(png.toFile());
                Set<Integer> seen = new HashSet<>();
                for (int y = 0; y < img.getHeight(); y++) {
                    for (int x = 0; x < img.getWidth(); x++) {
                        int c = img.getRGB(x, y) & 0xFFFFFF;
                        if (GILT.contains(c)) {
                            seen.add(c);
                        }
                    }
                }
                if (seen.size() < 2) {
                    failures.add(png.getFileName() + ": gilding is not drawn in the shared gilt ramp "
                            + "(tools/location_textures.py GILT_*)");
                }
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    @Test
    void creatureScaleFollowsTheWorld() throws IOException {
        Map<String, Double> height = new HashMap<>();
        try (Stream<Path> files = Files.list(DATA.resolve("creatures"))) {
            for (Path json : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonObject def = JsonParser.parseString(Files.readString(json)).getAsJsonObject();
                double scale = def.has("scale") ? def.get("scale").getAsDouble() : 1.0;
                height.put(json.getFileName().toString().replace(".json", ""),
                        def.get("height").getAsDouble() * scale);
            }
        }
        double player = 1.8;
        double troll = height.get("troll");
        List<String> failures = new ArrayList<>();
        check(failures, height.get("giant") > troll, "a giant must stand taller than a troll");
        check(failures, troll > player, "a troll must stand taller than a player");
        check(failures, height.get("centaur") > player, "a centaur must stand taller than a player");
        check(failures, height.get("thunderbird") > height.get("hippogriff"),
                "the Thunderbird must be bigger than a Hippogriff");
        for (String dragon : List.of("hungarian_horntail", "ukrainian_ironbelly", "romanian_longhorn",
                "norwegian_ridgeback", "hebridean_black", "chinese_fireball", "swedish_short_snout",
                "antipodean_opaleye", "common_welsh_green")) {
            check(failures, height.get(dragon) >= troll, dragon + " must be at least as tall as a troll");
        }
        check(failures, height.get("hungarian_horntail") > height.get("common_welsh_green"),
                "the Horntail must outsize the Welsh Green");
        assertTrue(failures.isEmpty(), "scale hierarchy broken (definition `scale`):\n"
                + String.join("\n", failures));
    }

    // ------------------------------------------------------------------ helpers

    private static void check(List<String> failures, boolean ok, String rule) {
        if (!ok) {
            failures.add(rule);
        }
    }

    private static boolean opaqueTile(BufferedImage img) {
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if ((img.getRGB(x, y) >>> 24) != 0xFF) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean hardAlpha(BufferedImage img) {
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int a = img.getRGB(x, y) >>> 24;
                if (a != 0 && a != 0xFF) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Mean absolute luminance step between neighbouring texels of the top-left 16x16 tile. */
    private static double neighbourNoise(BufferedImage img) {
        double[][] lum = new double[16][16];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int rgb = img.getRGB(x, y);
                lum[y][x] = (0.2126 * ((rgb >> 16) & 0xFF) + 0.7152 * ((rgb >> 8) & 0xFF)
                        + 0.0722 * (rgb & 0xFF)) / 255.0;
            }
        }
        double sum = 0;
        int n = 0;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if (x < 15) {
                    sum += Math.abs(lum[y][x + 1] - lum[y][x]);
                    n++;
                }
                if (y < 15) {
                    sum += Math.abs(lum[y + 1][x] - lum[y][x]);
                    n++;
                }
            }
        }
        return sum / n;
    }
}
