package at.koopro.wizardsandbeasts.item.darkartefact;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.NullMarked;

import java.util.function.Consumer;

@NullMarked
public class RavenclawsDiademItem extends HorcruxItem {

    public RavenclawsDiademItem(Properties properties) {
        super(properties, "Fiendfyre, 1998");
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.literal("Wit beyond measure is man's greatest treasure.")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.AQUA));
        appendSoulState(stack, tooltipAdder);
    }
}
