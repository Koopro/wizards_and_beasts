package at.koopro.wizardsandbeasts.item.armor;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * The Ritual Robe (Wardrobe 2.0, W4): a wardrobe cut with a hood, raised and lowered with the hood key like the Dark
 * Order robe's ({@link RobeHood}). A {@link RobeCutItem} in every other respect: liveries, ranks, cosmetic wear.
 */
public class RitualRobeItem extends RobeCutItem implements HoodedRobe {

    public RitualRobeItem(Properties properties) {
        super(properties, "ritual_robe", "ritual_black", true);
    }

    @Override
    protected String rendererClassName() {
        return "at.koopro.wizardsandbeasts.client.armor.HoodedRobeCutRenderer";
    }

    /** The hood's state and how to change it, as on the Dark Order robe: a keybind nobody is told of is never found. */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltipAdder, flag);
        tooltipAdder.accept(Component.translatable(RobeHood.isUp(stack)
                        ? "tooltip.wizards_and_beasts.hood_up"
                        : "tooltip.wizards_and_beasts.hood_down")
                .withStyle(ChatFormatting.GRAY));
    }
}
