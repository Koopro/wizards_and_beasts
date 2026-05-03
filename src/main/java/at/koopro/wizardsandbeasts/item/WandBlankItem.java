package at.koopro.wizardsandbeasts.item;

import at.koopro.wizardsandbeasts.wand.WandComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

public class WandBlankItem extends Item {
    public WandBlankItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltipAdder, TooltipFlag flag) {
        Identifier wood = WandComponents.getWood(stack);
        tooltipAdder.accept(Component.literal("Wood: " + (wood == null ? "unknown" : wood)));
    }
}
