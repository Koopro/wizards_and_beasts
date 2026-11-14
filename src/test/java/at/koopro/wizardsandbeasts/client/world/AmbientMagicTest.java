package at.koopro.wizardsandbeasts.client.world;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where a glint sits on a block: just outside the face it was drawn for, anywhere across it. */
class AmbientMagicTest {

    @Test
    void aGlintSitsJustOutsideItsFace() {
        RandomSource random = RandomSource.create(5);
        // A coin pile's box: low and inset.
        assertEquals(0.25 + 0.03, AmbientMagic.onFace(0.0, 0.25, 1, random), 1e-9);
        assertEquals(0.125 - 0.03, AmbientMagic.onFace(0.125, 0.875, -1, random), 1e-9);
    }

    @Test
    void acrossTheFaceItStaysInsideTheBox() {
        RandomSource random = RandomSource.create(9);
        for (int i = 0; i < 500; i++) {
            double v = AmbientMagic.onFace(0.125, 0.875, 0, random);
            assertTrue(v >= 0.125 && v <= 0.875, "glint left its block's box: " + v);
        }
    }
}
