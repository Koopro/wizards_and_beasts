package at.koopro.wizardsandbeasts.broom;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

/**
 * Where the broom rig's parts are in the world, for everything that has to agree about it: the seat, the
 * renderer, the slipstream, the crash debris, and the tests that hold all of them against the geometry.
 *
 * <h2>Frame</h2>
 * A broom's position is its rider's <em>feet</em>. The model is drawn lifted by
 * {@link BroomSeat#modelLift()}, so its seat meets a humanoid hip {@link #HIP_HEIGHT} above them. That makes
 * the entity's box the rider's box: it stops at a ceiling before a head goes into it, fits through a doorway
 * a player fits through, and a landing puts feet on the ground rather than knees under it. A parked broom
 * keeps the same lift, so it hovers at seat height waiting to be mounted and nothing jumps when it is.
 *
 * <h2>Local axes</h2>
 * Offsets are authored in the rig's own terms: {@code +y} up, {@code +z} toward the bristles, which is
 * behind the rider. Vanilla's {@code Vec3.yRot(-yaw)} maps local {@code +z} to the entity's <em>forward</em>
 * ({@code Camel} seats its driver at {@code z = +0.5}), so {@link #localToWorld} negates {@code z} first.
 * Getting that wrong is not subtle: the slipstream used to be seeded 2.5 blocks in front of the rider, who
 * then flew through it.
 */
@NullMarked
public final class BroomGeometry {

    private BroomGeometry() {}

    /**
     * Render scale for every broom rig. The rigs are authored 4.1 to 4.6 blocks long, about two and a half
     * times a player; at half size a broom is 2.1 to 2.3 blocks with its nose 0.7 ahead of the rider.
     */
    public static final float MODEL_SCALE = 0.5f;

    /**
     * Height of a rendered humanoid's hip pivot above its feet: {@code LivingEntityRenderer} translates the
     * model 1.501 up and the leg part hangs 12 units back down.
     */
    public static final double HIP_HEIGHT = 0.75;

    /** The rig's {@code fx_tail} anchor, in model units: the bristle tips. */
    private static final double FX_TAIL_Y_UNITS = 4.0;
    private static final double FX_TAIL_Z_UNITS = 40.0;

    /** Model units to rendered blocks, at {@link #MODEL_SCALE}. */
    public static double unitsToBlocks(double units) {
        return units / 16.0 * MODEL_SCALE;
    }

    /**
     * A broom-local offset in world space, turned by the broom's yaw.
     *
     * @param zTowardBristles positive is behind the rider
     */
    public static Vec3 localToWorld(double x, double y, double zTowardBristles, float yawDegrees) {
        return new Vec3(x, y, -zTowardBristles).yRot(-yawDegrees * Mth.DEG_TO_RAD);
    }

    /** From a broom's position to its bristle tips, on a model lifted by {@code modelLift}. */
    public static Vec3 tailOffset(double modelLift, float yawDegrees) {
        return localToWorld(0.0, modelLift + unitsToBlocks(FX_TAIL_Y_UNITS),
                unitsToBlocks(FX_TAIL_Z_UNITS), yawDegrees);
    }
}
