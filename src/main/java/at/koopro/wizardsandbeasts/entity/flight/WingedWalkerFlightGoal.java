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
 *
 * <p>Two more rules since the generic fliers joined (BEAST_RENAISSANCE §4.6). A second instance ({@link #startled})
 * takes off when something has just hurt it and it has nobody to fight: a winged horse leaves by air. And it chooses where to come down
 * ({@link #landingScore}): never water, never a treetop, and a creature that {@link WingedWalker#perches() perches}
 * takes the highest of the spots it looked at.
 */
public final class WingedWalkerFlightGoal<T extends PathfinderMob & WingedWalker> extends Goal {

    private static final int MAX_TICKS = 700;
    private static final int STUCK_TICKS = 80;
    private static final double REACHED = 2.5;
    /** Hurt this recently, with no one to fight back at, it leaves by air instead of waiting for the idle roll. */
    static final int STARTLED_TICKS = 160;
    private static final int LANDING_TRIES = 10;

    private final T creature;
    /** One in this many ticks, on average, an idle creature takes off; zero for the startled-only goal. */
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

    /**
     * The same flight, started only by a blow it has nobody to answer: register it above whatever would have the
     * creature run away on foot. The idle goal keeps its random roll and its low priority.
     */
    public static <T extends PathfinderMob & WingedWalker> WingedWalkerFlightGoal<T> startled(T creature) {
        return new WingedWalkerFlightGoal<>(creature, 0);
    }

    @Override
    public boolean canUse() {
        if (!ModuleManager.isEnabled(Module.CREATURES) || creature.isVehicle() || creature.getTarget() != null
                || !creature.onGround() || creature.isInWater() || !creature.canTakeFlight()) {
            return false;
        }
        if (chance == 0) {
            if (!startled(creature.getLastHurtByMob() != null,
                    creature.tickCount - creature.getLastHurtByMobTimestamp())) {
                return false;
            }
        } else if (creature.getRandom().nextInt(reducedTickDelay(chance)) != 0) {
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

    /** Whether a creature hurt {@code ticksSince} ticks ago by something is still startled enough to take off. */
    static boolean startled(boolean hurtBySomeone, int ticksSince) {
        return hurtBySomeone && ticksSince >= 0 && ticksSince < STARTLED_TICKS;
    }

    /**
     * How good a column is to land on; below zero means not at all.
     *
     * @param surface         first free block over everything that blocks motion
     * @param solidSurface    the same, ignoring leaves
     * @param wet             the block it would stand on is a fluid
     * @param perches         the creature prefers height
     */
    static int landingScore(int surface, int solidSurface, boolean wet, boolean perches) {
        if (wet || surface != solidSurface) {
            return -1;   // a lake, or a canopy
        }
        return perches ? surface + 64 : 0;
    }

    /** The next waypoint, or the landing spot once the waypoints are spent. */
    private void next() {
        if (!(creature.level() instanceof ServerLevel level)) {
            return;
        }
        boolean land = waypointsLeft <= 0;
        double reach = land ? Math.max(10.0, creature.flightReach() * 0.55) : creature.flightReach();
        Vec3 best = null;
        int bestScore = -1;
        for (int attempt = 0; attempt < LANDING_TRIES; attempt++) {
            double angle = creature.getRandom().nextDouble() * Math.PI * 2.0;
            double r = reach * (0.4 + 0.6 * creature.getRandom().nextDouble());
            int x = (int) Math.floor(creature.getX() + Math.cos(angle) * r);
            int z = (int) Math.floor(creature.getZ() + Math.sin(angle) * r);
            BlockPos column = new BlockPos(x, creature.getBlockY(), z);
            if (!level.isLoaded(column)) {
                continue;
            }
            int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            if (!land) {
                double y = surface + creature.cruiseAltitude()
                        + creature.getRandom().nextInt(Math.max(1, creature.cruiseAltitudeSpan()));
                aim(new Vec3(x + 0.5, Math.min(y, level.getMaxY() - 4), z + 0.5), false);
                return;
            }
            int solid = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            boolean wet = !level.getFluidState(new BlockPos(x, surface - 1, z)).isEmpty();
            int score = landingScore(surface, solid, wet, creature.perches());
            if (score > bestScore) {
                bestScore = score;
                best = new Vec3(x + 0.5, Math.min(surface, level.getMaxY() - 4), z + 0.5);
                if (!creature.perches()) {
                    break;   // any dry, open ground will do
                }
            }
        }
        if (best != null) {
            aim(best, true);
            return;
        }
        // Nothing sensible nearby: come straight down where it is.
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, creature.getBlockX(), creature.getBlockZ());
        aim(new Vec3(creature.getX(), surface, creature.getZ()), true);
    }

    private void aim(Vec3 at, boolean land) {
        target = at;
        landing = land;
        waypointsLeft--;
        bestDistance = creature.position().distanceTo(at);
        sinceProgress = 0;
    }
}
