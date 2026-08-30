package at.koopro.wizardsandbeasts.item.darkartefact;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.NullMarked;

import java.util.function.Consumer;

/**
 * The locket. Passive like the other vessels — the mental-stability drain and corruption creep are
 * applied while it is carried, by {@code HorcruxBearerTickHandler} — but its fragment is the one
 * canon singles out as wearing on whoever wears it, so it says so.
 */
@NullMarked
public class SlytherinsLocketItem extends HorcruxItem {

    public SlytherinsLocketItem(Properties properties) {
        super(properties, "Gryffindor's Sword, 1998");
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.literal("Salazar Slytherin's locket. Serpentine S, green stones.")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
        tooltipAdder.accept(isSoulIntact(stack)
                ? soulBound(" — wears heavily on the bearer")
                : soulDestroyed());
        tooltipAdder.accept(Component.literal("Source: Half-Blood Prince / Deathly Hallows")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
    }
}
