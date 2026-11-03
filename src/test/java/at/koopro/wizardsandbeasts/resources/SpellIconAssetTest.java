package at.koopro.wizardsandbeasts.resources;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
 * The spell menu's 16 px icons ({@code gui/sprites/spell_icon}): every spell has one, and each is pixel art drawn
 * 1:1 -- 16x16, hard-edged alpha, made by {@code tools/spell_glyphs.py}. The 92 px sigils the HUD and the wheel
 * draw are a separate set and are not checked here.
 */
class SpellIconAssetTest {

    private static final Path ICONS = Path.of(
            "src/main/resources/assets/wizards_and_beasts/textures/gui/sprites/spell_icon");
    private static final Path SPELL_JSON = Path.of("src/main/resources/data/wizards_and_beasts/spells");
    private static final Path SPELL_IMPL = Path.of("src/main/java/at/koopro/wizardsandbeasts/spell/impl");
    private static final String MARKER = "Generator\0wizards_and_beasts/tools/spell_glyphs.py";
    /** A Java spell names itself first in its constructor: {@code super("protego", "Protego", …)}. */
    private static final Pattern JAVA_ID = Pattern.compile("super\\(\\s*\"([a-z_]+)\"\\s*,\\s*\"");

    @Test
    void everySpellHasAnIcon() throws IOException {
        List<String> missing = new ArrayList<>();
        for (String id : spellIds()) {
            if (!Files.exists(ICONS.resolve(id + ".png"))) {
                missing.add(id);
            }
        }
        assertTrue(Files.exists(ICONS.resolve("placeholder.png")), "the menu's fallback icon is missing");
        assertTrue(missing.isEmpty(), "spells with no 16 px menu icon: " + missing);
    }

    @Test
    void iconsAreSixteenPixelArtFromTheirGenerator() throws IOException {
        List<String> bad = new ArrayList<>();
        try (Stream<Path> files = Files.list(ICONS)) {
            for (Path png : files.filter(p -> p.toString().endsWith(".png")).toList()) {
                String name = png.getFileName().toString();
                BufferedImage img = ImageIO.read(png.toFile());
                if (img.getWidth() != 16 || img.getHeight() != 16) {
                    bad.add(name + " is " + img.getWidth() + "x" + img.getHeight());
                    continue;
                }
                if (!new String(Files.readAllBytes(png), StandardCharsets.ISO_8859_1).contains(MARKER)) {
                    bad.add(name + " was not made by spell_glyphs.py");
                }
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        int alpha = img.getRGB(x, y) >>> 24;
                        if (alpha != 0 && alpha != 255) {
                            bad.add(name + " has soft alpha at " + x + "," + y);
                            y = 16;
                            break;
                        }
                    }
                }
            }
        }
        assertTrue(bad.isEmpty(), "spell icons outside the pixel rules: " + bad);
    }

    @Test
    void javaSpellsAreFound() throws IOException {
        // Guards the scan above: if the constructor shape changes, the Java spells would silently drop out.
        assertTrue(spellIds().containsAll(Set.of("protego", "imperio", "avada_kedavra", "expecto_patronum")));
    }

    private static Set<String> spellIds() throws IOException {
        Set<String> ids = new TreeSet<>();
        try (Stream<Path> files = Files.list(SPELL_JSON)) {
            files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".json"))
                    .forEach(n -> ids.add(n.substring(0, n.length() - 5)));
        }
        try (Stream<Path> files = Files.walk(SPELL_IMPL)) {
            for (Path java : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher m = JAVA_ID.matcher(Files.readString(java));
                if (m.find()) {
                    ids.add(m.group(1));
                }
            }
        }
        return ids;
    }
}
