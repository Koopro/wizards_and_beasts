package at.koopro.wizardsandbeasts.client.vfx;

import at.koopro.wizardsandbeasts.Config;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Size, fade and orientation of the world quads (shockwaves, marks, rune circles). */
class VfxQuadsTest {

    @Test
    void aBurstIsMostlyGrownEarly() {
        assertEquals(1.0f, VfxQuads.sizeAt(VfxQuads.Growth.BURST, 1f, 3f, 0f));
        assertEquals(3.0f, VfxQuads.sizeAt(VfxQuads.Growth.BURST, 1f, 3f, 1f), 1.0e-6);
        assertTrue(VfxQuads.sizeAt(VfxQuads.Growth.BURST, 0f, 1f, 0.3f) > 0.6f);
    }

    @Test
    void aMarkLandsThenHolds() {
        assertEquals(2.0f, VfxQuads.sizeAt(VfxQuads.Growth.LAND, 1f, 2f, 0.2f), 1.0e-6);
        assertEquals(2.0f, VfxQuads.sizeAt(VfxQuads.Growth.LAND, 1f, 2f, 0.9f), 1.0e-6);
    }

    @Test
    void fadesAreSteppedAndBounded() {
        assertEquals(0f, VfxQuads.alphaAt(-1f, 20, 0, 5));
        assertEquals(0f, VfxQuads.alphaAt(20f, 20, 0, 5));
        assertEquals(1f, VfxQuads.alphaAt(5f, 20, 0, 5));
        float fading = VfxQuads.alphaAt(18f, 20, 0, 5);
        assertTrue(fading > 0f && fading < 1f);
        // stepped: a multiple of one sixth
        assertEquals(0f, (fading * 6) % 1f, 1.0e-5);
    }

    @Test
    void basisSpansThePlaneOfTheNormal() {
        for (Vec3 n : new Vec3[]{new Vec3(0, 1, 0), new Vec3(1, 0, 0), new Vec3(0.3, -0.5, 0.8).normalize()}) {
            Vec3[] b = VfxQuads.basis(n);
            assertEquals(0.0, b[0].dot(n), 1.0e-9);
            assertEquals(0.0, b[1].dot(n), 1.0e-9);
            assertEquals(0.0, b[0].dot(b[1]), 1.0e-9);
            assertEquals(1.0, b[0].length(), 1.0e-9);
        }
    }

    @Test
    void capsFollowQuality() {
        assertTrue(VfxQuads.cap(Config.VfxQuality.LOW) < VfxQuads.cap(Config.VfxQuality.MEDIUM));
        assertTrue(VfxQuads.cap(Config.VfxQuality.MEDIUM) < VfxQuads.cap(Config.VfxQuality.HIGH));
        assertTrue(VfxEmitters.cap(Config.VfxQuality.LOW) < VfxEmitters.cap(Config.VfxQuality.HIGH));
    }
}
