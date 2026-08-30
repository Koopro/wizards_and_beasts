package at.koopro.wizardsandbeasts.network;

import at.koopro.wizardsandbeasts.util.ClientClassBridge;

/**
 * Named seam for the client screens the network layer opens.
 *
 * <p>Payload handlers know which screen they want but must not name it — the screen classes are
 * client-only. This resolves {@code ClientScreenHooks} by name at call time and hands the work to
 * {@link ClientClassBridge}, which owns the reflection and the failure logging.
 *
 * <p>Every argument type has to be dist-shared for the same reason: the hook itself resolves the
 * screen class, and only ever client-side.
 */
public final class ClientScreenHooksInvoker {

    private static final String CLIENT_HOOKS_CLASS = "at.koopro.wizardsandbeasts.client.network.ClientScreenHooks";

    private ClientScreenHooksInvoker() {
    }

    public static void invoke(String methodName) {
        ClientClassBridge.callStatic(CLIENT_HOOKS_CLASS, methodName);
    }

    /** Single-argument variant of {@link #invoke(String)}. */
    public static void invoke(String methodName, Class<?> paramType, Object arg) {
        ClientClassBridge.callStatic(CLIENT_HOOKS_CLASS, methodName,
                new Class<?>[] {paramType}, new Object[] {arg});
    }

    /** Two-argument variant of {@link #invoke(String)}. */
    public static void invoke(String methodName, Class<?> p1, Object a1, Class<?> p2, Object a2) {
        ClientClassBridge.callStatic(CLIENT_HOOKS_CLASS, methodName,
                new Class<?>[] {p1, p2}, new Object[] {a1, a2});
    }

    /** Three-argument variant of {@link #invoke(String)}. */
    public static void invoke(String methodName, Class<?> p1, Object a1, Class<?> p2, Object a2,
                              Class<?> p3, Object a3) {
        ClientClassBridge.callStatic(CLIENT_HOOKS_CLASS, methodName,
                new Class<?>[] {p1, p2, p3}, new Object[] {a1, a2, a3});
    }
}
