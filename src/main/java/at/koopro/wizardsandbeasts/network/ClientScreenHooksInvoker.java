package at.koopro.wizardsandbeasts.network;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public final class ClientScreenHooksInvoker {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CLIENT_HOOKS_CLASS = "at.koopro.wizardsandbeasts.client.network.ClientScreenHooks";

    private ClientScreenHooksInvoker() {
    }

    public static void invoke(String methodName) {
        try {
            Class<?> hooks = Class.forName(CLIENT_HOOKS_CLASS);
            hooks.getMethod(methodName).invoke(null);
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("[WizardsAndBeasts] Client screen hook '{}' failed to invoke", methodName, e);
        }
    }

    /**
     * Invokes a single-argument client screen hook. The argument type must be a common
     * (dist-shared) class so the call site never references a client-only type — the
     * client hook resolves the screen class only when executed client-side.
     */
    public static void invoke(String methodName, Class<?> paramType, Object arg) {
        try {
            Class<?> hooks = Class.forName(CLIENT_HOOKS_CLASS);
            hooks.getMethod(methodName, paramType).invoke(null, arg);
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("[WizardsAndBeasts] Client screen hook '{}' failed to invoke", methodName, e);
        }
    }
}
