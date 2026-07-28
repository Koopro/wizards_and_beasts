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
    // These five were plain Items, which meant they carried no core key — so the bench refused them and
    // JEI never listed them, even though they are tagged into WANDS and filed under "Wand Cores" in the
    // creative menu. They take no bespoke tooltip: each already has a `.desc` lang line, which
    // ItemDescriptionTooltipHandler renders for every item in this namespace.
    public static final DeferredItem<WandCoreMaterialItem> THESTRAL_TAIL_HAIR =
            ModItems.ITEMS.registerItem("thestral_tail_hair", props -> new WandCoreMaterialItem(props,
                    Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "thestral_tail_hair")));
    public static final DeferredItem<WandCoreMaterialItem> VEELA_HAIR =
            ModItems.ITEMS.registerItem("veela_hair", props -> new WandCoreMaterialItem(props,
                    Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "veela_hair")));
    public static final DeferredItem<WandCoreMaterialItem> TROLL_WHISKER =
            ModItems.ITEMS.registerItem("troll_whisker", props -> new WandCoreMaterialItem(props,
                    Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "troll_whisker")));
    public static final DeferredItem<WandCoreMaterialItem> WAMPUS_CAT_HAIR =
            ModItems.ITEMS.registerItem("wampus_cat_hair", props -> new WandCoreMaterialItem(props,
                    Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "wampus_cat_hair")));
    public static final DeferredItem<WandCoreMaterialItem> THUNDERBIRD_TAIL_FEATHER =
            ModItems.ITEMS.registerItem("thunderbird_tail_feather", props -> new WandCoreMaterialItem(props,
                    Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "thunderbird_tail_feather")));

    public static void init() {}

    private WandItemRegistry() {}
}
