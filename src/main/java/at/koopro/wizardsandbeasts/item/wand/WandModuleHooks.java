package at.koopro.wizardsandbeasts.item.wand;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;

public final class WandModuleHooks {
    private WandModuleHooks() {
    }

    public static boolean isWandsEnabled() {
        return ModuleManager.isEnabled(Module.WANDS);
    }

    public static boolean isWandsPreview() {
        return ModuleManager.isPreview(Module.WANDS);
    }
}
