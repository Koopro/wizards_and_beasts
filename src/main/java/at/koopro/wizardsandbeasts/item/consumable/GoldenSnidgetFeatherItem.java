package at.koopro.wizardsandbeasts.item.consumable;

import at.koopro.wizardsandbeasts.broom.SnidgetFeather;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.NullMarked;

import java.util.function.Consumer;

/**
 * A feather from a Golden Snidget — the fastest bird there is, and very nearly the last of them.
 *
 * <p>Two uses, and they are in tension, which is the point of the item. It is the tail of a
 * Firebolt-tier broom, and it is also the thing you hold in your off hand to fly one faster. A
 * player with one feather has to decide whether to build the broom or to fly the broom they have.
 *
 * <p>Wears out rather than being consumed: {@link SnidgetFeather#DURABILITY} points, one per twenty
 * seconds of powered flight, so a feather is roughly twenty minutes in the air. What it does while
 * held lives in {@link SnidgetFeather}; this class is the item and its label.
 */
@NullMarked
public class GoldenSnidgetFeatherItem extends Item {

    public GoldenSnidgetFeatherItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.golden_snidget_feather.flight",
                        Math.round(SnidgetFeather.SPEED_BONUS * 100.0f))
                .withStyle(ChatFormatting.GOLD));
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.golden_snidget_feather.wear",
                        SnidgetFeather.TICKS_PER_DURABILITY / 20)
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    /** It is gold, and it is the last of something. Both are reasons for it to catch the light. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
