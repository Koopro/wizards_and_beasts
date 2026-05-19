package at.koopro.wizardsandbeasts.item.brew;

import at.koopro.wizardsandbeasts.brew.Brew;
import at.koopro.wizardsandbeasts.brew.BrewPotency;
import at.koopro.wizardsandbeasts.brew.Brews;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import at.koopro.wizardsandbeasts.registry.ConsumableItemRegistry;

/**
 * The single, generic potion item produced by the brewing pillar. The
 * specific brew it carries is stored in the {@link ModDataComponents#BREW_ID}
 * data component as a fully-qualified {@link Brew} id; consumption resolves
 * the brew, scales effect durations by the drinker's {@code potion_potency}
 * skill level (via {@link BrewPotency}), and applies them.
 *
 * <p>Why one item instead of one item per brew: the registry of brewable
 * potions is dynamic (datapack-driven) so we can't know the full set at
 * registry-build time. Anchoring on a single item keeps recipes, models,
 * and creative-tab listing simple, and matches how vanilla potions work.
 */
public class BrewItem extends Item {

    public BrewItem(Properties properties) {
        super(properties);
    }

    /** Convenience: build a stack of a specific brew. */
    public static ItemStack of(Brew brew) {
        ItemStack stack = new ItemStack(at.koopro.wizardsandbeasts.registry.ConsumableItemRegistry.BREW.get());
        stack.set(ModDataComponents.BREW_ID.get(), brew.id());
        return stack;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.FAIL;
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 32;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.DRINK;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level.isClientSide() || !(entity instanceof Player player)) {
            return stack;
        }

        String brewId = stack.get(ModDataComponents.BREW_ID.get());
        Brew brew = Brews.byId(brewId);
        if (brew == null) {
            player.displayClientMessage(Component.literal("\u00A7cThis bottle is empty."), true);
            return stack;
        }

        float potency = (player instanceof ServerPlayer serverPlayer)
                ? BrewPotency.multiplierFor(serverPlayer)
                : 1.0f;

        for (Brew.EffectSpec spec : brew.effects()) {
            MobEffectInstance instance = spec.instantiate(potency);
            player.addEffect(instance);
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_DRINK.value(), player.getSoundSource(), 1.0f, 1.0f);
        stack.consume(1, player);
        player.getCooldowns().addCooldown(stack, 20);
        if (player instanceof ServerPlayer sp) {
            sp.awardStat(Stats.ITEM_USED.get(this));
        }
        return stack;
    }
}
