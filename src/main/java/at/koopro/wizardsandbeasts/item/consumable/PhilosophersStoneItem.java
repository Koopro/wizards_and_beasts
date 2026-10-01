package at.koopro.wizardsandbeasts.item.consumable;

import at.koopro.wizardsandbeasts.item.AnimatedItem;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * The Philosopher's Stone. Use it to drink the Elixir of Life — once per in-game day, and what it does is keep the
 * drinker alive ({@link ElixirOfLife}). It used to be a permanent Regeneration II / Absorption III / Resistance aura
 * that also wiped every effect, which is a buff stack, not the Elixir (documentation/CANON_AUDIT.md C-9).
 */
public class PhilosophersStoneItem extends Item implements AnimatedItem {

    public PhilosophersStoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.literal("Blood-red. Produces the Elixir of Life.")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
        tooltipAdder.accept(Component.literal("Created by Nicolas Flamel.")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.philosophers_stone.rule")
                .withStyle(ChatFormatting.GRAY));
        boolean destroyed = stack.getOrDefault(ModDataComponents.PHILOSOPHERS_STONE_DESTROYED.get(), false);
        if (!destroyed) {
            tooltipAdder.accept(Component.literal("[Intact]")
                    .withStyle(ChatFormatting.GOLD));
        } else {
            tooltipAdder.accept(Component.literal("[Destroyed — 1992, by mutual agreement]")
                    .withStyle(ChatFormatting.STRIKETHROUGH, ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!ModuleManager.isEnabled(Module.DARK_ARTS)) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getOrDefault(ModDataComponents.PHILOSOPHERS_STONE_DESTROYED.get(), false)) {
            return InteractionResult.FAIL;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        if (!ElixirOfLife.canDrink(serverPlayer)) {
            serverPlayer.displayClientMessage(Component.translatable("item.wizards_and_beasts.philosophers_stone.wait")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.FAIL;
        }
        ElixirOfLife.drink(serverPlayer);
        ((ServerLevel) level).playSound(null, serverPlayer.blockPosition(),
                SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.8f, 1.4f);
        serverPlayer.displayClientMessage(Component.translatable("item.wizards_and_beasts.philosophers_stone.drunk")
                .withStyle(ChatFormatting.GOLD), true);
        return InteractionResult.SUCCESS;
    }
}
