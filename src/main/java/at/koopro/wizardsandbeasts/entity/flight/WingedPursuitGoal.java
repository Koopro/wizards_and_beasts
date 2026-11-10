package at.koopro.wizardsandbeasts.entity.flight;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * A winged walker hunts from the air what it cannot reach on foot.
 *
 * <p>On the ground it fights like any beast (the melee goal walks it in). This goal takes over when walking will
 * not do: the target stands higher than it can reach, or is far enough off that a creature with wings would not
 * trot there. Then it takes off and flies passes: climb to a station above and short of the target, stoop straight
 * at it, strike on contact, pull up past it, and come round again. When the target is back within reach on foot it
 * may settle and finish on the ground. A creature that {@link WingedWalker#fightsFromTheAir() fights from the air}
 * takes off for any target and never settles while it has one.
 *
 * <p>Steering is the vanilla flying control aimed at a point, as the Ghast and Phantom fly; there is no 3D path
 * search, which for a dragon-sized box cost milliseconds per plan (Beast Renaissance D24). A leg it makes no headway
 * on for {@link #STUCK_TICKS} is dropped for a higher station, and a hunt never outlasts {@link #MAX_TICKS} in the
 * air. While it climbs it is above its target and airborne, which is what a {@code dive_bomb} ability waits for
 * (D13); that goal outranks this one and takes the stoop when the creature has it.
 */
public final class WingedPursuitGoal<T extends PathfinderMob & WingedWalker> extends Goal {

    /** A target this much higher than the creature's feet cannot be bitten from the ground. */
    static final double OUT_OF_REACH_ABOVE = 2.5;
    /** Beyond this it flies in rather than walks. */
    static final double FLY_IN_DISTANCE = 12.0;
    /** Within this, and not above, it is a ground fight again. */
    static final double GROUND_FIGHT_DISTANCE = 6.0;
    private static final int MAX_TICKS = 900;
    private static final int STUCK_TICKS = 60;
    private static final int STRIKE_COOLDOWN = 25;

    enum Leg { CLIMB, STOOP, PULL_UP }

    private final T creature;
    private Leg leg = Leg.CLIMB;
    private Vec3 aim = Vec3.ZERO;
    private int ticks;
    private int sinceProgress;
    private double bestDistance;
    private int strikeCooldown;
    private int passes;

    public WingedPursuitGoal(T creature) {
        this.creature = creature;
        setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    /** Whether a target at this offset is one to fly at, for a creature standing on the ground. */
    static boolean takesToTheAir(double above, double horizontal) {
        return above > OUT_OF_REACH_ABOVE || horizontal > FLY_IN_DISTANCE;
    }

    /** Whether an airborne hunter should come down and finish on foot. */
    static boolean settles(double above, double horizontal, int passes) {
        return passes > 0 && above <= OUT_OF_REACH_ABOVE && horizontal <= GROUND_FIGHT_DISTANCE;
    }

    @Override
    public boolean canUse() {
        LivingEntity target = creature.getTarget();
        if (target == null || !target.isAlive() || creature.isVehicle() || creature.isInWater()
                || !ModuleManager.isEnabled(Module.CREATURES) || !creature.canTakeFlight()) {
            return false;
        }
        if (!creature.onGround()) {
            return false;   // mid-fall or mid-jump: decide once it has its feet under it
        }
        double above = target.getY() - creature.getY();
        if (!creature.fightsFromTheAir() && !takesToTheAir(above, horizontal(target))) {
            return false;
        }
        BlockPos pos = creature.blockPosition();
        int clear = (int) Math.ceil(creature.getBbHeight()) + 4;
        for (int dy = (int) Math.ceil(creature.getBbHeight()); dy <= clear; dy++) {
            BlockPos over = pos.above(dy);
            if (!creature.level().getBlockState(over).getCollisionShape(creature.level(), over).isEmpty()) {
                return false;   // no room to open its wings here
            }
        }
        return true;
    }

    @Override
    public void start() {
        creature.setFlying(true);
        creature.getNavigation().stop();
        creature.setDeltaMovement(creature.getDeltaMovement().add(0.0, 0.6, 0.0));
        ticks = 0;
        passes = 0;
        strikeCooldown = 0;
        begin(Leg.CLIMB);
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = creature.getTarget();
        if (target == null || !target.isAlive() || creature.isVehicle() || !creature.isFlying()) {
            return false;
        }
        if (ticks > MAX_TICKS) {
            return false;
        }
        // "Within reach on foot" is measured from the ground under it, not from where it happens to be flying:
        // a target on a pillar is level with it right after a pass, and no nearer to being bitten from below.
        int ground = creature.level().getHeight(Heightmap.Types.MOTION_BLOCKING, creature.getBlockX(),
                creature.getBlockZ());
        return creature.fightsFromTheAir() || !(leg != Leg.STOOP && settles(target.getY() - ground, horizontal(target), passes)
                && creature.getY() - target.getY() < 4.0);
    }

    @Override
    public void stop() {
        // Gravity returns with the ground control; a flier takes no fall damage, so it simply comes down.
        creature.setFlying(false);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = creature.getTarget();
        if (target == null) {
            return;
        }
        ticks++;
        if (strikeCooldown > 0) {
            strikeCooldown--;
        }
        if (leg == Leg.STOOP) {
            aim = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);   // a moving target is followed in
        }
        double distance = creature.position().distanceTo(aim);
        if (distance < bestDistance - 0.25) {
            bestDistance = distance;
            sinceProgress = 0;
        } else if (++sinceProgress > STUCK_TICKS) {
            begin(Leg.CLIMB);   // blocked: go round from a fresh station
            return;
        }
        double reach = creature.getBbWidth() * 0.5 + target.getBbWidth() * 0.5 + 1.2;
        switch (leg) {
            case CLIMB -> {
                if (distance < 2.5) {
                    begin(Leg.STOOP);
                }
            }
            case STOOP -> {
                if (creature.distanceTo(target) <= reach + creature.getBbWidth() * 0.5) {
                    if (strikeCooldown == 0 && creature.level() instanceof ServerLevel level) {
                        creature.doHurtTarget(level, target);
                        strikeCooldown = STRIKE_COOLDOWN;
                    }
                    passes++;
                    begin(Leg.PULL_UP);
                }
            }
            case PULL_UP -> {
                if (distance < 2.5) {
                    begin(Leg.CLIMB);
                }
            }
        }
        double speed = leg == Leg.STOOP ? 1.35 : 1.0;
        creature.getMoveControl().setWantedPosition(aim.x, aim.y, aim.z, speed);
        creature.getLookControl().setLookAt(target, 30.0F, 30.0F);
    }

    private void begin(Leg next) {
        LivingEntity target = creature.getTarget();
        if (target == null) {
            return;
        }
        leg = next;
        Vec3 from = creature.position();
        Vec3 to = target.position();
        Vec3 flat = new Vec3(to.x - from.x, 0.0, to.z - from.z);
        Vec3 heading = flat.lengthSqr() < 1.0e-4
                ? Vec3.directionFromRotation(0.0F, creature.getYRot()) : flat.normalize();
        double height = creature.cruiseAltitude() * 0.75 + creature.getBbHeight();
        double top = creature.level().getMaxY() - 4.0;
        aim = switch (next) {
            // Short of the target and above it, so the stoop has a line to fall along.
            case CLIMB -> {
                double back = Math.min(flat.length(), 6.0 + creature.getBbWidth() * 2.0);
                // A blocked leg comes round a quarter turn each time, so it does not retry the same wall.
                Vec3 side = sinceProgress > STUCK_TICKS ? heading.yRot((float) (Math.PI / 2.0)) : heading;
                yield new Vec3(to.x - side.x * back, Math.min(to.y + height, top), to.z - side.z * back);
            }
            case STOOP -> to.add(0.0, target.getBbHeight() * 0.5, 0.0);
            // On through, and up the far side.
            case PULL_UP -> new Vec3(to.x + heading.x * 8.0, Math.min(to.y + height, top), to.z + heading.z * 8.0);
        };
        bestDistance = from.distanceTo(aim);
        sinceProgress = 0;
    }

    private double horizontal(LivingEntity target) {
        double dx = target.getX() - creature.getX();
        double dz = target.getZ() - creature.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
