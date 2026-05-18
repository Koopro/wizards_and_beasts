package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.broom.item.BroomItem;
import at.koopro.wizardsandbeasts.item.SimpleTooltipItem;
import net.neoforged.neoforge.registries.DeferredItem;

final class BroomItemRegistry {
    static final DeferredItem<BroomItem> BROOM_ITEM = RegistryUtils.registerBroom("broom", 120);
    static final DeferredItem<BroomItem> CLEANSWEEP_SEVEN = RegistryUtils.registerBroom("cleansweep_seven", 120);
    static final DeferredItem<BroomItem> COMET_260 = RegistryUtils.registerBroom("comet_260", 200);
    static final DeferredItem<BroomItem> NIMBUS_2000 = RegistryUtils.registerBroom("nimbus_2000", 320);
    static final DeferredItem<BroomItem> NIMBUS_2001 = RegistryUtils.registerBroom("nimbus_2001", 340);
    static final DeferredItem<BroomItem> FIREBOLT = RegistryUtils.registerBroom("firebolt", 520);
    static final DeferredItem<BroomItem> FIREBOLT_SUPREME = RegistryUtils.registerBroom("firebolt_supreme", 700);
    static final DeferredItem<BroomItem> OAKSHAFT_79 = RegistryUtils.registerBroom("oakshaft_79", 2000);

    static final DeferredItem<SimpleTooltipItem> BROOM_POLISH =
            RegistryUtils.registerTooltipItem("broom_polish", "item.wizards_and_beasts.broom_polish.tooltip", 16);
    static final DeferredItem<SimpleTooltipItem> ENCHANTED_TWIG_BUNDLE =
            RegistryUtils.registerTooltipItem("enchanted_twig_bundle", "item.wizards_and_beasts.enchanted_twig_bundle.tooltip", 8);

    private BroomItemRegistry() {
    }
}
