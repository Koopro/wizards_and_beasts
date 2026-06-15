package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.entity.niffler.NifflerPouchMenu;
import at.koopro.wizardsandbeasts.item.trinket.HermionesBagMenu;
import at.koopro.wizardsandbeasts.trunk.gui.PocketConfiguratorMenu;
import at.koopro.wizardsandbeasts.wand.gui.OllivanderTrialMenu;
import at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, WizardsAndBeastsMod.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<WandmakersBenchMenu>> WANDMAKERS_BENCH =
            MENUS.register("wandmakers_bench", () -> IMenuTypeExtension.create(WandmakersBenchMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>, MenuType<OllivanderTrialMenu>> OLLIVANDER_TRIAL =
            MENUS.register("ollivander_trial", () -> IMenuTypeExtension.create(OllivanderTrialMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>, MenuType<PocketConfiguratorMenu>> POCKET_CONFIGURATOR =
            MENUS.register("pocket_configurator", () -> IMenuTypeExtension.create(PocketConfiguratorMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>, MenuType<NifflerPouchMenu>> NIFFLER_POUCH =
            MENUS.register("niffler_pouch", () -> IMenuTypeExtension.create(NifflerPouchMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>, MenuType<HermionesBagMenu>> HERMIONES_BAG =
            MENUS.register("hermiones_bag", () -> IMenuTypeExtension.create(HermionesBagMenu::fromNetwork));

    private ModMenuTypes() {
    }
}
