package at.koopro.wizardsandbeasts.event.ability;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.ability.AnimagusAbilityService;
import at.koopro.wizardsandbeasts.ability.AnimagusForms;
import at.koopro.wizardsandbeasts.ability.AnimagusTransformService;
import at.koopro.wizardsandbeasts.ability.PlayerAbilityHelper;
import at.koopro.wizardsandbeasts.form.FormSystemAPI;
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
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;

/**
 * Server-side Animagus lifecycle:
 * <ul>
 *   <li>The mandrake-leaf ritual: a wizard who has learned the Animagus discipline
 *       right-clicks a mandrake during a thunderstorm to complete the transformation
 *       and gain the ability to assume a beast form.</li>
 *   <li>While in beast form, the player cannot use items or interact with blocks
 *       (a beast holds no wand), but may still melee — claw and bite.</li>
 *   <li>Death forces a revert out of beast form.</li>
 * </ul>
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class AnimagusEvents {

    private AnimagusEvents() {}

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // The "no item use while transformed" cancel that used to open this method now lives in
        // FormConstraintEvents, shared with the werewolf. It still runs -- and still runs first,
        // because a cancelled event never reaches a later listener -- so a beast cannot start the
        // ritual either.
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

        // One toast, not two chat lines: the second was a footnote to the first, and splitting them
        // meant the Ministry hint scrolled away on its own.
        PlayerFeedback.toast(player, NoticeKind.UNLOCK,
                Component.translatable("animagus.wizards_and_beasts.achieved.title"),
                Component.translatable("animagus.wizards_and_beasts.achieved.body"));
    }

    /**
     * Death ends the transformation, body and all.
     *
     * <p>{@code forceRevert} clears the transformed flag and the passives; it does not touch the
     * <em>form</em>, which lives on the heritage attachment. That attachment is {@code copyOnDeath} and
     * {@code FormLifecycleHandler.onRespawn} faithfully re-applies whatever form it finds, so before
     * this a wizard who died as a cat respawned still shaped like one, with
     * {@code currentlyTransformed} false underneath — a body no toggle could get them out of, because
     * the toggle believed they were already human.
     */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            boolean wasBeast = AnimagusTransformService.isInBeastForm(player);
            AnimagusTransformService.forceRevert(player);
            if (wasBeast) {
                FormSystemAPI.resetToDefault(player);
            }
        }
    }

    /** Refreshes per-form passive abilities each server tick while in beast form. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!isLockedBeastForm(player)) return;
        // Switching the module off mid-session must not leave a beast stranded in its body waiting
        // for a key press that is itself gated. Their chosen form id survives the revert.
        if (AnimagusTransformService.revertIfModuleDisabled(player)) return;
        AnimagusAbilityService.tick(player, PlayerAbilityHelper.getAnimagusFormId(player));
        // Capability-driven behaviour, from the form's own datapack file rather than a switch on its id.
        at.koopro.wizardsandbeasts.animagus.AnimagusFormBinding
                .resolve(PlayerAbilityHelper.getAnimagusFormId(player))
                .ifPresent(def -> at.koopro.wizardsandbeasts.animagus.AnimagusCapabilityService
                        .tickClimb(player, def));
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
        return AnimagusTransformService.isInBeastForm(player);
    }
}
