package at.koopro.wizardsandbeasts.item.trunk;

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

public class MoodysTrunkItem extends Item {

    public MoodysTrunkItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.literal("Seven locks. Seven compartments.")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
        tooltipAdder.accept(Component.literal("The seventh contains a man-sized pit.")
                .withStyle(ChatFormatting.DARK_RED));
        int lock = stack.getOrDefault(ModDataComponents.MOODYS_TRUNK_ACTIVE_LOCK.get(), 1);
        tooltipAdder.accept(Component.literal("Current lock: " + lock)
                .withStyle(ChatFormatting.GOLD));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!ModuleManager.isEnabled(Module.WANDS)) {
            return InteractionResult.FAIL;
        }
        // TODO: [storage] open compartment GUI for current lock — mirrors EnchantedTrunk system
        return InteractionResult.PASS;
    }
}
