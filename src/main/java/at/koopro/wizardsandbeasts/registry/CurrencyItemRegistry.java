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

    private CurrencyItemRegistry() {}

    public static void init() {}
}
