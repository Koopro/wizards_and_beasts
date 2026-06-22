package at.koopro.wizardsandbeasts.event.ability;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.ability.AnimagusAbilityService;
import at.koopro.wizardsandbeasts.ability.AnimagusForms;
import at.koopro.wizardsandbeasts.ability.AnimagusTransformService;
import at.koopro.wizardsandbeasts.ability.PlayerAbilityHelper;
import at.koopro.wizardsandbeasts.registry.ConsumableItemRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Server-side Animagus lifecycle:
 * <ul>
 *   <li>The mandrake-leaf ritual: a wizard who has learned the Animagus discipline
 *       right-clicks a mandrake during a thunderstorm to complete the transformation
 *       and gain the ability to assume a beast form.</li>
 *   <li>While in beast form, the player cannot use items, interact with blocks, or
 *       attack — a beast holds no wand.</li>
 *   <li>Death forces a revert out of beast form.</li>
 * </ul>
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class AnimagusEvents {

    private AnimagusEvents() {}

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // While transformed, items cannot be used.
        if (isLockedBeastForm(player)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }

        ItemStack stack = event.getItemStack();
        if (!stack.is(ConsumableItemRegistry.MANDRAKE.get())) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        // Only a wizard who has learned the discipline can complete the ritual.
        if (!AnimagusTransformService.hasAnimagusSkill(player)) return;
        if (PlayerAbilityHelper.isAnimagusUnlocked(player)) return;

        if (!level.isThundering()) {
            player.displayClientMessage(Component.literal(
                    "The mandrake stirs but the ritual needs the fury of a thunderstorm.")
                    .withStyle(ChatFormatting.DARK_GREEN), true);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.CONSUME);
            return;
        }

        completeRitual(player, level, stack);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    private static void completeRitual(ServerPlayer player, ServerLevel level, ItemStack mandrake) {
        PlayerAbilityHelper.setAnimagusUnlocked(player, true);
        if (PlayerAbilityHelper.getAnimagusFormId(player) == null) {
            PlayerAbilityHelper.setAnimagusFormId(player, AnimagusForms.defaultFormId());
        }
        if (!player.getAbilities().instabuild) {
            mandrake.shrink(1);
        }

        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0, player.getZ(),
                24, 0.5, 0.8, 0.5, 0.01);
        level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(),
                30, 0.5, 0.6, 0.5, 0.05);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 0.6f);

        player.displayClientMessage(Component.literal(
                "The transformation takes hold. You are an Animagus — toggle your beast form with the Animagus key, or /wandb animagus transform.")
                .withStyle(ChatFormatting.GOLD), false);
        player.displayClientMessage(Component.literal(
                "Register with the Ministry (/wandb animagus register) to keep it lawful.")
                .withStyle(ChatFormatting.GRAY), false);
    }

    @SubscribeEvent
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof ServerPlayer player && isLockedBeastForm(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && isLockedBeastForm(player)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isLockedBeastForm(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AnimagusTransformService.forceRevert(player);
        }
    }

    /** Refreshes per-form passive abilities each server tick while in beast form. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!isLockedBeastForm(player)) return;
        AnimagusAbilityService.tick(player, PlayerAbilityHelper.getAnimagusFormId(player));
    }

    /** Cat form lands on its feet — no fall damage. */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!isLockedBeastForm(player)) return;
        if (AnimagusAbilityService.negatesFallDamage(player, PlayerAbilityHelper.getAnimagusFormId(player))) {
            event.setCanceled(true);
        }
    }

    /** True when the player is currently in their Animagus beast form. */
    private static boolean isLockedBeastForm(ServerPlayer player) {
        return PlayerAbilityHelper.isCurrentlyTransformed(player)
                && AnimagusForms.isAnimagusForm(PlayerAbilityHelper.getAnimagusFormId(player));
    }
}
