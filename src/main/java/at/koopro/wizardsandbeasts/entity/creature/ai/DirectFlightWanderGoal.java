package at.koopro.wizardsandbeasts.entity.creature.ai;

import at.koopro.wizardsandbeasts.creature.schedule.AiBackoff;
import at.koopro.wizardsandbeasts.creature.schedule.AiCadence;
import at.koopro.wizardsandbeasts.creature.schedule.AiMetrics;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/**
 * Random flight for a large-bodied flier, the way vanilla's Ghast wanders: pick a point in the air within reach,
 * sweep the body along the straight line to make sure it fits, and steer there with the move control.
 *
 * <p>Why not {@code WaterAvoidingRandomFlyingGoal}: that one plans every wander with the 3D flying path finder, and
 * the finder classifies every block of the body's box around every node. For a dragon (2.7 blocks wide) one wander
 * search measured around 10 ms — 12.6 % of the whole server thread in the perf arena
 * (documentation/CREATURE_AI_SCHEDULING.md §5). Vanilla never A*-plans a large flier's idle flight (Ghast, Phantom,
 * the Ender Dragon's own node graph); in open sky the planned path is the straight line anyway.
 *
 * <p>What a player sees is the same behaviour: the creature takes off now and then, flies somewhere nearby above the
 * ground, and settles. Destinations are chosen 6–14 blocks away, 2–10 blocks above the ground there, never in water
 * (the old goal avoided water too), and only along a line its body fits through.
 *
 * <p><b>Cost:</b> a wander starts on vanilla's random 1-in-120 roll; choosing a destination is at most
 * {@link #TRIES} candidates, each a heightmap read and a box sweep of at most {@link #MAX_SWEEP_STEPS} collision
 * checks. A creature boxed in where nothing fits backs off (1 s → 5 s) instead of re-rolling every evaluation. No
 * allocation besides the chosen {@code Vec3}; nothing sent to clients beyond vanilla movement.
 */
@NullMarked
public class DirectFlightWanderGoal extends Goal {

    /** Bodies at least this wide wander this way; smaller fliers keep vanilla's planned flight (cheap for them). */
    public static final float LARGE_BODY_WIDTH = 1.5f;
    public static final int TRIES = 6;
    public static final int MAX_SWEEP_STEPS = 24;
    private static final int INTERVAL = 120;
    /** Give up on a destination it has not reached in this long (blocked after all, or pushed off course). */
    private static final int MAX_TICKS = 200;

    private final PathfinderMob mob;
    private final double speed;
    private final AiBackoff search = new AiBackoff(20, AiCadence.SLOW.period());
    private @Nullable Vec3 destination;
    private int ticks;

    public DirectFlightWanderGoal(PathfinderMob mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        // The same gates as vanilla's random stroll: not ridden, not idle-despawning, a 1-in-120 roll.
        if (mob.isVehicle() || mob.getNoActionTime() >= 100
                || mob.getRandom().nextInt(reducedTickDelay(INTERVAL)) != 0) {
            return false;
        }
        long now = mob.level().getGameTime();
        if (!search.ready(now)) {
            AiMetrics.inc(AiMetrics.Metric.BACKOFF_SKIP);
            return false;
        }
        destination = pick();
        if (destination == null) {
            search.fail(now);
            return false;
        }
        search.succeed();
        return true;
    }

    @Override
    public void start() {
        ticks = 0;
        mob.getNavigation().stop();
        steer();
    }

    @Override
    public boolean canContinueToUse() {
        Vec3 to = destination;
        return to != null && ticks < MAX_TICKS && !mob.isVehicle() && mob.position().distanceToSqr(to) > 2.25;
    }

    @Override
    public void tick() {
        ticks++;
        steer();
    }

    @Override
    public void stop() {
        destination = null;
    }

    private void steer() {
        Vec3 to = destination;
        if (to != null) {
            mob.getMoveControl().setWantedPosition(to.x, to.y, to.z, speed);
        }
    }

    /** A reachable point in the air nearby, or {@code null}. */
    private @Nullable Vec3 pick() {
        var random = mob.getRandom();
        for (int i = 0; i < TRIES; i++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            double distance = 6.0 + random.nextDouble() * 8.0;
            double x = mob.getX() + Math.cos(angle) * distance;
            double z = mob.getZ() + Math.sin(angle) * distance;
            int ground = mob.level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
            double y = ground + 2.0 + random.nextDouble() * 8.0;
            BlockPos at = BlockPos.containing(x, y, z);
            if (!mob.level().getFluidState(at).isEmpty() || !mob.level().getFluidState(at.below(2)).isEmpty()) {
                continue; // over or into water, as the planned flight avoided
            }
            Vec3 target = new Vec3(x, y, z);
            if (fits(target)) {
                return target;
            }
        }
        return null;
    }

    /** Whether the body can travel the straight line to {@code target}: vanilla Ghast's sweep. */
    private boolean fits(Vec3 target) {
        Vec3 delta = target.subtract(mob.position());
        double length = delta.length();
        int steps = Mth.clamp(Mth.ceil(length / Math.max(0.5, mob.getBbWidth() * 0.5)), 1, MAX_SWEEP_STEPS);
        Vec3 step = delta.scale(1.0 / steps);
        AABB box = mob.getBoundingBox();
        for (int i = 1; i <= steps; i++) {
            box = box.move(step);
            if (!mob.level().noCollision(mob, box)) {
                return false;
            }
        }
        return true;
    }
}
