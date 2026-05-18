package at.koopro.wizardsandbeasts.item.consumable;

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

public class PhilosophersStoneItem extends Item {

    public PhilosophersStoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.literal("Blood-red. Produces the Elixir of Life.")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
        tooltipAdder.accept(Component.literal("Created by Nicolas Flamel.")
                .withStyle(ChatFormatting.DARK_GRAY));
        boolean destroyed = stack.getOrDefault(ModDataComponents.PHILOSOPHERS_STONE_DESTROYED.get(), false);
        if (!destroyed) {
            tooltipAdder.accept(Component.literal("[Intact]")
                    .withStyle(ChatFormatting.GOLD));
        } else {
            tooltipAdder.accept(Component.literal("[Destroyed — 1992, by mutual agreement]")
                    .withStyle(ChatFormatting.STRIKETHROUGH, ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!ModuleManager.isEnabled(Module.WANDS)) {
            return InteractionResult.FAIL;
        }
        // TODO: [alchemy] Elixir of Life production — future alchemy phase
        return InteractionResult.PASS;
    }
}
