package at.koopro.wizardsandbeasts.item.trinket;

import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

public class PortkeyItem extends Item {
    public PortkeyItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide() || context.getPlayer() == null || !context.getPlayer().isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        ItemStack stack = context.getItemInHand();
        stack.set(ModDataComponents.PORTKEY_TARGET.get(), context.getClickedPos());
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltipAdder, flag);
        var target = stack.get(ModDataComponents.PORTKEY_TARGET.get());
        if (target != null) {
            tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.portkey.linked",
                            target.getX(), target.getY(), target.getZ())
                    .withStyle(ChatFormatting.DARK_AQUA));
        } else {
            tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.portkey.unlinked").withStyle(ChatFormatting.GRAY));
        }
    }
}
