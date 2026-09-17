package at.koopro.wizardsandbeasts.entity.niffler.ai;

import at.koopro.wizardsandbeasts.entity.niffler.NifflerEntity;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;

import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

public class NifflerSeekShinyItemGoal extends Goal {

    public static final TagKey<Item> NIFFLER_SHINY = TagKey.create(
            Registries.ITEM,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "niffler_shiny"));

    private static final double SEARCH_RANGE = 16.0;

    private final NifflerEntity niffler;
    @Nullable private ItemEntity targetItem;

    public NifflerSeekShinyItemGoal(NifflerEntity niffler) {
        this.niffler = niffler;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (niffler.isCarried()) return false;
        if (niffler.isPouchFull()) return false;
        targetItem = findPreferred();
        return targetItem != null && targetItem.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        if (niffler.isCarried() || niffler.isPouchFull()) return false;
        return targetItem != null && targetItem.isAlive() && !targetItem.isRemoved();
    }

    @Override
    public void tick() {
        if (targetItem == null || !targetItem.isAlive()) return;
        niffler.getLookControl().setLookAt(targetItem, 30f, 30f);
        niffler.getNavigation().moveTo(targetItem, 1.2);

        if (niffler.distanceToSqr(targetItem) < 1.5) {
            niffler.getPouch().addItem(targetItem.getItem().copy());
            niffler.onPickedUpShinyItem();
            targetItem.discard();
            targetItem = null;
        }
    }

    @Override
    public void stop() {
        targetItem = null;
        niffler.getNavigation().stop();
    }

    /** The shiny thing in range the Niffler wants most, nearest first among equals. */
    @Nullable
    public ItemEntity findPreferred() {
        AABB area = niffler.getBoundingBox().inflate(SEARCH_RANGE);
        List<ItemEntity> items = niffler.level().getEntitiesOfClass(ItemEntity.class, area,
                e -> e.isAlive() && e.getItem().is(NIFFLER_SHINY));
        // The shiniest first, then the nearest: a Niffler walks past a copper ingot to get to a gold one.
        return items.stream()
                .min(Comparator.<ItemEntity>comparingInt(item -> -at.koopro.wizardsandbeasts.creature.wildlife
                                .WildlifeRules.treasureValue(net.minecraft.core.registries.BuiltInRegistries.ITEM
                                        .getKey(item.getItem().getItem()).getPath()))
                        .thenComparingDouble(niffler::distanceToSqr))
                .orElse(null);
    }
}
