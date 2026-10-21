package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.item.currency.CoinItem;
import at.koopro.wizardsandbeasts.item.currency.DragotItem;
import at.koopro.wizardsandbeasts.item.currency.LeprechaunGoldItem;
import at.koopro.wizardsandbeasts.item.spell.gamp.CounterfeitGalleonItem;
import net.neoforged.neoforge.registries.DeferredItem;

public final class CurrencyItemRegistry {

    public static final DeferredItem<CoinItem> KNUT =
            ModItems.ITEMS.registerItem("knut", props -> new CoinItem(props, "knut"));
    public static final DeferredItem<CoinItem> SICKLE =
            ModItems.ITEMS.registerItem("sickle", props -> new CoinItem(props, "sickle"));
    public static final DeferredItem<CoinItem> GALLEON =
            ModItems.ITEMS.registerItem("galleon", props -> new CoinItem(props, "galleon"));
    public static final DeferredItem<CounterfeitGalleonItem> COUNTERFEIT_GALLEON =
            ModItems.ITEMS.registerItem("counterfeit_galleon", props -> new CounterfeitGalleonItem(props.stacksTo(64)));
    public static final DeferredItem<LeprechaunGoldItem> LEPRECHAUN_GOLD =
            ModItems.ITEMS.registerItem("leprechaun_gold", LeprechaunGoldItem::new);
    /** Foreign money, with its own rate, its own acceptance rules and its own forgeries. */
    public static final DeferredItem<DragotItem> DRAGOT =
            ModItems.ITEMS.registerItem("dragot", DragotItem::new);

    /**
     * Cursed treasure from the tombs Gringotts sends its curse-breakers into (treasure/). One stack each: every
     * find carries its own curse, depth and worth ({@code TreasureArtefact}).
     */
    public static final DeferredItem<at.koopro.wizardsandbeasts.treasure.TreasureArtefactItem> ANCIENT_GOBLET =
            ModItems.ITEMS.registerItem("ancient_goblet",
                    props -> new at.koopro.wizardsandbeasts.treasure.TreasureArtefactItem(props.stacksTo(1)));
    public static final DeferredItem<at.koopro.wizardsandbeasts.treasure.TreasureArtefactItem> WARDED_AMULET =
            ModItems.ITEMS.registerItem("warded_amulet",
                    props -> new at.koopro.wizardsandbeasts.treasure.TreasureArtefactItem(props.stacksTo(1)));
    public static final DeferredItem<at.koopro.wizardsandbeasts.treasure.TreasureArtefactItem> SEALED_CASKET =
            ModItems.ITEMS.registerItem("sealed_casket",
                    props -> new at.koopro.wizardsandbeasts.treasure.TreasureArtefactItem(props.stacksTo(1)));
    public static final DeferredItem<at.koopro.wizardsandbeasts.treasure.TreasureArtefactItem> TOMB_MEDALLION =
            ModItems.ITEMS.registerItem("tomb_medallion",
                    props -> new at.koopro.wizardsandbeasts.treasure.TreasureArtefactItem(props.stacksTo(1)));

    private CurrencyItemRegistry() {}

    public static void init() {}
}
