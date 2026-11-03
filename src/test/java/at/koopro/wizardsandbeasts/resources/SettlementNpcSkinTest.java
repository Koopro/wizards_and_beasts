package at.koopro.wizardsandbeasts.resources;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A hamlet's people ({@code textures/entity/settlement_npc}): every name a role gives out is a person with a skin of
 * their own, {@code <role>_<n>.png}, which {@code SettlementNpcRenderer} picks by the number the name key ends in.
 * Each is a 64x64 classic player skin from {@code tools/gen_npc_skins.py}, opaque on the base layer (cutout would
 * punch a hole through the body) and hard-edged on the overlay.
 */
class SettlementNpcSkinTest {

    private static final Path SKINS = Path.of("src/main/resources/assets/wizards_and_beasts/textures/entity/settlement_npc");
    private static final Path ROLES = Path.of("src/main/resources/data/wizards_and_beasts/npc_roles");
    private static final String MARKER = "Generator\0wizards_and_beasts/tools/gen_npc_skins.py";
    private static final Pattern NAME = Pattern.compile("\"npc\\.[a-z_]+\\.[a-z_]+\\.name\\.(\\d+)\"");
    /** The base layer's boxes, (u, v, w, h, d): head, body, right arm, right leg, left arm, left leg. */
    private static final int[][] BASE = {
            {0, 0, 8, 8, 8}, {16, 16, 8, 12, 4}, {40, 16, 4, 12, 4}, {0, 16, 4, 12, 4}, {32, 48, 4, 12, 4}, {16, 48, 4, 12, 4}};

    @Test
    void everyNamedPersonHasASkin() throws IOException {
        List<String> missing = new ArrayList<>();
        int people = 0;
        try (Stream<Path> files = Files.list(ROLES)) {
            for (Path json : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                String text = Files.readString(json);
                if (text.contains("\"entity\"")) {
                    continue; // drawn by its own entity (the goblin teller)
                }
                String role = json.getFileName().toString().replace(".json", "");
                Matcher m = NAME.matcher(text);
                while (m.find()) {
                    people++;
                    if (!Files.exists(SKINS.resolve(role + "_" + m.group(1) + ".png"))) {
                        missing.add(role + "_" + m.group(1));
                    }
                }
            }
        }
        assertTrue(people >= 16, "expected the four roles' sixteen people, found " + people);
        assertTrue(missing.isEmpty(), "named people with no skin: " + missing);
    }

    @Test
    void skinsArePlayerSkinsFromTheirGenerator() throws IOException {
        List<String> bad = new ArrayList<>();
        Map<String, String> seen = new HashMap<>();
        for (Path png : skins()) {
            String name = png.getFileName().toString();
            BufferedImage img = ImageIO.read(png.toFile());
            if (img.getWidth() != 64 || img.getHeight() != 64) {
                bad.add(name + " is " + img.getWidth() + "x" + img.getHeight());
                continue;
            }
            if (!new String(Files.readAllBytes(png), StandardCharsets.ISO_8859_1).contains(MARKER)) {
                bad.add(name + " was not made by gen_npc_skins.py");
            }
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    int alpha = img.getRGB(x, y) >>> 24;
                    if (alpha != 0 && alpha != 255) {
                        bad.add(name + " has soft alpha at " + x + "," + y);
                    }
                }
            }
            for (int[] box : BASE) {
                String hole = hole(img, box);
                if (hole != null) {
                    bad.add(name + " has a see-through base layer at " + hole);
                }
            }
            String other = seen.put(Arrays.toString(img.getRGB(0, 0, 64, 64, null, 0, 64)), name);
            if (other != null) {
                bad.add(name + " is the same skin as " + other);
            }
        }
        assertFalse(seen.isEmpty(), "no settlement skins found");
        assertTrue(bad.isEmpty(), "settlement skins outside the rules: " + bad);
    }

    /** The first transparent texel on a base box's six faces, or null. */
    private static String hole(BufferedImage img, int[] box) {
        int u = box[0], v = box[1], w = box[2], h = box[3], d = box[4];
        int[][] rects = {{u + d, v, 2 * w, d}, {u, v + d, 2 * d + 2 * w, h}};
        for (int[] r : rects) {
            for (int x = r[0]; x < r[0] + r[2]; x++) {
                for (int y = r[1]; y < r[1] + r[3]; y++) {
                    if (img.getRGB(x, y) >>> 24 == 0) {
                        return x + "," + y;
                    }
                }
            }
        }
        return null;
    }

    private static List<Path> skins() throws IOException {
        try (Stream<Path> files = Files.list(SKINS)) {
            return files.filter(p -> p.toString().endsWith(".png")).sorted().toList();
        }
    }
}
