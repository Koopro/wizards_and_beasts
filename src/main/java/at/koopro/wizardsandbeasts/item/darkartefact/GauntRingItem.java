package at.koopro.wizardsandbeasts.item.darkartefact;

import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.NullMarked;

import java.util.function.Consumer;

@NullMarked
public class GauntRingItem extends HorcruxItem {

    public GauntRingItem(Properties properties) {
        super(properties, "Gryffindor's Sword, 1996");
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.literal("Peverell coat of arms. Gaunt family heirloom.")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
        boolean stonePresent = stack.getOrDefault(ModDataComponents.RING_STONE_PRESENT.get(), true);
        if (stonePresent) {
            tooltipAdder.accept(Component.literal("Bears the Resurrection Stone.")
                    .withStyle(ChatFormatting.AQUA));
        } else {
            tooltipAdder.accept(Component.literal("Stone removed.")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        appendSoulState(stack, tooltipAdder);
    }
}
