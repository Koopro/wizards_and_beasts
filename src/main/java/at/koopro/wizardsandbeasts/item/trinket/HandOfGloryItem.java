package at.koopro.wizardsandbeasts.item.trinket;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

public class HandOfGloryItem extends Item {

    public HandOfGloryItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.literal("Gives light only to the holder. All others are blind.")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
        tooltipAdder.accept(Component.literal("Made from the hand of a hanged man.")
                .withStyle(ChatFormatting.DARK_RED));
        boolean lit = stack.getOrDefault(ModDataComponents.HAND_OF_GLORY_CANDLE_LIT.get(), false);
        if (lit) {
            tooltipAdder.accept(Component.literal("[Burning]")
                    .withStyle(ChatFormatting.GOLD));
        } else {
            tooltipAdder.accept(Component.literal("[Dark]")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!ModuleManager.isEnabled(Module.DARK_ARTS)) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = player.getItemInHand(hand);
        boolean lit = stack.getOrDefault(ModDataComponents.HAND_OF_GLORY_CANDLE_LIT.get(), false);
        stack.set(ModDataComponents.HAND_OF_GLORY_CANDLE_LIT.get(), !lit);
        // While lit, HandOfGloryTickHandler grants the holder night vision and blinds nearby
        // players; snuffing the candle simply lets those short-lived effects lapse.
        if (!level.isClientSide()) {
            player.displayClientMessage(Component.literal(lit ? "The candle gutters out." : "The candle flares to life.")
                    .withStyle(lit ? ChatFormatting.DARK_GRAY : ChatFormatting.GOLD), true);
        }
        return InteractionResult.SUCCESS;
    }
}
