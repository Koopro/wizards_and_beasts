package at.koopro.wizardsandbeasts.network;

public final class ClientScreenHooksInvoker {

    private static final String CLIENT_HOOKS_CLASS = "at.koopro.wizardsandbeasts.client.network.ClientScreenHooks";

    private ClientScreenHooksInvoker() {
    }

    public static void invoke(String methodName) {
        try {
            Class<?> hooks = Class.forName(CLIENT_HOOKS_CLASS);
            hooks.getMethod(methodName).invoke(null);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
