package at.koopro.wizardsandbeasts.entity.niffler.ai;

import at.koopro.wizardsandbeasts.entity.niffler.NifflerEntity;
import at.koopro.wizardsandbeasts.entity.niffler.NifflerTreasure;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Treasure on the ground: find it, go to it, take it.
 *
 * <p>Looks every {@link #SCAN_INTERVAL} ticks within {@link #SEARCH_RANGE} (not every evaluation — that was an entity
 * query per tick per Niffler), picks the most valuable thing it can fit in its pouch ({@link NifflerTreasure}), the
 * nearest among equals, and keeps that target until it has it or it is gone. It will not snatch a stack still under
 * its pickup delay — something a player has only just dropped is not yet lying about. What does not fit in the pouch
 * stays on the ground ({@link NifflerEntity#pickUp}); nothing is lost and nothing is copied.
 */
public class NifflerSeekShinyItemGoal extends Goal {

    public static final double SEARCH_RANGE = 12.0;
    public static final int SCAN_INTERVAL = 20;
    private static final double REACH_SQR = 1.5;

    private final NifflerEntity niffler;
    private @Nullable ItemEntity target;
    private int nextScan;

    public NifflerSeekShinyItemGoal(NifflerEntity niffler) {
        this.niffler = niffler;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (niffler.isCarried() || niffler.isPouchFull() || --nextScan > 0) {
            return false;
        }
        nextScan = SCAN_INTERVAL + niffler.getRandom().nextInt(10);
        target = findPreferred();
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        ItemEntity t = target;
        return t != null && t.isAlive() && !niffler.isCarried() && niffler.getPouch().canAddItem(t.getItem());
    }

    @Override
    public void start() {
        niffler.onSpottedTreasure();
    }

    @Override
    public void tick() {
        ItemEntity t = target;
        if (t == null) return;
        niffler.getLookControl().setLookAt(t, 30f, 30f);
        if (niffler.distanceToSqr(t) < REACH_SQR) {
            niffler.pickUp(t);
            target = null;
        } else if (niffler.getNavigation().isDone() || niffler.tickCount % 10 == 0) {
            niffler.getNavigation().moveTo(t, 1.25);
        }
    }

    @Override
    public void stop() {
        target = null;
        niffler.getNavigation().stop();
    }

    /** The treasure in range it wants most and has room for, nearest first among equals. */
    public @Nullable ItemEntity findPreferred() {
        AABB area = niffler.getBoundingBox().inflate(SEARCH_RANGE);
        List<ItemEntity> items = niffler.level().getEntitiesOfClass(ItemEntity.class, area,
                e -> e.isAlive() && !e.hasPickUpDelay() && NifflerTreasure.value(e.getItem()) > 0
                        && niffler.getPouch().canAddItem(e.getItem()));
        return items.stream()
                .min(Comparator.<ItemEntity>comparingInt(e -> -NifflerTreasure.value(e.getItem()))
                        .thenComparingDouble(niffler::distanceToSqr))
                .orElse(null);
    }
}
