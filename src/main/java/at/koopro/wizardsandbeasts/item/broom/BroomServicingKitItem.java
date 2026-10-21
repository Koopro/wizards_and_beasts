package at.koopro.wizardsandbeasts.item.broom;

import at.koopro.wizardsandbeasts.broom.fitting.BroomWorkshop;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * Harry's thirteenth-birthday present from Hermione (<i>Prisoner of Azkaban</i> ch. 1): polish, "silver tail-twig
 * clippers", a compass, a handbook. It was registered as a plain item with a picture and did nothing.
 *
 * <p>It is the tool that takes a broom back to stock: every fitting comes off and is handed back, and a dyed trail
 * is cleared. Fitting goes the other way and needs no tool — a fitting is used on the broom directly. Use it with a
 * broom in the other hand, or on a broom parked in the world; each strip wears the kit by one.
 */
public class BroomServicingKitItem extends Item {

    public BroomServicingKitItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack broom = player.getItemInHand(other);
        if (!(broom.getItem() instanceof BroomItem)) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer server) {
            return stripWith(server, broom, player.getItemInHand(hand), hand)
                    ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        return InteractionResult.SUCCESS;
    }

    /** Strips {@code broom} and wears {@code kit} by one if anything came off. */
    public static boolean stripWith(ServerPlayer player, ItemStack broom, ItemStack kit, InteractionHand hand) {
        if (!BroomWorkshop.strip(player, broom)) {
            return false;
        }
        kit.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.wizards_and_beasts.broomstick_servicing_kit.use")
                .withStyle(ChatFormatting.GRAY));
    }
}
