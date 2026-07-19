package at.koopro.wizardsandbeasts.entity.creature.ai;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.chicken.Chicken;

import java.util.EnumSet;

/**
 * Canon weakness for {@code Trait.COCKCROW_WEAKNESS} creatures (the basilisk): a rooster's crow is
 * unconditionally fatal. Vanilla has no separate rooster entity, so any nearby {@link Chicken} stands
 * in; deterministic and dawn-gated rather than a per-tick dice roll, since a rooster's crow is not a
 * chance event in canon — it either crows at dawn or it doesn't.
 */
public final class RoosterCrowWeaknessGoal extends Goal {

    private static final double RANGE = 10.0;
    private static final int INTERVAL = 40;
    private static final long DAWN_WINDOW_TICKS = 200L;

    private final GenericBeastEntity mob;
    private int tick;

    public RoosterCrowWeaknessGoal(GenericBeastEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        return mob.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void tick() {
        if (++tick % INTERVAL != 0) {
            return;
        }
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        if (level.getDayTime() % 24000L >= DAWN_WINDOW_TICKS) {
            return;
        }
        boolean roosterNearby = !level.getEntitiesOfClass(Chicken.class,
                mob.getBoundingBox().inflate(RANGE), Chicken::isAlive).isEmpty();
        if (roosterNearby) {
            mob.hurtServer(level, mob.damageSources().magic(), Float.MAX_VALUE);
        }
    }
}
