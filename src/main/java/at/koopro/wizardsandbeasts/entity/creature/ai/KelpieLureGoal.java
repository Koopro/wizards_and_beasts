package at.koopro.wizardsandbeasts.entity.creature.ai;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.entity.creature.KelpieEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/**
 * The lure, the reveal and the drowning — the {@code lure_disguise} ability's goal.
 *
 * <p>A disguised Kelpie lets someone climb on (see {@link GenericBeastEntity#mobInteract}). For
 * {@code revealAfterMountTicks} it is only a horse that will not be steered and turns for the water — a moment to
 * notice and get off. Then it reveals itself and grips ({@link KelpieEntity#beginGrip}): the rider can no longer
 * dismount, it makes for the nearest water and dives, and under water the rider's breath runs out fast
 * ({@link KelpieEntity#drown}) and it bites now and then. The grip ends — the rider is thrown off — after
 * {@link KelpieEntity#GRIP_TICKS}, when the rider has struck it hard enough, when it is bridled, or when either dies.
 *
 * <p>Costs: water is looked for in a small box, only while gripping, and only every {@link #WATER_SCAN_INTERVAL}
 * ticks; the old goal scanned eighteen thousand blocks a second and dealt drowning damage every single tick.
 */
public final class KelpieLureGoal extends Goal {

    public static final int WATER_SCAN_INTERVAL = 40;
    public static final int BITE_INTERVAL = 40;
    public static final float BITE_DAMAGE = 3.0f;
    private static final int DIVE_DEPTH = 3;

    private final GenericBeastEntity mob;
    private final int revealAfterMountTicks;
    private final double dragSpeed;
    private final int waterSearchRadius;

    private int mountedTicks;
    private int gripTick;
    private @Nullable BlockPos waterTarget;

    public KelpieLureGoal(GenericBeastEntity mob, int revealAfterMountTicks, double dragSpeed, int waterSearchRadius) {
        this.mob = mob;
        this.revealAfterMountTicks = revealAfterMountTicks;
        this.dragSpeed = dragSpeed;
        this.waterSearchRadius = Math.min(waterSearchRadius, 12);
        setFlags(EnumSet.of(Flag.MOVE));
        mob.setDisguised(true);
    }

    @Override
    public boolean canUse() {
        return rider() != null && !(mob instanceof KelpieEntity kelpie && kelpie.isBridled());
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        mountedTicks = 0;
        gripTick = 0;
        waterTarget = null;
    }

    @Override
    public void stop() {
        mountedTicks = 0;
        waterTarget = null;
        if (mob instanceof KelpieEntity kelpie) {
            kelpie.releaseGrip();
        }
        mob.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity rider = rider();
        if (rider == null || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        KelpieEntity kelpie = mob instanceof KelpieEntity k ? k : null;
        if (kelpie != null && !kelpie.isGripping()) {
            if (!mob.isDisguised()) {
                return;   // revealed and not gripping: it let go, and the rider will be off in a moment
            }
            // The horse moment: it walks for the water and will not be steered.
            if (++mountedTicks % WATER_SCAN_INTERVAL == 1) {
                waterTarget = findWater(level);
            }
            moveToWater(false);
            if (mountedTicks >= revealAfterMountTicks) {
                kelpie.beginGrip(rider);
                gripTick = 0;
            }
            return;
        }
        if (kelpie == null || !kelpie.tickGrip()) {
            return;
        }
        gripTick++;
        if (gripTick % WATER_SCAN_INTERVAL == 1 || waterTarget == null) {
            waterTarget = findWater(level);
        }
        moveToWater(true);
        KelpieEntity.drown(rider);
        if (gripTick % BITE_INTERVAL == 0 && mob.isInWater()) {
            rider.hurtServer(level, mob.damageSources().mobAttack(mob), BITE_DAMAGE);
            mob.triggerDeclared("bite");
        }
    }

    private void moveToWater(boolean dive) {
        BlockPos water = waterTarget;
        if (water == null) return;
        double y = water.getY() + (dive && mob.isInWater() ? -DIVE_DEPTH : 0);
        mob.getNavigation().moveTo(water.getX() + 0.5, y, water.getZ() + 0.5, dragSpeed);
        if (dive && mob.isInWater()) {
            mob.getMoveControl().setWantedPosition(water.getX() + 0.5, y, water.getZ() + 0.5, dragSpeed);
        }
    }

    private @Nullable LivingEntity rider() {
        Entity passenger = mob.getFirstPassenger();
        return passenger instanceof LivingEntity living ? living : null;
    }

    /** The nearest deep-enough water in a small box: two blocks of water, one on the other. */
    public @Nullable BlockPos findWater(ServerLevel level) {
        BlockPos origin = mob.blockPosition();
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;
        int r = waterSearchRadius;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-r, -3, -r), origin.offset(r, 2, r))) {
            if (level.getFluidState(pos).is(FluidTags.WATER) && level.getFluidState(pos.below()).is(FluidTags.WATER)) {
                double distSq = pos.distSqr(origin);
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    best = pos.immutable();
                }
            }
        }
        return best;
    }
}
