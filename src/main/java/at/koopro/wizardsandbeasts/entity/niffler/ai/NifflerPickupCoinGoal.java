package at.koopro.wizardsandbeasts.entity.niffler.ai;

import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.entity.niffler.NifflerEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

public class NifflerPickupCoinGoal extends Goal {

    private final NifflerEntity niffler;
    private final double searchRange;
    @Nullable
    private ItemEntity targetItem;

    public NifflerPickupCoinGoal(NifflerEntity niffler, double searchRange) {
        this.niffler = niffler;
        this.searchRange = searchRange;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        targetItem = findNearestCoin();
        return targetItem != null && targetItem.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return targetItem != null && targetItem.isAlive() && !targetItem.isRemoved();
    }

    @Override
    public void tick() {
        if (targetItem == null || !targetItem.isAlive()) return;

        niffler.getLookControl().setLookAt(targetItem, 30f, 30f);
        niffler.getNavigation().moveTo(targetItem, 1.2);

        if (niffler.distanceToSqr(targetItem) < 1.5) {
            niffler.getPouch().addItem(targetItem.getItem().copy());
            targetItem.discard();
            targetItem = null;
        }
    }

    @Override
    public void stop() {
        targetItem = null;
        niffler.getNavigation().stop();
    }

    @Nullable
    private ItemEntity findNearestCoin() {
        AABB searchArea = niffler.getBoundingBox().inflate(searchRange);
        List<ItemEntity> items = niffler.level().getEntitiesOfClass(ItemEntity.class, searchArea,
                item -> item.isAlive() && CurrencyHelper.isCanonicalCoin(item.getItem()));
        return items.stream()
                .min(Comparator.comparingDouble(niffler::distanceToSqr))
                .orElse(null);
    }
}
