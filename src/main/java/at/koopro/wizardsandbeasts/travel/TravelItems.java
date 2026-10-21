package at.koopro.wizardsandbeasts.travel;

import at.koopro.wizardsandbeasts.registry.ModItems;
import at.koopro.wizardsandbeasts.travel.courier.CourierParcelItem;
import net.neoforged.neoforge.registries.DeferredItem;

/** Items the travel systems hand out: the courier parcel and the Floo directory. */
public final class TravelItems {

    /** One sealed parcel; never stacks, because each carries its own address and deadline. */
    public static final DeferredItem<CourierParcelItem> COURIER_PARCEL =
            ModItems.ITEMS.registerItem("courier_parcel", props -> new CourierParcelItem(props.stacksTo(1)));

    private TravelItems() {}

    public static void init() {}
}
