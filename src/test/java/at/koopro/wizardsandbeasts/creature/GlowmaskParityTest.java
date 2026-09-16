package at.koopro.wizardsandbeasts.creature;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every entity glowmask still describes the skin it sits next to.
 *
 * <p>{@code GeoRendererHelper.applyGlowIfPresent} attaches an {@code AutoGlowingGeoLayer} whenever
 * {@code textures/entity/<id>_glowmask.png} exists, and the mask is sampled through the same UVs as
 * the skin. Nothing ties the two files together, so a rig rebuilt with a new UV layout keeps the old
 * rig's mask and lights up texels that now belong to a leg, a flank or nothing at all. The first wave
 * of hand-built creature rigs shipped exactly that on six creatures — every one of them with a mask of
 * a different size from its new skin, which is the check below.
 *
 * <p>Pure file I/O, no Minecraft bootstrap.
 */
class GlowmaskParityTest {

    private static final Path ENTITY_TEXTURES =
            Path.of("src", "main", "resources", "assets", "wizards_and_beasts", "textures", "entity");

    private static final String SUFFIX = "_glowmask.png";

    @Test
    void everyGlowmaskMatchesItsSkin() throws IOException {
        List<Path> masks;
        try (var files = Files.list(ENTITY_TEXTURES)) {
            masks = files.filter(p -> p.getFileName().toString().endsWith(SUFFIX)).sorted().toList();
        }
        assertFalse(masks.isEmpty(), "expected entity glowmasks on disk");

        List<String> problems = new ArrayList<>();
        for (Path maskPath : masks) {
            String name = maskPath.getFileName().toString();
            String id = name.substring(0, name.length() - SUFFIX.length());
            Path skinPath = ENTITY_TEXTURES.resolve(id + ".png");
            if (!Files.exists(skinPath)) {
                problems.add(id + ": glowmask has no skin beside it");
                continue;
            }
            BufferedImage mask = ImageIO.read(maskPath.toFile());
            BufferedImage skin = ImageIO.read(skinPath.toFile());
            if (mask.getWidth() != skin.getWidth() || mask.getHeight() != skin.getHeight()) {
                problems.add(id + ": glowmask is " + mask.getWidth() + "x" + mask.getHeight()
                        + " but the skin is " + skin.getWidth() + "x" + skin.getHeight()
                        + " — it was painted for a different UV layout; regenerate or delete it");
                continue;
            }
            int stray = 0;
            for (int y = 0; y < mask.getHeight(); y++) {
                for (int x = 0; x < mask.getWidth(); x++) {
                    boolean lit = (mask.getRGB(x, y) >>> 24) != 0;
                    boolean painted = (skin.getRGB(x, y) >>> 24) != 0;
                    if (lit && !painted) {
                        stray++;
                    }
                }
            }
            if (stray > 0) {
                problems.add(id + ": glowmask lights " + stray + " texel(s) the skin never painted");
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }
}
