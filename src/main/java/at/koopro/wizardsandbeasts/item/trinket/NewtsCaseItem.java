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

public class NewtsCaseItem extends Item {

    public NewtsCaseItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.literal("Muggle-worthy switch on the lock.")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
        boolean muggleWorthy = stack.getOrDefault(ModDataComponents.NEWTS_CASE_MUGGLE_WORTHY.get(), true);
        if (muggleWorthy) {
            tooltipAdder.accept(Component.literal("[Muggle-worthy mode ON]")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltipAdder.accept(Component.literal("[Interior accessible]")
                    .withStyle(ChatFormatting.AQUA));
        }
        tooltipAdder.accept(Component.literal("Contains entire ecosystems.")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_PURPLE));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!ModuleManager.isEnabled(Module.POCKET_DIMENSIONS)) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = player.getItemInHand(hand);
        boolean muggleWorthy = stack.getOrDefault(ModDataComponents.NEWTS_CASE_MUGGLE_WORTHY.get(), true);
        if (!muggleWorthy) {
            // TODO: [creature] enter case interior — same architecture as EnchantedTrunk pocket dimension
            return InteractionResult.PASS;
        } else {
            stack.set(ModDataComponents.NEWTS_CASE_MUGGLE_WORTHY.get(), false);
            return InteractionResult.SUCCESS;
        }
    }
}
