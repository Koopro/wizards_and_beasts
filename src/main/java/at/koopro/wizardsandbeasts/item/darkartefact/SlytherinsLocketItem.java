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

public class SlytherinsLocketItem extends Item implements IHorcruxVessel {

    public SlytherinsLocketItem(Properties properties) {
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
        tooltipAdder.accept(Component.literal("Salazar Slytherin's locket. Serpentine S, green stones.")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
        if (isSoulIntact(stack)) {
            tooltipAdder.accept(Component.literal("[Soul fragment bound — wears heavily on the bearer]")
                    .withStyle(ChatFormatting.DARK_PURPLE));
        } else {
            tooltipAdder.accept(Component.literal("[Destroyed — Gryffindor's Sword, 1998]")
                    .withStyle(ChatFormatting.STRIKETHROUGH, ChatFormatting.DARK_RED));
        }
        tooltipAdder.accept(Component.literal("Source: Half-Blood Prince / Deathly Hallows")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!ModuleManager.isEnabled(Module.DARK_ARTS)) {
            return InteractionResult.FAIL;
        }
        // Passive: the dread aura (mental-stability drain + corruption creep) is applied while
        // the horcrux is carried by HorcruxBearerTickHandler — no active use behaviour.
        return InteractionResult.PASS;
    }
}
