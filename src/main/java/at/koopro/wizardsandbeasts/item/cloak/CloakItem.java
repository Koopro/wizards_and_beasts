package at.koopro.wizardsandbeasts.item.cloak;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Wearable cloak that equips into the chest slot when used.
 */
public class CloakItem extends Item {
    private final boolean deathlyHallow;

    public CloakItem(Properties properties, boolean deathlyHallow) {
        super(properties);
        this.deathlyHallow = deathlyHallow;
    }

    public boolean isDeathlyHallow() {
        return deathlyHallow;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack inHand = player.getItemInHand(hand);
        ItemStack inChest = player.getItemBySlot(EquipmentSlot.CHEST);

        if (!inChest.isEmpty()) {
            return InteractionResult.PASS;
        }

        ItemStack equipStack = inHand.copyWithCount(1);
        player.setItemSlot(EquipmentSlot.CHEST, equipStack);
        inHand.shrink(1);

        player.awardStat(Stats.ITEM_USED.get(this));
        player.playSound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0F, 1.0F);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        ItemStack targetChest = target.getItemBySlot(EquipmentSlot.CHEST);
        if (!targetChest.isEmpty()) {
            return InteractionResult.PASS;
        }

        ItemStack equipStack = stack.copyWithCount(1);
        target.setItemSlot(EquipmentSlot.CHEST, equipStack);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        target.playSound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0F, 1.0F);
        return InteractionResult.SUCCESS;
    }
}
