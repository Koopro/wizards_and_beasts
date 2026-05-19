package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.item.broom.BroomItem;
import at.koopro.wizardsandbeasts.item.SimpleTooltipItem;
import net.neoforged.neoforge.registries.DeferredItem;

public final class BroomItemRegistry {
    public static final DeferredItem<BroomItem> BROOM_ITEM = RegistryUtils.registerBroom("broom", 120);
    public static final DeferredItem<BroomItem> CLEANSWEEP_SEVEN = RegistryUtils.registerBroom("cleansweep_seven", 120);
    public static final DeferredItem<BroomItem> COMET_260 = RegistryUtils.registerBroom("comet_260", 200);
    public static final DeferredItem<BroomItem> NIMBUS_2000 = RegistryUtils.registerBroom("nimbus_2000", 320);
    public static final DeferredItem<BroomItem> NIMBUS_2001 = RegistryUtils.registerBroom("nimbus_2001", 340);
    public static final DeferredItem<BroomItem> FIREBOLT = RegistryUtils.registerBroom("firebolt", 520);
    public static final DeferredItem<BroomItem> FIREBOLT_SUPREME = RegistryUtils.registerBroom("firebolt_supreme", 700);
    public static final DeferredItem<BroomItem> OAKSHAFT_79 = RegistryUtils.registerBroom("oakshaft_79", 2000);

    public static final DeferredItem<SimpleTooltipItem> BROOM_POLISH =
            RegistryUtils.registerTooltipItem("broom_polish", "item.wizards_and_beasts.broom_polish.tooltip", 16);
    public static final DeferredItem<SimpleTooltipItem> ENCHANTED_TWIG_BUNDLE =
            RegistryUtils.registerTooltipItem("enchanted_twig_bundle", "item.wizards_and_beasts.enchanted_twig_bundle.tooltip", 8);

    private BroomItemRegistry() {}

    public static void init() {}
}
