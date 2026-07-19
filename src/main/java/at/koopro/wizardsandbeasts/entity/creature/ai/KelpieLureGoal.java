package at.koopro.wizardsandbeasts.entity.creature.ai;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;
import java.util.List;

/**
 * Kelpie lure/disguise: idles as a tame-looking horse (see {@link GenericBeastEntity#isDisguised()} /
 * {@link GenericBeastEntity#mobInteract}) until mounted. After {@code revealAfterMountTicks}, drops the
 * disguise and charges the nearest water, dragging and periodically damaging the rider. Resets back to
 * disguised once the rider is gone.
 *
 * <p>Claims {@code Flag.MOVE} only while mounted, so the normal wander goal keeps the disguised kelpie
 * moving naturally when riderless, and this goal takes over navigation the instant someone climbs on.
 */
public final class KelpieLureGoal extends Goal {

    private static final int WATER_SCAN_INTERVAL = 20;
    private static final int DRAG_DAMAGE_INTERVAL = 20;

    private final GenericBeastEntity mob;
    private final int revealAfterMountTicks;
    private final double dragSpeed;
    private final int waterSearchRadius;

    private int mountedTicks;
    private BlockPos waterTarget;

    public KelpieLureGoal(GenericBeastEntity mob, int revealAfterMountTicks, double dragSpeed, int waterSearchRadius) {
        this.mob = mob;
        this.revealAfterMountTicks = revealAfterMountTicks;
        this.dragSpeed = dragSpeed;
        this.waterSearchRadius = waterSearchRadius;
        setFlags(EnumSet.of(Flag.MOVE));
        mob.setDisguised(true);
    }

    @Override
    public boolean canUse() {
        return !mob.getPassengers().isEmpty();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        mountedTicks = 0;
        waterTarget = null;
    }

    @Override
    public void stop() {
        mountedTicks = 0;
        waterTarget = null;
        mob.setDisguised(true);
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity rider = riderOf(mob);
        if (rider == null) {
            return;
        }

        if (mob.isDisguised()) {
            mountedTicks++;
            if (mountedTicks < revealAfterMountTicks) {
                return;
            }
            mob.setDisguised(false);
        }

        if (mob.tickCount % WATER_SCAN_INTERVAL == 0 || waterTarget == null) {
            waterTarget = findNearestWater();
        }
        if (waterTarget != null) {
            mob.getNavigation().moveTo(waterTarget.getX() + 0.5, waterTarget.getY(), waterTarget.getZ() + 0.5, dragSpeed);
        }

        if (mob.tickCount % DRAG_DAMAGE_INTERVAL == 0 && !mob.level().isClientSide()
                && mob.level() instanceof ServerLevel serverLevel && rider instanceof Player player) {
            player.hurtServer(serverLevel, mob.damageSources().mobAttack(mob), 2.0f);
        }

        if (mob.isInWater() && rider instanceof Player player && !mob.level().isClientSide()
                && mob.level() instanceof ServerLevel serverLevel) {
            player.hurtServer(serverLevel, mob.damageSources().drown(), 4.0f);
        }
    }

    private static LivingEntity riderOf(GenericBeastEntity mob) {
        List<Entity> passengers = mob.getPassengers();
        return passengers.isEmpty() ? null : (passengers.get(0) instanceof LivingEntity living ? living : null);
    }

    private BlockPos findNearestWater() {
        BlockPos origin = mob.blockPosition();
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-waterSearchRadius, -waterSearchRadius / 2, -waterSearchRadius),
                origin.offset(waterSearchRadius, waterSearchRadius / 2, waterSearchRadius))) {
            if (mob.level().getFluidState(pos).is(FluidTags.WATER)) {
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
