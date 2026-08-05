package at.koopro.wizardsandbeasts.entity.beast.ai;

import at.koopro.wizardsandbeasts.entity.beast.ThestralEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Stands still and lowers the head to browse, driving {@code animation.thestral.graze}.
 *
 * <p>Deliberately not vanilla's {@link net.minecraft.world.entity.ai.goal.EatBlockGoal}: that one is
 * built for sheep, consumes the grass block and regrows wool. A thestral is a carrion eater that also
 * picks at the ground; nothing should be eaten, only the pose held. The goal therefore does no world
 * interaction at all — it takes the MOVE and LOOK flags so wandering and head-tracking cannot fight it,
 * and flips a synced flag the animation controller reads.
 */
public class ThestralGrazeGoal extends Goal {

    /** Roughly once every 20 seconds of idling. */
    private static final int START_CHANCE = 400;
    private static final int MIN_TICKS = 80;
    private static final int MAX_TICKS = 220;

    private final ThestralEntity mob;
    private int ticksLeft;

    public ThestralGrazeGoal(ThestralEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (mob.isInWater() || !mob.onGround() || mob.getTarget() != null) {
            return false;
        }
        return mob.getRandom().nextInt(START_CHANCE) == 0;
    }

    @Override
    public boolean canContinueToUse() {
        return ticksLeft > 0 && mob.onGround() && mob.getTarget() == null;
    }

    @Override
    public void start() {
        ticksLeft = MIN_TICKS + mob.getRandom().nextInt(MAX_TICKS - MIN_TICKS);
        mob.getNavigation().stop();
        mob.setGrazing(true);
    }

    @Override
    public void stop() {
        ticksLeft = 0;
        mob.setGrazing(false);
    }

    @Override
    public void tick() {
        ticksLeft--;
    }
}
