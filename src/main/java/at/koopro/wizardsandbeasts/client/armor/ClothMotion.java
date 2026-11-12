package at.koopro.wizardsandbeasts.client.armor;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The robe skirt's motion rule, as pure arithmetic (WARDROBE_RENAISSANCE §B5 and §C-look, option B).
 *
 * <p>A wardrobe cut carries three cloth bones under {@code armorBody}: {@code clothLegR} and
 * {@code clothLegL}, the long lower halves, each pivoting on its own hip joint; and
 * {@code clothHipFront}, the front of the short hip skirt. Inputs are the vanilla {@code ModelPart}
 * x-rotations (radians, forward swing negative); outputs are GeckoLib bone x-rotations for those
 * child bones (radians, forward positive — GeckoLib's armour code turns a slot bone by
 * {@code -xRot}, and the children follow the same convention).
 *
 * <p>The children inherit {@code armorBody}'s own pitch, so a lower half is turned by the leg's
 * pitch <em>relative to the body</em>: crouching tilts the body forward while the legs stay upright,
 * and the halves counter-rotate to keep hanging with the legs.
 *
 * <p>Owner-approved on the offline walk/sprint/seated sheets; the numbers match
 * {@code tools/wardrobe_preview.py#cloth_pose}.
 */
public final class ClothMotion {

    /**
     * How closely a lower half follows its leg. 1 since 2026-11-12 (owner: "the bottom part should move with the
     * legs, left half with the left leg, right half with the right"): each half is drawn as part of its leg, like
     * the skin's own trouser layer, so it can never part from it.
     */
    public static final float LEG_FOLLOW = 1.0f;

    /** Where a lower half's bone pivots, x by side: the hip joint, as {@code tools/wardrobe_model.py} places it. */
    public static final float HIP_X = 1.9f;
    /** The hip joint's depth below the body's pivot (the neck), in model units. */
    public static final float HIP_DROP = 12f;

    /** How far the hip skirt's front lifts with the forward pitch both legs share (seated). */
    public static final float SEAT_LIFT = 0.8f;

    public static final String LEG_R = "clothLegR";
    public static final String LEG_L = "clothLegL";
    public static final String HIP_FRONT = "clothHipFront";

    public static final String CAPE_ROOT = "capeRoot";
    public static final String CAPE_HEM = "capeHem";

    /** How far behind the stride's trailing robe half the cape's lower part keeps, in degrees. */
    public static final float CAPE_CLEARANCE_DEG = 4f;

    /**
     * Share of the stride's amplitude the cape's lower part follows back. Not the full reach of the
     * trailing robe half: in game a walk swings the legs up to 80 degrees, and clearing that stands the
     * cape out like a flag at an ordinary walk (seen in the 2026-11-04 capture). Vanilla's own cape
     * does not clear a full swing either; a brief brush at the peak of a step is accepted.
     */
    public static final float CAPE_CLEAR_SHARE = 0.5f;

    /** Most the lower part folds beyond vanilla's cape angle, in degrees. */
    public static final float CAPE_MAX_FOLD_DEG = 18f;

    private ClothMotion() {}

    /**
     * Bone x-rotation of a cape's upper part: vanilla's own cape angle,
     * {@code 6 + capeLean / 2 + capeFlap} degrees ({@code PlayerCapeModel#setupAnim}), swinging back —
     * which in GeckoLib's sign is negative.
     */
    public static float capeRoot(float capeLean, float capeFlap) {
        return -(float) Math.toRadians(vanillaCapeDegrees(capeLean, capeFlap));
    }

    /**
     * Extra fold of the cape's lower part: whatever the robe's trailing half needs beyond the upper
     * part's angle over a stride, up to {@link #CAPE_MAX_FOLD_DEG}. Read from the stride's amplitude ({@code 1.4 * walkAnimationSpeed},
     * vanilla's leg swing), not the instantaneous leg, so the cape holds steady instead of bouncing
     * every step. Zero when standing.
     *
     * @param strideAmplitude vanilla's leg swing amplitude in radians
     */
    public static float capeHem(float capeLean, float capeFlap, float strideAmplitude) {
        if (strideAmplitude <= 0f) {
            return 0f;
        }
        float needDeg = (float) Math.toDegrees(strideAmplitude) * CAPE_CLEAR_SHARE + CAPE_CLEARANCE_DEG;
        float extra = Math.clamp(needDeg - vanillaCapeDegrees(capeLean, capeFlap), 0f, CAPE_MAX_FOLD_DEG);
        return -(float) Math.toRadians(extra);
    }

    private static float vanillaCapeDegrees(float capeLean, float capeFlap) {
        return 6f + capeLean / 2f + capeFlap;
    }

    /**
     * Bone translation that puts a lower half's hip pivot on its leg's pivot, in GeckoLib bone units.
     *
     * <p>The half is a child of {@code armorBody}, so it rides the body's pivot and rotation. Crouching, riding a
     * broom or any pose that leans the body moves the body (down, forward) while the legs stay or move back; without
     * this the halves stay hung on the body's hip line and come away from the legs. The miss between where the body
     * carries the hip ({@code bodyPos + R_body * hip}) and where the leg's pivot is, taken into the body's own frame,
     * is the bone's offset. Model space is y-down; GeckoLib's bones are y-up, so y changes sign (its
     * {@code ArmorSegment} matchers do the same).
     *
     * @param side -1 for the right half, +1 for the left
     */
    public static Vector3f lowerHalfOffset(int side, float bodyX, float bodyY, float bodyZ,
                                           float bodyXRot, float bodyYRot, float bodyZRot,
                                           float legX, float legY, float legZ) {
        Quaternionf bodyRot = new Quaternionf().rotationZYX(bodyZRot, bodyYRot, bodyXRot);
        Vector3f carried = bodyRot.transform(new Vector3f(side * HIP_X, HIP_DROP, 0f)).add(bodyX, bodyY, bodyZ);
        Vector3f miss = new Vector3f(legX, legY, legZ).sub(carried);
        bodyRot.conjugate().transform(miss);
        return miss.set(miss.x, -miss.y, miss.z);
    }

    /** Bone x-rotation of a lower half, from its leg's and the body's {@code ModelPart} x-rotations. */
    public static float lowerHalf(float legXRot, float bodyXRot) {
        return -(legXRot - bodyXRot) * LEG_FOLLOW;
    }

    /**
     * Bone x-rotation of the hip skirt's front. Walking swings one leg forward and the other back,
     * so the share is zero and the front hangs; sitting brings both forward and it lifts into a lap.
     */
    public static float hipFront(float rightLegXRot, float leftLegXRot) {
        float shared = Math.min(-rightLegXRot, -leftLegXRot);
        return Math.max(0f, shared) * SEAT_LIFT;
    }
}
