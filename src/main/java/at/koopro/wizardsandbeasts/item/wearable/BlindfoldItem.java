package at.koopro.wizardsandbeasts.item.wearable;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Wearable blindfold. Equips into the head slot when used; grants immunity to a basilisk's death
 * gaze (see {@code DeathGazeGoal}) at the cost of blocking normal vision — the canonical
 * "look away" counterplay made deliberate rather than incidental.
 */
public class BlindfoldItem extends Item {
    public BlindfoldItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack inHand = player.getItemInHand(hand);
        ItemStack inHead = player.getItemBySlot(EquipmentSlot.HEAD);

        if (!inHead.isEmpty()) {
            return InteractionResult.PASS;
        }

        ItemStack equipStack = inHand.copyWithCount(1);
        player.setItemSlot(EquipmentSlot.HEAD, equipStack);
        inHand.shrink(1);

        player.awardStat(Stats.ITEM_USED.get(this));
        player.playSound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0F, 1.0F);
        return InteractionResult.SUCCESS;
    }
}
