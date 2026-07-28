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
    private final @Nullable Component tooltip;
    private final Identifier coreKey;

    public WandCoreMaterialItem(Properties properties, Identifier coreKey, @Nullable Component tooltip) {
        super(properties);
        this.coreKey = coreKey;
        this.tooltip = tooltip;
    }

    /**
     * A core that says nothing extra. {@code ItemDescriptionTooltipHandler} already renders the item's
     * {@code .desc} lang key for everything in this namespace, so a core whose description is written
     * there wants no second line here.
     */
    public WandCoreMaterialItem(Properties properties, Identifier coreKey) {
        this(properties, coreKey, null);
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

    /**
     * Whether the Wandmaker's Bench will take this in its core slot.
     *
     * <p>This used to be a hardcoded list of three items, which is why five of the eight registered core
     * materials — thestral tail hair, veela hair, troll whisker, wampus cat hair and thunderbird tail
     * feather — could not be placed in the bench and never appeared in JEI, despite being tagged into
     * {@code WANDS} and filed under "Wand Cores" in the creative menu. Carrying a core key is now the
     * whole test: identity lives in the item's registration and its {@code wand_cores} definition, so a
     * new core becomes bench-legal by existing rather than by being added to a list here.
     */
    public static boolean isBenchCore(ItemStack stack) {
        return getCoreKey(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltipAdder, TooltipFlag flag) {
        if (tooltip != null) {
            tooltipAdder.accept(tooltip);
        }
    }
}
