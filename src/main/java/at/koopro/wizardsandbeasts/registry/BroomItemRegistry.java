package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.item.BroomItem;
import at.koopro.wizardsandbeasts.item.SimpleTooltipItem;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;

final class BroomItemRegistry {
    static final DeferredItem<BroomItem> BROOM_ITEM =
            ModItems.ITEMS.registerItem("broom", props -> new BroomItem(props,
                            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "cleansweep_seven")),
                    new Item.Properties().stacksTo(1).durability(120));
    static final DeferredItem<BroomItem> CLEANSWEEP_SEVEN =
            ModItems.ITEMS.registerItem("cleansweep_seven", props -> new BroomItem(props,
                            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "cleansweep_seven")),
                    new Item.Properties().stacksTo(1).durability(120));
    static final DeferredItem<BroomItem> COMET_260 =
            ModItems.ITEMS.registerItem("comet_260", props -> new BroomItem(props,
                            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "comet_260")),
                    new Item.Properties().stacksTo(1).durability(200));
    static final DeferredItem<BroomItem> NIMBUS_2000 =
            ModItems.ITEMS.registerItem("nimbus_2000", props -> new BroomItem(props,
                            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "nimbus_2000")),
                    new Item.Properties().stacksTo(1).durability(320));
    static final DeferredItem<BroomItem> NIMBUS_2001 =
            ModItems.ITEMS.registerItem("nimbus_2001", props -> new BroomItem(props,
                            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "nimbus_2001")),
                    new Item.Properties().stacksTo(1).durability(340));
    static final DeferredItem<BroomItem> FIREBOLT =
            ModItems.ITEMS.registerItem("firebolt", props -> new BroomItem(props,
                            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "firebolt")),
                    new Item.Properties().stacksTo(1).durability(520));
    static final DeferredItem<BroomItem> FIREBOLT_SUPREME =
            ModItems.ITEMS.registerItem("firebolt_supreme", props -> new BroomItem(props,
                            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "firebolt_supreme")),
                    new Item.Properties().stacksTo(1).durability(700));
    static final DeferredItem<BroomItem> OAKSHAFT_79 =
            ModItems.ITEMS.registerItem("oakshaft_79", props -> new BroomItem(props,
                            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "oakshaft_79")),
                    new Item.Properties().stacksTo(1).durability(2000));

    static final DeferredItem<SimpleTooltipItem> BROOM_POLISH =
            ModItems.ITEMS.registerItem("broom_polish",
                    props -> new SimpleTooltipItem(props, "item.wizards_and_beasts.broom_polish.tooltip"),
                    new Item.Properties().stacksTo(16));
    static final DeferredItem<SimpleTooltipItem> ENCHANTED_TWIG_BUNDLE =
            ModItems.ITEMS.registerItem("enchanted_twig_bundle",
                    props -> new SimpleTooltipItem(props, "item.wizards_and_beasts.enchanted_twig_bundle.tooltip"),
                    new Item.Properties().stacksTo(8));

    private BroomItemRegistry() {
    }
}
