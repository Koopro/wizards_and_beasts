package at.koopro.wizardsandbeasts.item.darkartefact;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.NullMarked;

import java.util.function.Consumer;

@NullMarked
public class HufflepuffsCupItem extends HorcruxItem {

    public HufflepuffsCupItem(Properties properties) {
        super(properties, "Basilisk fang, 1998");
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.literal("Helga Hufflepuff's golden goblet. Two handles, badger engraving.")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
        appendSoulState(stack, tooltipAdder);
    }
}
