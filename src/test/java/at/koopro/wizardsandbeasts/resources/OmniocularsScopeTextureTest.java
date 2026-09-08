package at.koopro.wizardsandbeasts.resources;

import at.koopro.wizardsandbeasts.client.trinket.OmniocularsClientExtensions;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scope mask exists, and is actually two lenses.
 *
 * <p>Both halves matter and neither is caught anywhere else. A bad texture id is silent — the game
 * blits the missing-texture magenta over the whole screen and logs nothing — and the shape is worse
 * than silent, because a mask can be perfectly valid, load without complaint, and still be a
 * spyglass. The first geometry {@code tools/omnioculars_scope.py} produced was exactly that: two
 * circles overlapped so far that their union was one wide porthole. It looked deliberate. The waist
 * assertion below is what tells the difference.
 */
class OmniocularsScopeTextureTest {

    private static final Path ASSETS = Path.of("src", "main", "resources", "assets");

    /**
     * Alpha is the mask: 0 is glass the player sees the world through, 255 is the black surround.
     *
     * <p>The lens <em>centres</em> are asserted at exactly {@link #CLEAR}, but anywhere else inside
     * the aperture is only asserted to be {@link #SEE_THROUGH}. The generator lays an inner vignette
     * a little way in from each rim, so a point part-way out reads a few units above zero — that is
     * art, and it is meant to be retunable without a test failing. What must not change is that the
     * player can see through it.
     */
    private static final int CLEAR = 0;
    private static final int OPAQUE = 255;
    private static final int SEE_THROUGH = 64;

    private static Path fileFor(Identifier id) {
        return ASSETS.resolve(id.getNamespace()).resolve(id.getPath());
    }

    private static int alphaAt(BufferedImage image, int x, int y) {
        return (image.getRGB(x, y) >>> 24) & 0xFF;
    }

    @Test
    void theScopeTextureTheItemNamesExistsOnDisk() {
        Path file = fileFor(OmniocularsClientExtensions.SCOPE_TEXTURE);
        assertTrue(Files.isRegularFile(file),
                "Omnioculars scope texture missing: " + file
                        + " — run tools/omnioculars_scope.py");
    }

    /**
     * {@code Gui#renderSpyglassOverlay} blits the mask into a square of {@code min(width, height)}
     * and blacks out everything around it. A non-square texture would be stretched into that square
     * rather than letterboxed, so the lenses would come out as ellipses.
     */
    @Test
    void theMaskIsSquare() throws IOException {
        BufferedImage image = ImageIO.read(fileFor(OmniocularsClientExtensions.SCOPE_TEXTURE).toFile());
        assertEquals(image.getWidth(), image.getHeight(), "the scope mask is not square");
    }

    @Test
    void theSurroundIsOpaqueAndBothLensesAreClear() throws IOException {
        BufferedImage image = ImageIO.read(fileFor(OmniocularsClientExtensions.SCOPE_TEXTURE).toFile());
        int size = image.getWidth();
        int middle = size / 2;
        int lensOffset = Math.round(0.215f * size);

        assertEquals(OPAQUE, alphaAt(image, 0, 0), "the corner of the mask is not opaque");
        assertEquals(OPAQUE, alphaAt(image, size - 1, size - 1), "the corner of the mask is not opaque");
        assertEquals(CLEAR, alphaAt(image, middle - lensOffset, middle), "the left lens is not clear");
        assertEquals(CLEAR, alphaAt(image, middle + lensOffset, middle), "the right lens is not clear");
    }

    /**
     * The one assertion that distinguishes binoculars from a widened spyglass: on the row through
     * the upper part of the lenses, the two apertures are open and the centre between them is not.
     * Push the circles together until that notch closes and this fails.
     */
    @Test
    void thereIsAWaistBetweenTheTwoLenses() throws IOException {
        BufferedImage image = ImageIO.read(fileFor(OmniocularsClientExtensions.SCOPE_TEXTURE).toFile());
        int size = image.getWidth();
        int middle = size / 2;
        int lensOffset = Math.round(0.215f * size);
        // High enough up the lenses to be past the waist, well short of their top edge.
        int row = middle - Math.round(0.19f * size);

        assertTrue(alphaAt(image, middle - lensOffset, row) < SEE_THROUGH,
                "the left lens closed too early");
        assertTrue(alphaAt(image, middle + lensOffset, row) < SEE_THROUGH,
                "the right lens closed too early");
        assertEquals(OPAQUE, alphaAt(image, middle, row),
                "no waist between the lenses — the mask is one porthole, not binoculars");
    }
}
