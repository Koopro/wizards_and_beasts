package at.koopro.wizardsandbeasts.wand.visual;

import at.koopro.wizardsandbeasts.wand.customization.WandModule;
import at.koopro.wizardsandbeasts.wand.customization.WandModuleRegistry;
import at.koopro.wizardsandbeasts.wand.customization.WandSlot;
import at.koopro.wizardsandbeasts.wand.registry.WandCoreAppearance;
import at.koopro.wizardsandbeasts.wand.registry.WandCoreDefinition;
import at.koopro.wizardsandbeasts.wand.registry.WandRareConstruction;
import at.koopro.wizardsandbeasts.wand.registry.WandWoodDefinition;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The look system's shipped half: the wood and core {@code appearance} blocks, the sheets
 * {@code tools/wand_model.py} and {@code tools/item_geo.py} paint, and how they join.
 *
 * <p>Every failure here is otherwise silent in game. A grain sheet painted on another UV layout draws
 * a wand in the wrong texels; a wood naming a grain nobody painted falls back to the plain sheet; a
 * rare construction naming a module the rig lacks shows an empty slot; a mask lighting texels the
 * wand does not use lights nothing. And the last test is a visual regression snapshot: every shipped
 * wood with every shipped core, resolved, against {@code src/test/resources/wand/wand_looks.golden}.
 */
class WandLookAssetTest {

    private static final Path DATA = Path.of("src", "main", "resources", "data",
            "wizards_and_beasts", "wizards_and_beasts");
    private static final Path ASSETS = Path.of("src", "main", "resources", "assets", "wizards_and_beasts");
    private static final Path TEXTURES = ASSETS.resolve("textures/item");
    private static final Path WAND_GEO = ASSETS.resolve("geckolib/models/item/wand.geo.json");
    private static final Path TOOL = Path.of("tools", "wand_model.py");
    private static final Path GOLDEN = Path.of("src", "test", "resources", "wand", "wand_looks.golden");

    private static final Map<String, WandWoodDefinition> WOODS = new TreeMap<>();
    private static final Map<String, WandCoreDefinition> CORES = new TreeMap<>();

    @BeforeAll
    static void load() throws IOException {
        WandModuleRegistry.bootstrap();
        WOODS.putAll(parse(DATA.resolve("wand_woods"), WandWoodDefinition.CODEC));
        CORES.putAll(parse(DATA.resolve("wand_cores"), WandCoreDefinition.CODEC));
        assertFalse(WOODS.isEmpty());
        assertFalse(CORES.isEmpty());
    }

    private static <T> Map<String, T> parse(Path dir, Codec<T> codec) throws IOException {
        Map<String, T> out = new HashMap<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                out.put(file.getFileName().toString().replace(".json", ""), codec
                        .parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(file)))
                        .getOrThrow(AssertionError::new));
            }
        }
        return out;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("wizards_and_beasts", path);
    }

    private static BufferedImage image(Path path) throws IOException {
        assertTrue(Files.isRegularFile(path), "missing " + path);
        return ImageIO.read(path.toFile());
    }

    private static boolean opaque(BufferedImage img, int x, int y) {
        return (img.getRGB(x, y) >>> 24) > 0;
    }

    // ── grain ────────────────────────────────────────────────────────────────

    @Test
    void everyWoodHasItsOwnGrainOnTheWandsLayout() throws IOException {
        BufferedImage plain = image(TEXTURES.resolve("wand.png"));
        Set<Identifier> grains = new HashSet<>();
        for (Map.Entry<String, WandWoodDefinition> wood : WOODS.entrySet()) {
            Identifier grain = wood.getValue().appearance().grain().orElse(null);
            assertNotNull(grain, wood.getKey() + " names no grain");
            assertTrue(grains.add(grain), wood.getKey() + " shares grain " + grain + " with another wood");
            BufferedImage sheet = image(TEXTURES.resolve("wand/grain_" + grain.getPath() + ".png"));
            assertEquals(plain.getWidth(), sheet.getWidth(), grain + " sheet width");
            assertEquals(plain.getHeight(), sheet.getHeight(), grain + " sheet height");
            for (int y = 0; y < plain.getHeight(); y++) {
                for (int x = 0; x < plain.getWidth(); x++) {
                    assertEquals(opaque(plain, x, y), opaque(sheet, x, y),
                            grain + " is not painted on wand.png's UV layout at " + x + "," + y);
                }
            }
        }
    }

    // ── trim and core-light masks ────────────────────────────────────────────

    @Test
    void overlaySheetsOnlyTouchTexelsTheWandUses() throws IOException {
        checkOverlays(TEXTURES.resolve("wand.png"), "wand_trim.png", "wand_glint.png", "wand_glint_bonded.png");
        checkOverlays(TEXTURES.resolve("elder_wand.png"), null, "elder_wand_glint.png", "elder_wand_glint_bonded.png");
    }

    private static void checkOverlays(Path basePath, String trim, String glint, String bonded) throws IOException {
        BufferedImage base = image(basePath);
        List<String> sheets = new ArrayList<>(List.of(glint, bonded));
        if (trim != null) {
            sheets.add(trim);
        }
        for (String name : sheets) {
            BufferedImage sheet = image(TEXTURES.resolve(name));
            assertEquals(base.getWidth(), sheet.getWidth(), name);
            assertEquals(base.getHeight(), sheet.getHeight(), name);
            int lit = 0;
            for (int y = 0; y < base.getHeight(); y++) {
                for (int x = 0; x < base.getWidth(); x++) {
                    if (opaque(sheet, x, y)) {
                        lit++;
                        assertTrue(opaque(base, x, y), name + " marks texel " + x + "," + y + " the wand never samples");
                    }
                }
            }
            assertTrue(lit > 0, name + " is empty");
        }
        BufferedImage point = image(TEXTURES.resolve(glint));
        BufferedImage full = image(TEXTURES.resolve(bonded));
        for (int y = 0; y < base.getHeight(); y++) {
            for (int x = 0; x < base.getWidth(); x++) {
                if (opaque(point, x, y)) {
                    assertTrue(opaque(full, x, y), bonded + " must light everything " + glint + " does");
                }
            }
        }
    }

    // ── cores ────────────────────────────────────────────────────────────────

    @Test
    void everyCoreShowsItsOwnLight() {
        List<int[]> seen = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (Map.Entry<String, WandCoreDefinition> core : CORES.entrySet()) {
            WandCoreAppearance look = core.getValue().appearance();
            assertTrue(look.glint().isPresent(), core.getKey() + " authors no light");
            int rgb = look.glint().get();
            int[] c = {(rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, look.pattern().ordinal()};
            for (int i = 0; i < seen.size(); i++) {
                int[] o = seen.get(i);
                double distance = Math.sqrt(Math.pow(c[0] - o[0], 2) + Math.pow(c[1] - o[1], 2) + Math.pow(c[2] - o[2], 2));
                assertTrue(distance > 40 || c[3] != o[3],
                        core.getKey() + " and " + names.get(i) + " show the same light in the same rhythm");
            }
            seen.add(c);
            names.add(core.getKey());
        }
    }

    // ── rare constructions ───────────────────────────────────────────────────

    @Test
    void rareConstructionsAreRareAndBuiltFromRealParts() throws IOException {
        String geo = Files.readString(WAND_GEO);
        for (Map.Entry<String, WandWoodDefinition> wood : WOODS.entrySet()) {
            List<WandRareConstruction> rares = wood.getValue().appearance().rare();
            double total = rares.stream().mapToDouble(WandRareConstruction::chance).sum();
            assertTrue(total <= 0.2 + 1e-6, wood.getKey() + ": " + total + " of its wands are 'rare'");
            for (WandRareConstruction rare : rares) {
                for (Map.Entry<WandSlot, Identifier> module : rare.modules().entrySet()) {
                    WandModule found = WandModuleRegistry.get(module.getValue()).orElse(null);
                    assertNotNull(found, wood.getKey() + ": no wand module " + module.getValue());
                    assertEquals(module.getKey(), found.slot(), wood.getKey() + ": " + module.getValue()
                            + " is not a " + module.getKey().slotId());
                    assertTrue(geo.contains("\"name\": \"" + found.boneName() + "\""),
                            wood.getKey() + ": wand.geo.json has no bone " + found.boneName());
                }
            }
        }
    }

    // ── the offline preview mirrors the resolver ─────────────────────────────

    /** {@code wand_model.py --looks} copies the resolver's tables; a drift would make its sheets lie. */
    @Test
    void theLookSheetToolUsesTheResolversTables() throws IOException {
        String tool = Files.readString(TOOL, StandardCharsets.UTF_8);
        Matcher constants = Pattern.compile(
                "REFERENCE_INCHES, SCALE_PER_INCH, MIN_SCALE, MAX_SCALE = ([\\d.]+), ([\\d.]+), ([\\d.]+), ([\\d.]+)")
                .matcher(tool);
        assertTrue(constants.find(), "wand_model.py no longer states the length table");
        assertEquals(WandLookResolver.REFERENCE_INCHES, Float.parseFloat(constants.group(1)), 1e-6f);
        assertEquals(WandLookResolver.SCALE_PER_INCH, Float.parseFloat(constants.group(2)), 1e-6f);
        assertEquals(WandLookResolver.MIN_LENGTH_SCALE, Float.parseFloat(constants.group(3)), 1e-6f);
        assertEquals(WandLookResolver.MAX_LENGTH_SCALE, Float.parseFloat(constants.group(4)), 1e-6f);
        for (WandFlexibility flex : WandFlexibility.values()) {
            String name = flex.getSerializedName();
            assertEquals(WandLookResolver.girth(flex), pyTable(tool, "GIRTH", name), 1e-6f, "GIRTH " + name);
            assertEquals(WandLookResolver.bowDegrees(flex), pyTable(tool, "BOW", name), 1e-6f, "BOW " + name);
        }
    }

    private static float pyTable(String tool, String table, String key) {
        Matcher line = Pattern.compile("^" + table + " = \\{(.*)}$", Pattern.MULTILINE).matcher(tool);
        assertTrue(line.find(), "wand_model.py has no " + table + " table");
        Matcher entry = Pattern.compile("\"" + key + "\": ([\\d.]+)").matcher(line.group(1));
        assertTrue(entry.find(), table + " has no " + key);
        return Float.parseFloat(entry.group(1));
    }

    // ── visual regression snapshot ───────────────────────────────────────────

    /**
     * Every shipped wood with every shipped core, plus a length and flexibility sweep, resolved and
     * written one line each. A change to any table, any wood or core {@code appearance}, or the seed shows
     * up here as a diff to review. When the change is intended, delete the golden file and run the test
     * once: it writes the new snapshot and fails so the rewrite cannot pass unnoticed.
     */
    @Test
    void resolvedLooksMatchTheGoldenSnapshot() throws IOException {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, WandWoodDefinition> wood : WOODS.entrySet()) {
            for (Map.Entry<String, WandCoreDefinition> core : CORES.entrySet()) {
                boolean elder = wood.getKey().equals("elder") && core.getKey().equals("thestral_tail_hair");
                lines.add(line(wood.getKey() + "/" + core.getKey(), WandLookResolver.resolve(wood.getValue(),
                        id(wood.getKey()), core.getValue(), id(core.getKey()), 12.25f, WandFlexibility.PLIANT, null, elder)));
            }
        }
        for (float inches : new float[]{8.5f, 10.0f, 12.25f, 13.5f, 15.0f}) {
            for (WandFlexibility flex : WandFlexibility.values()) {
                lines.add(line("holly/" + inches + "/" + flex.getSerializedName(), WandLookResolver.resolve(
                        WOODS.get("holly"), id("holly"), CORES.get("unicorn_hair"), id("unicorn_hair"),
                        inches, flex, null, false)));
            }
        }
        String actual = String.join("\n", lines) + "\n";
        if (!Files.exists(GOLDEN)) {
            Files.createDirectories(GOLDEN.getParent());
            Files.writeString(GOLDEN, actual, StandardCharsets.UTF_8);
            fail("wrote a new " + GOLDEN + "; review it and run again");
        }
        String expected = Files.readString(GOLDEN, StandardCharsets.UTF_8).replace("\r\n", "\n");
        if (!expected.equals(actual)) {
            List<String> want = List.of(expected.split("\n"));
            for (int i = 0; i < Math.max(want.size(), lines.size()); i++) {
                String w = i < want.size() ? want.get(i) : "<none>";
                String a = i < lines.size() ? lines.get(i) : "<none>";
                assertEquals(w, a, "wand look changed at line " + (i + 1)
                        + " (delete " + GOLDEN + " and rerun if this is intended)");
            }
        }
    }

    private static String line(String label, WandLook look) {
        return String.format(Locale.ROOT,
                "%s grain=%s tint=%08X cfg=%s rare=%d len=%.3f girth=%.2f bow=%+.3f twist=%+.3f glint=%06X %s %.2f phase=%.4f elder=%s",
                label, look.grain() == null ? "-" : look.grain().getPath(), look.tint(), modules(look), look.rareIndex(),
                look.lengthScale(), look.girth(), look.bowDegrees(), look.twistDegrees(), look.glint(),
                look.pattern().getSerializedName(), look.glintStrength(), look.phase(), look.elder());
    }

    private static String modules(WandLook look) {
        StringBuilder out = new StringBuilder();
        for (WandSlot slot : WandSlot.values()) {
            look.configuration().getModule(slot).ifPresent(m ->
                    out.append(out.isEmpty() ? "" : ",").append(slot.slotId()).append(':').append(m.getPath()));
        }
        return out.toString();
    }
}
