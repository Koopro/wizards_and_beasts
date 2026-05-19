package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.item.wand.DebugWandItem;
import at.koopro.wizardsandbeasts.item.wand.MorphWandItem;
import at.koopro.wizardsandbeasts.item.wand.WandBlankItem;
import at.koopro.wizardsandbeasts.item.wand.WandCoreMaterialItem;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.customization.WandConfiguration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;

public final class WandItemRegistry {

    public static final DeferredItem<WandItem> WAND =
            ModItems.ITEMS.registerItem("wand", props -> new WandItem(props
                    .stacksTo(1)
                    .component(WandComponents.WAND_CONFIGURATION.get(), WandConfiguration.DEFAULT)));

    public static final DeferredItem<WandBlankItem> WAND_BLANK =
            ModItems.ITEMS.registerItem("wand_blank", props -> new WandBlankItem(props.stacksTo(1)));

    public static final DeferredItem<DebugWandItem> DEBUG_WAND =
            ModItems.ITEMS.registerItem("debug_wand", props -> new DebugWandItem(props.stacksTo(1)));

    public static final DeferredItem<MorphWandItem> MORPH_WAND =
            ModItems.ITEMS.registerItem("morph_wand", props -> new MorphWandItem(props.stacksTo(1)));

    // --- Wand Core Materials ---

    public static final DeferredItem<WandCoreMaterialItem> PHOENIX_FEATHER =
            ModItems.ITEMS.registerItem("phoenix_feather", props -> new WandCoreMaterialItem(props,
                    Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "phoenix_feather"),
                    Component.literal("Source key: fawkes")));
    public static final DeferredItem<WandCoreMaterialItem> DRAGON_HEARTSTRING =
            ModItems.ITEMS.registerItem("dragon_heartstring", props -> new WandCoreMaterialItem(props,
                    Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "dragon_heartstring"),
                    Component.literal("Source key: hungarian_horntail")));
    public static final DeferredItem<WandCoreMaterialItem> UNICORN_HAIR =
            ModItems.ITEMS.registerItem("unicorn_hair", props -> new WandCoreMaterialItem(props,
                    Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "unicorn_hair"),
                    Component.literal("Fragile core material. Handle with care.")));
    public static final DeferredItem<Item> THESTRAL_TAIL_HAIR =
            ModItems.ITEMS.registerSimpleItem("thestral_tail_hair");
    public static final DeferredItem<Item> VEELA_HAIR =
            ModItems.ITEMS.registerSimpleItem("veela_hair");
    public static final DeferredItem<Item> TROLL_WHISKER =
            ModItems.ITEMS.registerSimpleItem("troll_whisker");
    public static final DeferredItem<Item> WAMPUS_CAT_HAIR =
            ModItems.ITEMS.registerSimpleItem("wampus_cat_hair");
    public static final DeferredItem<Item> THUNDERBIRD_TAIL_FEATHER =
            ModItems.ITEMS.registerSimpleItem("thunderbird_tail_feather");

    public static void init() {}

    private WandItemRegistry() {}
}
