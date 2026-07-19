package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.item.bestiary.BestiaryItem;
import at.koopro.wizardsandbeasts.item.MinistryHandbookItem;
import at.koopro.wizardsandbeasts.item.InkItem;
import at.koopro.wizardsandbeasts.item.ParchmentItem;
import at.koopro.wizardsandbeasts.item.deluminator.DeluminatorItem;
import at.koopro.wizardsandbeasts.item.floo.FlooPowderItem;
import at.koopro.wizardsandbeasts.item.map.MaraudersMapItem;
import at.koopro.wizardsandbeasts.item.wearable.BlindfoldItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;

public final class MiscItemRegistry {

    public static final DeferredItem<BestiaryItem> BESTIARY =
            ModItems.ITEMS.registerItem("bestiary", props -> new BestiaryItem(props.stacksTo(1)));

    public static final DeferredItem<BlindfoldItem> BLINDFOLD =
            ModItems.ITEMS.registerItem("blindfold", props -> new BlindfoldItem(props.stacksTo(1).equippable(EquipmentSlot.HEAD)));

    public static final DeferredItem<MinistryHandbookItem> MINISTRY_HANDBOOK =
            ModItems.ITEMS.registerItem("ministry_handbook", props -> new MinistryHandbookItem(props.stacksTo(1)));

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

    public static final DeferredItem<SpawnEggItem> BOWTRUCKLE_SPAWN_EGG =
            ModItems.ITEMS.registerItem("bowtruckle_spawn_egg",
                    properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.BOWTRUCKLE.get())));

    public static final DeferredItem<SpawnEggItem> CORNISH_PIXIE_SPAWN_EGG =
            ModItems.ITEMS.registerItem("cornish_pixie_spawn_egg",
                    properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.CORNISH_PIXIE.get())));

    public static final DeferredItem<SpawnEggItem> THESTRAL_SPAWN_EGG =
            ModItems.ITEMS.registerItem("thestral_spawn_egg",
                    properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.THESTRAL.get())));

    public static final DeferredItem<SpawnEggItem> PHOENIX_SPAWN_EGG =
            ModItems.ITEMS.registerItem("phoenix_spawn_egg",
                    properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.PHOENIX.get())));

    public static final DeferredItem<SpawnEggItem> RUNESPOOR_SPAWN_EGG =
            ModItems.ITEMS.registerItem("runespoor_spawn_egg",
                    properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.RUNESPOOR.get())));

    public static final DeferredItem<SpawnEggItem> HIDEBEHIND_SPAWN_EGG =
            ModItems.ITEMS.registerItem("hidebehind_spawn_egg",
                    properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.HIDEBEHIND.get())));

    public static final DeferredItem<SpawnEggItem> AUGUREY_SPAWN_EGG =
            ModItems.ITEMS.registerItem("augurey_spawn_egg",
                    properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.AUGUREY.get())));

    public static final DeferredItem<SpawnEggItem> MOONCALF_SPAWN_EGG =
            ModItems.ITEMS.registerItem("mooncalf_spawn_egg",
                    properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.MOONCALF.get())));

    public static final DeferredItem<SpawnEggItem> STREELER_SPAWN_EGG =
            ModItems.ITEMS.registerItem("streeler_spawn_egg",
                    properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.STREELER.get())));

    private MiscItemRegistry() {}

    public static void init() {}
}
