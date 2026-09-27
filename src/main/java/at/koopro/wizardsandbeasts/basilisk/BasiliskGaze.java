package at.koopro.wizardsandbeasts.basilisk;

import at.koopro.wizardsandbeasts.entity.creature.ai.DeathGazeGoal;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * What a basilisk's eyes do to one person, decided on the server from geometry.
 *
 * <p>Canon (Chamber of Secrets): whoever looks <em>directly</em> into a basilisk's eyes dies. Everyone who saw them
 * only indirectly was petrified — Colin through his camera, Justin through Nearly Headless Nick, Hermione and
 * Penelope in a mirror, Mrs Norris in the water on the floor. Harry survived by keeping his eyes shut. So:
 *
 * <table>
 *   <tr><th>condition</th><th>outcome</th></tr>
 *   <tr><td>blind, blindfolded, or behind a Protego ward</td><td>nothing</td></tr>
 *   <tr><td>out of {@link #RANGE}, wall between, or the basilisk not facing them</td><td>nothing</td></tr>
 *   <tr><td>not looking at its eyes</td><td>nothing — averting your eyes is the counterplay</td></tr>
 *   <tr><td>meeting its eyes through water, or through a spyglass's lens</td><td>{@link Outcome#PETRIFY}</td></tr>
 *   <tr><td>meeting its eyes directly</td><td>{@link Outcome#DEATH}</td></tr>
 * </table>
 *
 * <p>Nothing here acts: {@link DeathGazeGoal} holds the eye contact over a windup and applies the outcome.
 */
public final class BasiliskGaze {

    public enum Outcome { NONE, PETRIFY, DEATH }

    /** How far its eyes carry: the length of a chamber, not the length of a valley. */
    public static final double RANGE = 16.0;
    /** How squarely the basilisk must face someone (cosine; about 60° either side of its head). */
    public static final double FACING = 0.5;
    /** How squarely someone must look at its eyes to meet them (cosine; about 45°). */
    public static final double MEET = 0.7;
    private static final double WATER_STEP = 0.5;

    private BasiliskGaze() {}

    /** The rule, with the world already measured. */
    public static Outcome outcome(boolean immune, boolean clearSight, double facing, double meet, boolean indirect) {
        if (immune || !clearSight || facing < FACING || meet < MEET) {
            return Outcome.NONE;
        }
        return indirect ? Outcome.PETRIFY : Outcome.DEATH;
    }

    /**
     * Measures the world and applies {@link #outcome}.
     *
     * @param eyes   where the basilisk's eyes are
     * @param facing the way its head points (unit vector)
     */
    public static Outcome evaluate(Level level, Vec3 eyes, Vec3 facing, Player victim) {
        if (victim.isSpectator() || victim.isCreative() || !victim.isAlive()) {
            return Outcome.NONE;
        }
        Vec3 target = victim.getEyePosition();
        Vec3 toVictim = target.subtract(eyes);
        double distance = toVictim.length();
        if (distance > RANGE || distance < 1.0e-3) {
            return Outcome.NONE;
        }
        Vec3 dir = toVictim.scale(1.0 / distance);
        double facingDot = facing.dot(dir);
        double meetDot = victim.getViewVector(1.0f).dot(dir.scale(-1));
        boolean clear = clearSight(level, eyes, target, victim);
        boolean indirect = victim.isScoping() || waterBetween(level, eyes, target);
        return outcome(DeathGazeGoal.isGazeImmune(victim), clear, facingDot, meetDot, indirect);
    }

    /**
     * Solid blocks stop it; water does not (it is seen <em>through</em> water, which is what makes that sight
     * indirect). Glass is collision and stops it too — the Chamber's victims saw reflections, not windows.
     */
    static boolean clearSight(Level level, Vec3 from, Vec3 to, Player victim) {
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, victim));
        return hit.getType() == HitResult.Type.MISS;
    }

    /** Whether the line between the two passes through water — Mrs Norris's puddle. */
    static boolean waterBetween(Level level, Vec3 from, Vec3 to) {
        Vec3 step = to.subtract(from);
        int samples = (int) Math.ceil(step.length() / WATER_STEP);
        for (int i = 1; i < samples; i++) {
            Vec3 at = from.add(step.scale(i / (double) samples));
            if (level.getFluidState(net.minecraft.core.BlockPos.containing(at)).is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }
}
