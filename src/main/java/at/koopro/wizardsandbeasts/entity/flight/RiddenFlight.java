package at.koopro.wizardsandbeasts.entity.flight;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Riding a winged walker, on vanilla's ridden contract.
 *
 * <p>The mod has no mount framework beyond brooms, and vanilla already has the right one: a mount answers
 * {@code getControllingPassenger}, {@code getRiddenInput}, {@code tickRidden}, {@code getRiddenSpeed} and
 * {@code travel}, the rider's client simulates the movement (vanilla's rule for any ridden vehicle), and the server
 * takes the positions. This is how the Happy Ghast flies. Each winged walker overrides those five hooks with one line
 * into here, so the Hippogriff and the Thestral fly one way.
 *
 * <p>On the ground: walk, sprint to run, jump to take off (a leap). In the air: fly where the rider looks, jump to
 * climb, and with nothing held glide down — wings, not engines. Touching the ground ends the flight.
 */
public final class RiddenFlight {

    /** Downward pull per tick while gliding with no input. */
    static final double GLIDE_SINK = 0.035;
    static final double TAKEOFF_LEAP = 0.75;

    private RiddenFlight() {}

    /** {@code getRiddenInput}. */
    public static Vec3 input(LivingEntity mount, Player rider) {
        if (mount.onGround()) {
            float forward = rider.zza <= 0.0F ? rider.zza * 0.25F : rider.zza;
            return new Vec3(rider.xxa * 0.5F, 0.0, forward);
        }
        float forward = 0.0F;
        float vertical = 0.0F;
        if (rider.zza != 0.0F) {
            float pitch = rider.getXRot() * Mth.DEG_TO_RAD;
            forward = Mth.cos(pitch) * (rider.zza > 0.0F ? 1.0F : -0.3F);
            vertical = -Mth.sin(pitch) * (rider.zza > 0.0F ? 1.0F : 0.0F);
        }
        if (rider.isJumping()) {
            vertical += 0.6F;
        }
        return new Vec3(rider.xxa * 0.4F, vertical, forward);
    }

    /** {@code getRiddenSpeed}: walking pace, a run when the rider sprints, flying pace in the air. */
    public static float speed(LivingEntity mount, Player rider) {
        float walk = (float) mount.getAttributeValue(Attributes.MOVEMENT_SPEED);
        if (mount.onGround()) {
            return rider.isSprinting() ? walk * 1.8F : walk;
        }
        return (float) mount.getAttributeValue(Attributes.FLYING_SPEED) * 0.6F;
    }

    /** {@code tickRidden}: turn after the rider, pitch with them in the air, and leap into the air on jump. */
    public static void tick(LivingEntity mount, Player rider) {
        float yaw = mount.getYRot() + Mth.wrapDegrees(rider.getYRot() - mount.getYRot()) * 0.2F;
        mount.setYRot(yaw);
        mount.setXRot(mount.onGround() ? 0.0F : rider.getXRot() * 0.5F);
        mount.yRotO = yaw;
        mount.yBodyRot = yaw;
        mount.yHeadRot = yaw;
        if (mount.onGround() && rider.isJumping()) {
            Vec3 motion = mount.getDeltaMovement();
            mount.setDeltaMovement(motion.x, TAKEOFF_LEAP, motion.z);
        }
    }

    /**
     * {@code travel} while ridden and airborne. Returns false when the caller should do its ordinary travel (on the
     * ground, or nobody riding).
     */
    public static <T extends LivingEntity & WingedWalker> boolean travel(T mount, Vec3 input) {
        if (!(mount.getControllingPassenger() instanceof Player) || mount.onGround()) {
            return false;
        }
        mount.flyRidden(input, mount.getSpeed());
        if (input.y <= 0.0) {
            mount.setDeltaMovement(mount.getDeltaMovement().add(0.0, -GLIDE_SINK, 0.0));
        }
        return true;
    }
}
