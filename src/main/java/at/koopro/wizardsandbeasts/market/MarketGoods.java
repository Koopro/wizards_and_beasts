package at.koopro.wizardsandbeasts.market;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.ministry.licence.MinistryLicenceTags;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.treasure.TreasureArtefact;
import at.koopro.wizardsandbeasts.treasure.TreasureArtefacts;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.allegiance.WandAllegianceRules;
import at.koopro.wizardsandbeasts.wand.registry.WandDatapackRegistries;
import at.koopro.wizardsandbeasts.wand.registry.WandWoodDefinition;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The server's reading of a stack against a {@link MarketGood}: does it match, what is one of it worth, and what
 * does the law make of trading it. Every answer comes from the stack itself — its item, tags and components —
 * never from the client.
 */
@NullMarked
public final class MarketGoods {

    /** Goods nobody may trade: a lawful counter refuses them, a fence takes them at a risk. */
    public static final TagKey<Item> CONTRABAND = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "contraband"));

    private MarketGoods() {}

    /** Whether {@code stack} is one of {@code good}, as {@code seller} would hand it over. */
    public static boolean matches(MarketGood good, ItemStack stack, UUID seller) {
        if (stack.isEmpty()) {
            return false;
        }
        return switch (good.kind()) {
            case ITEM -> matchesItem(good, stack);
            case TREASURE -> {
                TreasureArtefact artefact = TreasureArtefacts.of(stack);
                yield artefact != null && (!artefact.cursed() || MarketGood.ANY.equals(good.treasureCondition()));
            }
            case WAND -> matchesWand(good, stack, seller);
        };
    }

    private static boolean matchesItem(MarketGood good, ItemStack stack) {
        Optional<Identifier> id = good.item();
        if (id.isEmpty() || !BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id.get())) {
            return false;
        }
        if (stack.isDamageableItem() && stack.isDamaged()) {
            return false; // a trader buys goods in good order
        }
        if (good.brew().isPresent()) {
            return good.brew().get().equals(stack.get(ModDataComponents.BREW_ID.get()));
        }
        return true;
    }

    private static boolean matchesWand(MarketGood good, ItemStack stack, UUID seller) {
        if (!(stack.getItem() instanceof WandItem) || ModDataComponents.isElderWand(stack)
                || WandComponents.getWood(stack) == null || WandComponents.getCore(stack) == null
                || WandComponents.getIntegrity(stack) <= WandAllegianceRules.BROKEN_AT) {
            return false;
        }
        Optional<UUID> master = WandComponents.getMaster(stack);
        return switch (good.condition().orElse("")) {
            case MarketGood.UNBONDED -> master.isEmpty();
            case MarketGood.STOLEN -> master.isPresent() && !master.get().equals(seller);
            default -> false;
        };
    }

    /**
     * What one {@code stack} is worth to the trader before pressure, demand and the server's scale, in Knuts.
     * 0 when it is worth nothing to them (an Elder-grade wood, a worthless artefact).
     */
    public static double baseValue(MarketGood good, ItemStack stack, RegistryAccess registries) {
        return switch (good.kind()) {
            case ITEM -> good.priceKnuts();
            case TREASURE -> {
                TreasureArtefact artefact = TreasureArtefacts.of(stack);
                if (artefact == null) {
                    yield 0.0;
                }
                double share = artefact.cursed() ? MarketPricing.CURSED_SHARE : 1.0;
                yield artefact.valueKnuts() * (double) good.rate() * share;
            }
            case WAND -> good.priceKnuts() * woodRarity(registries, WandComponents.getWood(stack))
                    * Math.clamp(WandComponents.getIntegrity(stack), 0.0f, 1.0f);
        };
    }

    /**
     * How much a wood's rarity multiplies a wand's price. A legendary wood is worth nothing to a buyer: an Elder
     * wand is won, never sold across a counter.
     */
    static double woodRarity(RegistryAccess registries, @Nullable Identifier wood) {
        if (wood == null) {
            return 0.0;
        }
        WandWoodDefinition def = registries.lookupOrThrow(WandDatapackRegistries.WAND_WOOD_REGISTRY).getValue(wood);
        return rarityMultiplier(def == null ? "common" : def.rarity());
    }

    /** The price multiplier for a wood's {@code rarity} word ({@code wand_woods/*.json}). */
    public static double rarityMultiplier(String rarity) {
        return switch (rarity.toLowerCase(Locale.ROOT)) {
            case "uncommon" -> 1.5;
            case "rare" -> 2.5;
            case "legendary" -> 0.0;
            default -> 1.0;
        };
    }

    /**
     * What the law makes of trading {@code stack} as {@code good}. {@code outsideTheLaw} is whether the counter is a
     * fence's: treasure handed to a fence was never surrendered to the bank whose claim it is, and is stolen there.
     */
    public static DealClass classify(MarketGood good, ItemStack stack, boolean outsideTheLaw) {
        DealClass deal = classify(stack);
        if (good.kind() == MarketGood.Kind.WAND && MarketGood.STOLEN.equals(good.condition().orElse(""))) {
            deal = deal.worse(DealClass.STOLEN);
        }
        if (good.kind() == MarketGood.Kind.TREASURE && outsideTheLaw) {
            deal = deal.worse(DealClass.STOLEN);
        }
        return deal;
    }

    /** The class a stack carries by its item alone (tags). */
    public static DealClass classify(ItemStack stack) {
        if (stack.is(CONTRABAND)) {
            return DealClass.CONTRABAND;
        }
        if (stack.is(MinistryLicenceTags.RESTRICTED_INGREDIENT)) {
            return DealClass.RESTRICTED;
        }
        return DealClass.LEGAL;
    }

    /** The class of a plain item by id, for goods a trader sells (no stack yet). */
    public static DealClass classify(Identifier item) {
        Item resolved = BuiltInRegistries.ITEM.getValue(item);
        return classify(new ItemStack(resolved));
    }
}
