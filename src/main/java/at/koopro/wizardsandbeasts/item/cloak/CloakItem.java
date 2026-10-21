package at.koopro.wizardsandbeasts.item.cloak;

import at.koopro.wizardsandbeasts.demiguise.CloakCharges;
import at.koopro.wizardsandbeasts.equipment.EquipmentAPI;
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
 * Wearable cloak, worn in the Curios back slot ({@link at.koopro.wizardsandbeasts.equipment.EquipmentType#CLOAK}).
 *
 * <p>The item owns only what a cloak <em>is</em> — Hallow or not, its charge tooltip. Where it is worn
 * and what wearing it does live elsewhere, so moving it off the chest slot changed neither.
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

    /**
     * Right-click puts the cloak on, in the Curios back slot.
     *
     * <p>Where the cloak is worn is the equipment layer's business ({@link EquipmentAPI}); what it does
     * there — charges, concealment, the Hallow's exemption — stays in {@link CloakCharges} and
     * {@code CloakEffectsHandler}, which read it through the same API. A cloak already on the back is
     * never knocked off by a second one.
     */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        InteractionResult result = EquipmentAPI.equipFromHand(player, hand, SoundEvents.ARMOR_EQUIP_LEATHER);
        if (result == InteractionResult.SUCCESS) {
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        return result;
    }

    /**
     * Drapes the cloak over someone else.
     *
     * <p>A player (anything with a Curios inventory) gets it on the back like the wearer would. A mob
     * has no Curios slots, so it keeps the old behaviour: the cloak goes in its chest slot, where
     * {@code CloakEffectsHandler#onLivingEquipmentChanged} turns it invisible.
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (EquipmentAPI.canWear(target)) {
            if (!EquipmentAPI.canEquip(target, stack)) {
                return InteractionResult.PASS;
            }
            if (!player.level().isClientSide()) {
                ItemStack given = player.getAbilities().instabuild ? stack.copy() : stack;
                EquipmentAPI.tryEquip(target, given);
            }
            target.playSound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        }

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
