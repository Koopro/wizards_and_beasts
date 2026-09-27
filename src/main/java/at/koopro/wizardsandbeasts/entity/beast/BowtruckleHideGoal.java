package at.koopro.wizardsandbeasts.entity.beast;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/**
 * Shy: when a stranger comes near, it scurries to its home tree and presses itself to the trunk — where it goes still
 * and its camouflage takes over ({@link BowtruckleEntity#isCamouflaged}). It stays until the stranger has gone.
 *
 * <p>Strangers are players who are not its bonded person and are not holding something it wants (the tempt goal
 * outranks this one, so food still draws it out). Checked every {@link #CHECK_INTERVAL} ticks, not every tick.
 */
public class BowtruckleHideGoal extends Goal {

    public static final double STARTLE_RANGE = 8.0;
    public static final double SAFE_RANGE = 12.0;
    private static final int CHECK_INTERVAL = 10;

    private final BowtruckleEntity bowtruckle;
    private @Nullable Player stranger;
    private @Nullable BlockPos spot;
    private int nextCheck;

    public BowtruckleHideGoal(BowtruckleEntity bowtruckle) {
        this.bowtruckle = bowtruckle;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (bowtruckle.isDefending() || bowtruckle.homeTree() == null || --nextCheck > 0) {
            return false;
        }
        nextCheck = CHECK_INTERVAL;
        stranger = bowtruckle.nearestStranger(STARTLE_RANGE);
        if (stranger == null) {
            return false;
        }
        spot = hidingSpot(bowtruckle.homeTree());
        return spot != null;
    }

    @Override
    public boolean canContinueToUse() {
        Player s = stranger;
        return s != null && s.isAlive() && !bowtruckle.isDefending()
                && bowtruckle.distanceToSqr(s) < SAFE_RANGE * SAFE_RANGE;
    }

    @Override
    public void start() {
        bowtruckle.startled();
        if (spot != null) {
            bowtruckle.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 1.4);
        }
    }

    @Override
    public void tick() {
        Player s = stranger;
        BlockPos home = bowtruckle.homeTree();
        if (s == null || home == null) return;
        // Face the tree, not the threat: it hides by pressing itself to the bark.
        bowtruckle.getLookControl().setLookAt(home.getX() + 0.5, bowtruckle.getEyeY(), home.getZ() + 0.5);
    }

    @Override
    public void stop() {
        stranger = null;
        spot = null;
    }

    /** A free, standable spot right beside the trunk at or near the ground. */
    @Nullable BlockPos hidingSpot(BlockPos log) {
        var level = bowtruckle.level();
        BlockPos base = log;
        for (int i = 0; i < 8 && level.getBlockState(base.below()).is(net.minecraft.tags.BlockTags.LOGS); i++) {
            base = base.below();   // down to the foot of the trunk
        }
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos at = base.relative(dir);
            if (level.getBlockState(at).isAir() && level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) {
                return at;
            }
        }
        return null;
    }
}
