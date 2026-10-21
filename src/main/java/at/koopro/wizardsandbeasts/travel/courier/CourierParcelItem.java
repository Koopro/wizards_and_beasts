package at.koopro.wizardsandbeasts.travel.courier;

import at.koopro.wizardsandbeasts.location.LocationText;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * A sealed courier parcel. Does nothing in the hand: it is delivered by sneak-using a keeper of the place it is
 * addressed to ({@link CourierService#deliverAt}). Lapses in its courier's pack at the deadline.
 */
public class CourierParcelItem extends Item {

    public CourierParcelItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        super.inventoryTick(stack, level, entity, slot);
        if (level.getGameTime() % 20L != 0L || !(entity instanceof ServerPlayer holder)) {
            return;
        }
        CourierParcel parcel = stack.get(ModDataComponents.COURIER_PARCEL.get());
        if (parcel == null) {
            stack.shrink(1);
            return;
        }
        if (parcel.lapsed(level.getGameTime())) {
            CourierService.lapse(holder, stack, parcel);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        CourierParcel parcel = stack.get(ModDataComponents.COURIER_PARCEL.get());
        if (parcel == null) {
            return;
        }
        tooltip.accept(Component.translatable("travel.wizards_and_beasts.courier.tooltip.to",
                LocationText.name(parcel.destination())).withStyle(ChatFormatting.GOLD));
        tooltip.accept(Component.translatable("travel.wizards_and_beasts.courier.tooltip.from",
                LocationText.name(parcel.origin()), parcel.originPos().getX(), parcel.originPos().getZ(),
                (int) CourierService.MIN_DISTANCE).withStyle(ChatFormatting.GRAY));
        var level = context.level();
        if (level != null) {
            long now = level.getGameTime();
            if (parcel.express(now)) {
                tooltip.accept(Component.translatable("travel.wizards_and_beasts.courier.tooltip.express",
                        seconds(parcel.expressBy() - now)).withStyle(ChatFormatting.AQUA));
            }
            tooltip.accept(Component.translatable("travel.wizards_and_beasts.courier.tooltip.deadline",
                    seconds(parcel.deadline() - now)).withStyle(ChatFormatting.GRAY));
        }
        tooltip.accept(Component.translatable("travel.wizards_and_beasts.courier.tooltip.how")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    /** "4:05" from ticks. */
    static String seconds(long ticks) {
        long total = Math.max(0L, ticks / 20L);
        return String.format(java.util.Locale.ROOT, "%d:%02d", total / 60L, total % 60L);
    }
}
