package at.koopro.wizardsandbeasts.location.ollivanders;

import at.koopro.wizardsandbeasts.currency.billing.MoneyFlow;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.item.wand.WandBlankItem;
import at.koopro.wizardsandbeasts.item.wand.WandCoreMaterialItem;
import at.koopro.wizardsandbeasts.location.LocationService;
import at.koopro.wizardsandbeasts.location.LocationText;
import at.koopro.wizardsandbeasts.market.MarketGoods;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.proficiency.SpellPractice;
import at.koopro.wizardsandbeasts.util.InventoryHandover;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.recipe.WandmakingRecipe;
import at.koopro.wizardsandbeasts.wand.registry.WandCoreDefinition;
import at.koopro.wizardsandbeasts.wand.registry.WandDatapackRegistries;
import at.koopro.wizardsandbeasts.wand.registry.WandWoodDefinition;
import at.koopro.wizardsandbeasts.wand.resonance.WandResonanceSystem;
import at.koopro.wizardsandbeasts.wand.rules.WandRules;
import at.koopro.wizardsandbeasts.wand.rules.WandRulesService;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A wand made to order. The customer brings a shaped blank (the wood) and a core material, one in each hand; the
 * wandmaker takes both and the fee, and the wand is ready to collect some days later — fewer for a better customer.
 *
 * <p>What the commission buys is the workshop, not the materials. A home bench needs a fine enough workshop around
 * it for the better pairings ({@code minimum_bench_tier}) and makes whatever flexibility it is set to; Ollivander's
 * bench has no tier to reach, and he fits the wand to the customer — the flexibility and length that sit best with
 * them ({@link WandResonanceSystem}). The wand still chooses: a pairing that does not resonate is handed over
 * unbonded, as any wand that refuses its maker would be.
 *
 * <p><b>Why it cannot be farmed.</b> Price = fee × wood rarity (1 / 1.5 / 2.5), rounded up to a Sickle; the shipped
 * fee (3 Galleons) is far above anything a trader pays for a finished wand (290 K × rarity unbonded, 348 K × rarity
 * at a fence), so commissioning to sell is a loss before the materials are counted
 * ({@code MarketDataTest.aCommissionCostsMoreThanAnyWandSells}). Legendary woods are refused: an Elder wand is won,
 * never made to order.
 */
@NullMarked
public final class WandCommission implements LocationService {

    /** Days a commission takes for a customer at the offer's minimum tier; one fewer per tier above, never under 1. */
    public static final int BASE_DAYS = 3;

    private static final String KEY = "location.wizards_and_beasts.ollivanders.commission.";

    /** Days the order takes for a customer {@code tiersAbove} the offer's minimum. */
    public static int daysFor(int tiersAbove) {
        return Math.max(1, BASE_DAYS - Math.max(0, tiersAbove));
    }

    /** The price of commissioning a wand of a wood of {@code rarity} at {@code fee}; 0 for a wood never made. */
    public static long priceOf(long fee, String rarity) {
        double multiplier = MarketGoods.rarityMultiplier(rarity);
        if (multiplier <= 0.0 || fee <= 0L) {
            return 0L;
        }
        return CurrencyHelper.roundUpToSickle((long) Math.ceil(fee * multiplier));
    }

    // -- what the customer is holding -----------------------------------------------------------------

    /** The wood and core the customer is offering, and which hand holds each. */
    private record Materials(Identifier wood, Identifier core, InteractionHand blankHand, InteractionHand coreHand) {}

    private static @Nullable Materials materials(ServerPlayer player) {
        InteractionHand blankHand = null;
        InteractionHand coreHand = null;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof WandBlankItem && WandComponents.getWood(stack) != null) {
                blankHand = hand;
            } else if (WandCoreMaterialItem.getCoreKey(stack) != null) {
                coreHand = hand;
            }
        }
        if (blankHand == null || coreHand == null) {
            return null;
        }
        Identifier wood = WandComponents.getWood(player.getItemInHand(blankHand));
        Identifier core = WandCoreMaterialItem.getCoreKey(player.getItemInHand(coreHand));
        return wood == null || core == null ? null : new Materials(wood, core, blankHand, coreHand);
    }

    private static @Nullable WandWoodDefinition wood(RegistryAccess registries, Identifier id) {
        return registries.lookupOrThrow(WandDatapackRegistries.WAND_WOOD_REGISTRY).getValue(id);
    }

    private static Component woodName(RegistryAccess registries, Identifier id) {
        WandWoodDefinition def = wood(registries, id);
        return def == null ? Component.literal(id.getPath()) : def.displayName();
    }

    private static Component coreName(RegistryAccess registries, Identifier id) {
        WandCoreDefinition def = registries.lookupOrThrow(WandDatapackRegistries.WAND_CORE_REGISTRY).getValue(id);
        return def == null ? Component.literal(id.getPath()) : def.displayName();
    }

    static long today(ServerPlayer player) {
        return SpellPractice.day(player.level().getGameTime());
    }

    public static WandCommissionOrder orderOf(ServerPlayer player) {
        return player.getData(ModAttachments.WAND_COMMISSION.get());
    }

    /** Why these materials cannot be made to order, or {@code null} when they can. */
    private static @Nullable Component refusal(ServerPlayer player, @Nullable Materials materials) {
        if (at.koopro.wizardsandbeasts.ministry.law.CodeOfWandUse.forbidden(player)) {
            return at.koopro.wizardsandbeasts.ministry.law.CodeOfWandUse.refusal();
        }
        if (materials == null) {
            return Component.translatable(KEY + "hold");
        }
        WandWoodDefinition def = wood(player.registryAccess(), materials.wood());
        if (def == null || MarketGoods.rarityMultiplier(def.rarity()) <= 0.0) {
            return Component.translatable(KEY + "not_to_order", woodName(player.registryAccess(), materials.wood()));
        }
        if (!WandRules.mayMake(materials.wood(), materials.core())
                || WandRulesService.recipeFor(player.level().getServer(), materials.wood(), materials.core()).isEmpty()) {
            return Component.translatable(KEY + "no_pairing", woodName(player.registryAccess(), materials.wood()),
                    coreName(player.registryAccess(), materials.core()));
        }
        return null;
    }

    private static long price(ServerPlayer player, Use use, Materials materials) {
        WandWoodDefinition def = wood(player.registryAccess(), materials.wood());
        return Use.billed() && def != null ? priceOf(use.fee(), def.rarity()) : 0L;
    }

    // -- the service ---------------------------------------------------------------------------------

    @Override
    public Quote quote(ServerPlayer player, Use use) {
        RegistryAccess registries = player.registryAccess();
        WandCommissionOrder.Order order = orderOf(player).order().orElse(null);
        if (order != null) {
            Component wand = Component.translatable(KEY + "wand", woodName(registries, order.wood()),
                    coreName(registries, order.core()));
            long today = today(player);
            return order.ready(today)
                    ? Quote.yes(Component.translatable(KEY + "collect", wand))
                    : Quote.no(Component.translatable(KEY + "waiting", wand, order.daysLeft(today)));
        }
        Materials materials = materials(player);
        Component refused = refusal(player, materials);
        if (refused != null || materials == null) {
            return Quote.no(refused == null ? Component.translatable(KEY + "hold") : refused);
        }
        return Quote.yes(Component.translatable(KEY + "quote",
                Component.translatable(KEY + "wand", woodName(registries, materials.wood()),
                        coreName(registries, materials.core())),
                CurrencyHelper.formatFromKnuts(price(player, use, materials)), daysFor(use.tiersAbove())));
    }

    @Override
    public boolean perform(ServerPlayer player, Use use) {
        WandCommissionOrder.Order order = orderOf(player).order().orElse(null);
        if (order != null) {
            if (!order.ready(today(player))) {
                PlayerFeedback.refuse(player, LocationText.service(use.offer().service()), quote(player, use).detail());
                return false;
            }
            return collect(player, order);
        }
        Materials materials = materials(player);
        Component refused = refusal(player, materials);
        if (materials == null || refused != null) {
            PlayerFeedback.refuse(player, LocationText.service(use.offer().service()),
                    refused == null ? Component.translatable(KEY + "hold") : refused);
            return false;
        }
        long price = price(player, use, materials);
        if (!use.charge(player, MoneyFlow.WAND_COMMISSION, price)) {
            return false;
        }
        // Paid: the wandmaker takes the materials across the counter in the same tick.
        player.getItemInHand(materials.blankHand()).shrink(1);
        player.getItemInHand(materials.coreHand()).shrink(1);
        player.containerMenu.broadcastChanges();
        int days = daysFor(use.tiersAbove());
        player.setData(ModAttachments.WAND_COMMISSION.get(), WandCommissionOrder.of(new WandCommissionOrder.Order(
                materials.wood(), materials.core(), today(player) + days, price)));
        PlayerFeedback.toast(player, NoticeKind.SUCCESS, Component.translatable(KEY + "placed"),
                Component.translatable(KEY + "placed.body", days));
        return true;
    }

    /** Hands over the finished wand, fitted to the customer, and closes the order. */
    private static boolean collect(ServerPlayer player, WandCommissionOrder.Order order) {
        RegistryAccess registries = player.registryAccess();
        // The recipe as it stands today; a pairing a datapack has since removed is still finished to the
        // same pattern a bench would use, because the customer paid for it.
        WandmakingRecipe recipe = WandRulesService.recipeFor(player.level().getServer(), order.wood(), order.core())
                .orElse(new WandmakingRecipe(order.wood(), order.core(), 0.0f, 9.0f, 14.0f, 1.0f));
        ItemStack best = ItemStack.EMPTY;
        float bestScore = -1.0f;
        float span = recipe.resultLengthMax() - recipe.resultLengthMin();
        for (WandFlexibility flexibility : WandFlexibility.values()) {
            for (int step = 0; step <= 4; step++) {
                ItemStack candidate = recipe.createWand(flexibility, recipe.resultLengthMin() + span * step / 4.0f);
                float score = WandResonanceSystem.computeResonance(player, candidate, registries);
                if (score > bestScore) {
                    best = candidate;
                    bestScore = score;
                }
            }
        }
        player.setData(ModAttachments.WAND_COMMISSION.get(), WandCommissionOrder.NONE);
        WandResonanceSystem.applyResonance(player, best, bestScore, registries);
        InventoryHandover.giveOrDrop(player, best);
        player.containerMenu.broadcastChanges();
        PlayerFeedback.toast(player, NoticeKind.SUCCESS, Component.translatable(KEY + "collected"),
                Component.translatable(KEY + "wand", woodName(registries, order.wood()), coreName(registries, order.core())));
        return true;
    }
}
