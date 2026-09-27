package at.koopro.wizardsandbeasts.entity.flight;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * A winged walker spends its day on the ground and now and then takes to the air: up, a few wide passes, and down
 * again.
 *
 * <p>This is the one place its flying starts and ends when nobody is riding it. It takes off from open ground with
 * sky above, flies three to five waypoints between six and twelve blocks over the terrain, then picks a landing spot
 * and descends until it touches down. A flight never outlasts {@link #MAX_TICKS}, and a waypoint it makes no progress
 * toward for {@link #STUCK_TICKS} is abandoned for the landing — so it cannot hang in the air or wedge in a canopy.
 * Loaded chunks only; the heightmap decides every height, so it never aims inside terrain.
 */
public final class WingedWalkerFlightGoal<T extends PathfinderMob & WingedWalker> extends Goal {

    private static final int MAX_TICKS = 700;
    private static final int STUCK_TICKS = 80;
    private static final double REACHED = 2.5;

    private final T creature;
    /** One in this many ticks, on average, an idle creature takes off. */
    private final int chance;
    private int waypointsLeft;
    private Vec3 target;
    private boolean landing;
    private int ticks;
    private int sinceProgress;
    private double bestDistance;

    public WingedWalkerFlightGoal(T creature, int chance) {
        this.creature = creature;
        this.chance = chance;
        setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (!ModuleManager.isEnabled(Module.CREATURES) || creature.isVehicle() || creature.getTarget() != null
                || !creature.onGround() || creature.isInWater() || !creature.canTakeFlight()) {
            return false;
        }
        if (creature.getRandom().nextInt(reducedTickDelay(chance)) != 0) {
            return false;
        }
        BlockPos pos = creature.blockPosition();
        for (int dy = 1; dy <= 5; dy++) {
            if (!creature.level().getBlockState(pos.above(dy)).getCollisionShape(creature.level(), pos.above(dy)).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void start() {
        creature.setFlying(true);
        creature.getNavigation().stop();
        creature.setDeltaMovement(creature.getDeltaMovement().add(0.0, 0.6, 0.0));
        waypointsLeft = 3 + creature.getRandom().nextInt(3);
        landing = false;
        ticks = 0;
        next();
    }

    @Override
    public boolean canContinueToUse() {
        return creature.isFlying() && !creature.isVehicle() && ticks < MAX_TICKS + 200
                && !(landing && creature.onGround() && ticks > 20);
    }

    @Override
    public void stop() {
        creature.setFlying(false);
        target = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        ticks++;
        if (target == null) {
            next();
            if (target == null) {
                return;
            }
        }
        double distance = creature.position().distanceTo(target);
        if (distance < bestDistance - 0.25) {
            bestDistance = distance;
            sinceProgress = 0;
        } else if (++sinceProgress > STUCK_TICKS && !landing) {
            waypointsLeft = 0;
            next();
            return;
        }
        if (distance < REACHED && !landing) {
            next();
            return;
        }
        if (ticks > MAX_TICKS && !landing) {
            waypointsLeft = 0;
            next();
            return;
        }
        double speed = landing ? 0.8 : 1.0;
        creature.getMoveControl().setWantedPosition(target.x, target.y, target.z, speed);
        creature.getLookControl().setLookAt(target.x, target.y, target.z);
    }

    /** The next waypoint, or the landing spot once the waypoints are spent. */
    private void next() {
        if (!(creature.level() instanceof ServerLevel level)) {
            return;
        }
        boolean land = waypointsLeft <= 0;
        double reach = land ? 10.0 : 18.0;
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = creature.getRandom().nextDouble() * Math.PI * 2.0;
            double r = reach * (0.4 + 0.6 * creature.getRandom().nextDouble());
            int x = (int) Math.floor(creature.getX() + Math.cos(angle) * r);
            int z = (int) Math.floor(creature.getZ() + Math.sin(angle) * r);
            BlockPos column = new BlockPos(x, creature.getBlockY(), z);
            if (!level.isLoaded(column)) {
                continue;
            }
            int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos ground = new BlockPos(x, surface - 1, z);
            if (land && !level.getFluidState(ground).isEmpty()) {
                continue;   // not into a lake
            }
            double y = land ? surface : surface + 6 + creature.getRandom().nextInt(7);
            y = Math.min(y, level.getMaxY() - 4);
            target = new Vec3(x + 0.5, y, z + 0.5);
            landing = land;
            waypointsLeft--;
            bestDistance = creature.position().distanceTo(target);
            sinceProgress = 0;
            return;
        }
        // Nothing sensible nearby: come straight down where it is.
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, creature.getBlockX(), creature.getBlockZ());
        target = new Vec3(creature.getX(), surface, creature.getZ());
        landing = true;
        bestDistance = creature.position().distanceTo(target);
        sinceProgress = 0;
    }
}
