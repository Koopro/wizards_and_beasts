package at.koopro.wizardsandbeasts.item.currency;

import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

public class LeprechaunGoldItem extends Item {

    private static final int LIFESPAN_TICKS = 6000; // ~5 minutes

    public LeprechaunGoldItem(Properties properties) {
        super(properties);
    }

    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide()) return;

        Long createdAt = stack.get(ModDataComponents.CREATION_TICK.get());
        if (createdAt == null) {
            stack.set(ModDataComponents.CREATION_TICK.get(), level.getGameTime());
            return;
        }

        if (level.getGameTime() - createdAt >= LIFESPAN_TICKS) {
            stack.shrink(stack.getCount());
        }
    }

    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.literal("Glimmers suspiciously...").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
    }
}
