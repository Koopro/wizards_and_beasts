package at.koopro.wizardsandbeasts.resources;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every mob effect the mod registers has its 18x18 icon, and every ability the wheel can show names a 16 px icon
 * that exists. Six effects shipped with none -- four of them shown in the HUD, so the HUD drew the missing-texture
 * sprite -- and every ability named one {@code ability/placeholder.png} nobody had painted.
 */
class EffectAndAbilityIconAssetTest {

    private static final Path ASSETS = Path.of("src/main/resources/assets/wizards_and_beasts");
    private static final Path MOD_EFFECTS = Path.of("src/main/java/at/koopro/wizardsandbeasts/effect/ModEffects.java");
    private static final Path ABILITIES = Path.of("src/main/resources/data/wizards_and_beasts/abilities");
    private static final Pattern REGISTER = Pattern.compile("MOB_EFFECTS\\.register\\(\\s*\"([a-z0-9_]+)\"");
    private static final Pattern ICON = Pattern.compile("\"icon\"\\s*:\\s*\"wizards_and_beasts:([^\"]+)\"");
    /** Framework test abilities: granted only with debug tools on, and left on the monogram fallback. */
    private static final String DEBUG_PREFIX = "debug_";

    @Test
    void everyRegisteredEffectHasAnIcon() throws IOException {
        Set<String> ids = new TreeSet<>();
        Matcher m = REGISTER.matcher(Files.readString(MOD_EFFECTS));
        while (m.find()) {
            ids.add(m.group(1));
        }
        assertTrue(ids.size() > 40, "found only " + ids.size() + " MOB_EFFECTS.register calls in " + MOD_EFFECTS);
        List<String> missing = ids.stream()
                .filter(id -> !Files.exists(ASSETS.resolve("textures/mob_effect/" + id + ".png")))
                .toList();
        assertTrue(missing.isEmpty(), "registered effects with no icon (the HUD draws the missing texture): " + missing);
    }

    @Test
    void everyAbilityIconExistsAndIsSixteenPixelArt() throws IOException {
        List<String> bad = new ArrayList<>();
        int checked = 0;
        try (Stream<Path> files = Files.list(ABILITIES)) {
            for (Path json : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String id = json.getFileName().toString().replace(".json", "");
                if (id.startsWith(DEBUG_PREFIX)) {
                    continue;
                }
                Matcher m = ICON.matcher(Files.readString(json));
                if (!m.find()) {
                    bad.add(id + " names no mod icon");
                    continue;
                }
                Path png = ASSETS.resolve(m.group(1));
                if (!Files.exists(png)) {
                    bad.add(id + " names " + m.group(1) + ", which does not exist");
                    continue;
                }
                checked++;
                BufferedImage img = ImageIO.read(png.toFile());
                if (img.getWidth() != 16 || img.getHeight() != 16) {
                    bad.add(id + " is " + img.getWidth() + "x" + img.getHeight() + " (the wheel draws 16 px 1:1)");
                    continue;
                }
                if (hasSoftAlpha(img)) {
                    bad.add(id + " has soft alpha");
                }
            }
        }
        assertTrue(checked > 10, "only " + checked + " ability icons checked");
        assertTrue(bad.isEmpty(), "abilities outside the icon rules: " + bad);
    }

    private static boolean hasSoftAlpha(BufferedImage img) {
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int alpha = img.getRGB(x, y) >>> 24;
                if (alpha != 0 && alpha != 255) {
                    return true;
                }
            }
        }
        return false;
    }
}
