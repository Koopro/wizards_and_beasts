package at.koopro.wizardsandbeasts.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

public class WandCoreMaterialItem extends Item {
    private final Component tooltip;

    public WandCoreMaterialItem(Properties properties, Component tooltip) {
        super(properties);
        this.tooltip = tooltip;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(tooltip);
    }
}
