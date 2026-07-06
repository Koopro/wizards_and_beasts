package at.koopro.wizardsandbeasts.item.wand;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;
import at.koopro.wizardsandbeasts.registry.WandItemRegistry;

public class WandCoreMaterialItem extends Item {
    private final Component tooltip;
    private final Identifier coreKey;

    public WandCoreMaterialItem(Properties properties, Identifier coreKey, Component tooltip) {
        super(properties);
        this.coreKey = coreKey;
        this.tooltip = tooltip;
    }

    public Identifier getCoreKey() {
        return coreKey;
    }

    public static @Nullable Identifier getCoreKey(ItemStack stack) {
        if (stack.getItem() instanceof WandCoreMaterialItem coreItem) {
            return coreItem.coreKey;
        }
        return null;
    }

    public static boolean isBenchCore(ItemStack stack) {
        Item item = stack.getItem();
        return item == WandItemRegistry.PHOENIX_FEATHER.get()
                || item == WandItemRegistry.DRAGON_HEARTSTRING.get()
                || item == WandItemRegistry.UNICORN_HAIR.get();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(tooltip);
    }
}
