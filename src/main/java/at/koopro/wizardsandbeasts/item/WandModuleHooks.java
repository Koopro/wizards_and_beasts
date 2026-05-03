package at.koopro.wizardsandbeasts.item;

import java.lang.reflect.Method;

public final class WandModuleHooks {
    private WandModuleHooks() {
    }

    public static boolean isWandsEnabled() {
        try {
            Class<?> moduleClass = Class.forName("at.koopro.wizardsandbeasts.module.Module");
            @SuppressWarnings("unchecked")
            Enum<?> wandModule = Enum.valueOf((Class<? extends Enum>) moduleClass, "WANDS");
            Class<?> managerClass = Class.forName("at.koopro.wizardsandbeasts.module.ModuleManager");
            Method method = managerClass.getMethod("isEnabled", moduleClass);
            Object result = method.invoke(null, wandModule);
            return result instanceof Boolean b && b;
        } catch (Throwable ignored) {
            return true;
        }
    }
}
