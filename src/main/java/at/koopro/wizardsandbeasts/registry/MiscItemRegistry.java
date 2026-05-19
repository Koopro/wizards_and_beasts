package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.item.bestiary.BestiaryItem;
import at.koopro.wizardsandbeasts.item.InkItem;
import at.koopro.wizardsandbeasts.item.ParchmentItem;
import at.koopro.wizardsandbeasts.item.deluminator.DeluminatorItem;
import at.koopro.wizardsandbeasts.item.floo.FlooPowderItem;
import at.koopro.wizardsandbeasts.item.map.MaraudersMapItem;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;

public final class MiscItemRegistry {

    public static final DeferredItem<BestiaryItem> BESTIARY =
            ModItems.ITEMS.registerItem("bestiary", props -> new BestiaryItem(props.stacksTo(1)));

    public static final DeferredItem<ParchmentItem> PARCHMENT =
            ModItems.ITEMS.registerItem("parchment", ParchmentItem::new);

    public static final DeferredItem<InkItem> INK_BOTTLE =
            ModItems.ITEMS.registerItem("ink_bottle", InkItem::new);

    public static final DeferredItem<DeluminatorItem> DELUMINATOR =
            ModItems.ITEMS.registerItem("deluminator", props -> new DeluminatorItem(props.stacksTo(1)));

    public static final DeferredItem<MaraudersMapItem> MARAUDERS_MAP =
            ModItems.ITEMS.registerItem("marauders_map", props -> new MaraudersMapItem(props.stacksTo(1)));

    public static final DeferredItem<FlooPowderItem> FLOO_POWDER =
            ModItems.ITEMS.registerItem("floo_powder", FlooPowderItem::new);

    public static final DeferredItem<SpawnEggItem> GOBLIN_TELLER_SPAWN_EGG =
            ModItems.ITEMS.registerItem("goblin_teller_spawn_egg",
                    properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.GOBLIN_TELLER.get())));

    public static final DeferredItem<SpawnEggItem> NIFFLER_SPAWN_EGG =
            ModItems.ITEMS.registerItem("niffler_spawn_egg",
                    properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.NIFFLER.get())));

    private MiscItemRegistry() {}

    public static void init() {}
}
