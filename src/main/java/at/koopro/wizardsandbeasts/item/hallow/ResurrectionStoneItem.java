package at.koopro.wizardsandbeasts.item.hallow;

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

public class ResurrectionStoneItem extends Item implements IHallowItem {

    public ResurrectionStoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.literal("Turns thrice in the hand to summon shades of the dead.")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
        tooltipAdder.accept(Component.literal("They are not truly here.")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_PURPLE));
        int uses = stack.getOrDefault(ModDataComponents.RESURRECTION_STONE_USES.get(), 0);
        if (uses > 0) {
            tooltipAdder.accept(Component.literal("Times used: " + uses)
                    .withStyle(ChatFormatting.DARK_RED));
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!ModuleManager.isEnabled(Module.DARK_ARTS)) {
            return InteractionResult.FAIL;
        }
        // TODO: [heritage] summon shade effect — requires HeritageAPI integration
        return InteractionResult.PASS;
    }
}
