package at.koopro.wizardsandbeasts.client.beam;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

/**
 * What the current frame draws the level and the first-person hand with, so a point on the held item
 * can be moved to where the level pass puts the same pixel.
 *
 * <p>The two passes disagree twice over ({@code GameRenderer#renderLevel}):
 * <ul>
 *   <li><b>FOV.</b> The level uses the configured FOV times every dynamic modifier — sprint, broom,
 *       Butterbeer, Apparition. The hand is drawn at a base of 70, whatever the setting.</li>
 *   <li><b>View bobbing.</b> The level carries it in its projection matrix, the hand in its pose
 *       stack. A hand point therefore already has the bob in it, and the level would apply it a
 *       second time — a visible bounce at arm's length while walking.</li>
 * </ul>
 * All three values are locals of {@code renderLevel} that no event exposes, so
 * {@code GameRendererMixin} copies them as they go by. Render thread only.
 */
public final class FirstPersonProjection {

    private static final Matrix4f VIEW_BOB = new Matrix4f();
    private static float levelFov = 70f;
    private static float handFov = 70f;

    private FirstPersonProjection() {}

    public static void captureLevelFov(float fov) {
        levelFov = fov;
    }

    public static void captureViewBob(Matrix4fc bob) {
        VIEW_BOB.set(bob);
    }

    public static void captureHandFov(float fov) {
        handFov = fov;
    }

    /** {@link #handToLevel(Vec3, Matrix4fc, float, float)} with this frame's values. */
    public static Vec3 handToLevel(Vec3 handView) {
        return handToLevel(handView, VIEW_BOB, levelFov, handFov);
    }

    /**
     * Moves a point from the hand pass's view space to the level's view space, onto the same pixel
     * and at the same depth.
     *
     * <p>Both passes are symmetric perspectives with the window's aspect, so a pixel is fixed by
     * {@code x / -z} and {@code y / -z} times {@code 1 / tan(fov / 2)}. Scaling x and y by
     * {@code tan(level / 2) / tan(hand / 2)} puts the point on the level's ray through that pixel;
     * undoing the bob then cancels the copy the level projection adds back.
     */
    static Vec3 handToLevel(Vec3 handView, Matrix4fc viewBob, float levelFov, float handFov) {
        double k = Math.tan(Math.toRadians(levelFov) / 2.0) / Math.tan(Math.toRadians(handFov) / 2.0);
        Vector3f point = viewBob.invert(new Matrix4f()).transformPosition(
                new Vector3f((float) (handView.x * k), (float) (handView.y * k), (float) handView.z));
        return new Vec3(point.x, point.y, point.z);
    }
}
