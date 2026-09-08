package at.koopro.wizardsandbeasts.item.brew;

import at.koopro.wizardsandbeasts.brew.Brew;
import at.koopro.wizardsandbeasts.brew.BrewNaming;
import at.koopro.wizardsandbeasts.brew.BrewPotency;
import at.koopro.wizardsandbeasts.brew.Brews;
import at.koopro.wizardsandbeasts.brew.effect.BrewEffectContext;
import at.koopro.wizardsandbeasts.brew.effect.BrewEffectEntry;
import at.koopro.wizardsandbeasts.item.consumable.ConsumedItem;
import at.koopro.wizardsandbeasts.registry.ConsumableItemRegistry;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.Locale;
import java.util.function.Consumer;

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
public class BrewItem extends ConsumedItem {

    public BrewItem(Properties properties) {
        super(properties, 32, ItemUseAnimation.DRINK);
    }

    /**
     * The bottle is named for what is in it, not for the item it is.
     *
     * <p>Without this every potion in the game read "Brew" — fourteen distinct brews, one name, even
     * though each one has carried a {@code displayName} key since the pillar was written. The key comes
     * from {@link BrewNaming} rather than from the {@link Brew} itself because this runs on both sides
     * and the catalogue is datapack content the client only knows about through a packet; see that
     * class for why the derivation is the safe read.
     *
     * <p>An unbottled stack keeps the generic item name, and a brew with no lang entry falls back to a
     * readable form of its own id — a raw translation key sitting in the hotbar looks like a crash.
     */
    @Override
    public Component getName(ItemStack stack) {
        String brewId = stack.get(ModDataComponents.BREW_ID.get());
        if (brewId == null || brewId.isBlank()) {
            return super.getName(stack);
        }
        return Component.translatableWithFallback(BrewNaming.nameKey(brewId), prettify(brewId));
    }

    /** The flavour line, when the brew has one. */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        String brewId = stack.get(ModDataComponents.BREW_ID.get());
        if (brewId == null || brewId.isBlank()) {
            return;
        }
        // Language is common, not client-only, so the check costs nothing and needs no side guard.
        // Asking first is what keeps a datapack brew with no description from showing a blank line.
        String descKey = BrewNaming.descKey(brewId);
        if (Language.getInstance().has(descKey)) {
            tooltipAdder.accept(Component.translatable(descKey)
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    /** {@code wizards_and_beasts:felix_felicis} → {@code Felix Felicis}. Last resort only. */
    private static String prettify(String brewId) {
        String path = brewId.substring(brewId.indexOf(':') + 1);
        StringBuilder out = new StringBuilder(path.length());
        for (String word : path.split("_")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return out.isEmpty() ? path : out.toString();
    }

    /** Convenience: build a stack of a specific brew. */
    public static ItemStack of(Brew brew) {
        ItemStack stack = new ItemStack(at.koopro.wizardsandbeasts.registry.ConsumableItemRegistry.BREW.get());
        stack.set(ModDataComponents.BREW_ID.get(), brew.id());
        return stack;
    }

    @Override
    protected void onConsumed(ItemStack stack, Level level, Player player) {
        String brewId = stack.get(ModDataComponents.BREW_ID.get());
        Brew brew = Brews.byId(brewId);
        if (brew == null) {
            player.displayClientMessage(
                    Component.translatable("brew.wizards_and_beasts.bottle_empty"), true);
            return;
        }

        float potency = (player instanceof ServerPlayer serverPlayer)
                ? BrewPotency.multiplierFor(serverPlayer)
                : 1.0f;

        // Components, not the effect list. Every brew has at least one by the time it reaches here:
        // BrewDefinition wraps a legacy effects list in an apply_effects component at load, so this
        // single path covers both authored styles and there is no second application route to keep
        // in step.
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            // The stack goes through: a Polyjuice bottle is defined by whose hair is in it, which
            // is a property of this bottle and not of the brew every bottle shares.
            BrewEffectEntry.run(brew.components(),
                    BrewEffectContext.onDrink(brew, player, serverLevel, potency, stack));
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_DRINK.value(), player.getSoundSource(), 1.0f, 1.0f);
        stack.consume(1, player);
        player.getCooldowns().addCooldown(stack, 20);
        if (player instanceof ServerPlayer sp) {
            sp.awardStat(Stats.ITEM_USED.get(this));
        }
    }
}
