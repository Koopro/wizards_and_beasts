package at.koopro.wizardsandbeasts.item.cloak;

import at.koopro.wizardsandbeasts.demiguise.CloakCharges;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
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

    /**
     * How much concealment is left, for every cloak but the Hallow.
     *
     * <p>Without this line a cloak that quietly stopped hiding its wearer would be indistinguishable
     * from a broken mod. The Deathly Hallow prints nothing, because it has nothing to run out of —
     * and its silence here is the tell that it is the real one.
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                java.util.function.Consumer<Component> tooltipAdder, TooltipFlag flag) {
        if (!CloakCharges.isChargeable(stack)) {
            tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.cloak.everlasting")
                    .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
            return;
        }
        int left = CloakCharges.remaining(stack);
        ChatFormatting colour = left <= 0 ? ChatFormatting.DARK_RED
                : left < 60 ? ChatFormatting.GOLD : ChatFormatting.GRAY;
        tooltipAdder.accept(Component.translatable(
                left <= 0 ? "item.wizards_and_beasts.cloak.spent" : "item.wizards_and_beasts.cloak.charges",
                left).withStyle(colour));
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.cloak.reweave")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
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
