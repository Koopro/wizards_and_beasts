package at.koopro.wizardsandbeasts.item.consumable;

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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

public class PhilosophersStoneItem extends Item {

    /** Elixir of Life sustains the drinker — long, potent vitality. */
    private static final int ELIXIR_DURATION = 6000;       // 5 minutes
    private static final int COOLDOWN_TICKS = 6000;        // re-brew once the elixir fades

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
        if (player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.FAIL;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            // The Elixir of Life: enduring vitality. Clear the body of ailment, then sustain it.
            serverPlayer.removeAllEffects();
            serverPlayer.addEffect(new MobEffectInstance(MobEffects.REGENERATION, ELIXIR_DURATION, 1, true, true));
            serverPlayer.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, ELIXIR_DURATION, 2, true, true));
            serverPlayer.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, ELIXIR_DURATION, 0, true, true));
            serverPlayer.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
            ((ServerLevel) level).playSound(null, serverPlayer.blockPosition(),
                    SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.8f, 1.4f);
            serverPlayer.displayClientMessage(
                    Component.literal("You drink the Elixir of Life.").withStyle(ChatFormatting.GOLD), true);
        }
        return InteractionResult.SUCCESS;
    }
}
