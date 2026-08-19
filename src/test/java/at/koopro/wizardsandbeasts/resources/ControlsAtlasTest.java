package at.koopro.wizardsandbeasts.resources;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The controls atlas is the right size for the cells {@code McStylePanel} samples out of it.
 *
 * <p>Its layout is spelled out twice — once in {@code tools/gui_chrome.py} which composes the sheet,
 * once in {@code McStylePanel} which blits cells of it — and a drift between the two is silent. A
 * sheet a row short does not fail to load; it samples transparent, or wraps, and the first anyone
 * knows is a button rendering as a hole. The generator is not on the build path (it is a local
 * tool), so this pins the artefact it produces instead.
 */
class ControlsAtlasTest {

    /** Mirrors {@code McStylePanel.ATLAS_W/ATLAS_H}, which are private to that class. */
    private static final int ATLAS_W = 160;
    private static final int ATLAS_H = 112;
    /** {@code WizardsMetrics.PANEL_SPRITE_SIZE}. Five tones across, three states down. */
    private static final int TILE = 32;
    private static final int TONES = 5;
    private static final int STATES = 3;

    private static final Path ATLAS = Path.of("src", "main", "resources", "assets",
            "wizards_and_beasts", "textures", "gui", "theme", "controls.png");

    @Test
    void atlas_isLargeEnoughForEveryCellTheCodeSamples() throws IOException {
        assertTrue(Files.exists(ATLAS), "controls atlas missing: " + ATLAS.toAbsolutePath()
                + " — regenerate with `python tools/gui_chrome.py --only controls.png`");

        BufferedImage img;
        try (var in = Files.newInputStream(ATLAS)) {
            img = ImageIO.read(in);
        }

        assertEquals(ATLAS_W, img.getWidth(), "controls atlas width drifted from McStylePanel.ATLAS_W");
        assertEquals(ATLAS_H, img.getHeight(), "controls atlas height drifted from McStylePanel.ATLAS_H");

        // The button grid must fit, and the icon band must sit below it rather than on top of the
        // bottom row of buttons.
        assertTrue(TONES * TILE <= img.getWidth(), "atlas too narrow for " + TONES + " tone columns");
        assertTrue(STATES * TILE <= img.getHeight(), "atlas too short for " + STATES + " state rows");
        assertTrue(img.getHeight() > STATES * TILE, "atlas has no icon band below the button grid");
    }
}
