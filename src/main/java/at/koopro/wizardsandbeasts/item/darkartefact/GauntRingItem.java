package at.koopro.wizardsandbeasts.item.darkartefact;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

public class GauntRingItem extends Item implements IHorcruxVessel {

    public GauntRingItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isSoulIntact(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.SOUL_FRAGMENT_INTACT.get(), true);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isSoulIntact(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.literal("Peverell coat of arms. Gaunt family heirloom.")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
        boolean stonePresent = stack.getOrDefault(ModDataComponents.RING_STONE_PRESENT.get(), true);
        if (stonePresent) {
            tooltipAdder.accept(Component.literal("Bears the Resurrection Stone.")
                    .withStyle(ChatFormatting.AQUA));
        } else {
            tooltipAdder.accept(Component.literal("Stone removed.")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (isSoulIntact(stack)) {
            tooltipAdder.accept(Component.literal("[Soul fragment bound]")
                    .withStyle(ChatFormatting.DARK_PURPLE));
        } else {
            tooltipAdder.accept(Component.literal("[Destroyed — Gryffindor's Sword, 1996]")
                    .withStyle(ChatFormatting.STRIKETHROUGH, ChatFormatting.DARK_RED));
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!ModuleManager.isEnabled(Module.DARK_ARTS)) {
            return InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }
}
