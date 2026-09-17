package at.koopro.wizardsandbeasts.client.beam;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FirstPersonProjection} moves a point drawn by the first-person hand pass to where the level
 * pass draws the same pixel. Checked against matrices built the way vanilla builds them
 * ({@code GameRenderer#getProjectionMatrix}, the 3D HUD buffer, a {@code bobView}-shaped pose), since
 * the whole problem is that the two passes disagree.
 */
class FirstPersonProjectionTest {

    private static final float ASPECT = 16f / 9f;
    private static final double EPS = 1e-4;
    /** A wand tip in the hand pass's view space: right of centre, below it, arm's length ahead. */
    private static final Vec3 TIP = new Vec3(0.42, -0.35, -0.72);

    private static Matrix4f levelProjection(float fov) {
        return new Matrix4f().perspective((float) Math.toRadians(fov), ASPECT, 0.05f, 256f);
    }

    private static Matrix4f handProjection(float fov) {
        return new Matrix4f().perspective((float) Math.toRadians(fov), ASPECT, 0.05f, 100f);
    }

    /** Shaped like vanilla's bobView mid-stride: a sideways and downward shift, a roll, a nod. */
    private static Matrix4f walkingBob() {
        return new Matrix4f()
                .translate(0.03f, -0.08f, 0f)
                .rotateZ((float) Math.toRadians(1.5))
                .rotateX((float) Math.toRadians(2.5));
    }

    private static Vector3f pixel(Matrix4f clip, Vec3 point) {
        return clip.transformProject(new Vector3f((float) point.x, (float) point.y, (float) point.z));
    }

    @Test
    void handToLevel_landsOnTheSamePixel() {
        float levelFov = 95f;
        float handFov = 70f;
        Matrix4f bob = walkingBob();

        Vec3 level = FirstPersonProjection.handToLevel(TIP, bob, levelFov, handFov);

        // The hand pass: its projection over a pose that already carries the bob — TIP is that point.
        Vector3f handPixel = pixel(handProjection(handFov), TIP);
        // The level pass: its projection with the bob multiplied in, over the level view-space point.
        Vector3f levelPixel = pixel(levelProjection(levelFov).mul(bob), level);

        assertEquals(handPixel.x, levelPixel.x, EPS);
        assertEquals(handPixel.y, levelPixel.y, EPS);
    }

    @Test
    void handToLevel_withMatchingFovAndNoBob_isTheSamePoint() {
        Vec3 level = FirstPersonProjection.handToLevel(TIP, new Matrix4f(), 70f, 70f);
        assertEquals(0.0, level.distanceTo(TIP), EPS);
    }

    @Test
    void handToLevel_widerLevelFov_pushesThePointOutward() {
        Vec3 level = FirstPersonProjection.handToLevel(TIP, new Matrix4f(), 110f, 70f);
        assertEquals(TIP.z, level.z, EPS, "depth is kept");
        assertTrue(Math.abs(level.x) > Math.abs(TIP.x) && Math.abs(level.y) > Math.abs(TIP.y),
                "a wider level FOV shrinks the world on screen, so the point moves out to stay on its pixel");
    }
}
