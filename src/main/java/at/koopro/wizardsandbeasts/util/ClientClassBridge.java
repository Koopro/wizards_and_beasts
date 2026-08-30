package at.koopro.wizardsandbeasts.util;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * The one way common code reaches a client-only class.
 *
 * <p>Client-only types cannot be named from common code without risking a dedicated-server
 * {@code NoClassDefFoundError}, so the call goes through reflection instead. Three separate
 * mechanisms used to do this — this class, {@code ClientScreenHooksInvoker}, and hand-rolled
 * {@code Class.forName} blocks inside three items. They all live behind these methods now.
 *
 * <p>{@link #instantiate} throws, because a renderer that failed to construct is a bug the caller
 * cannot paper over. {@link #callStatic} logs and returns, because a screen that failed to open
 * should not take the client down with it.
 */
public final class ClientClassBridge {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ClientClassBridge() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T instantiate(String className, Class<T> expectedType, Class<?>[] parameterTypes, Object[] args) {
        try {
            Class<?> rawClass = Class.forName(className);
            Constructor<?> constructor = rawClass.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            Object instance = constructor.newInstance(args);
            return expectedType.cast(instance);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to instantiate client class: " + className, ex);
        }
    }

    public static boolean callStaticBoolean(String className, String methodName) {
        try {
            Class<?> rawClass = Class.forName(className);
            Method method = rawClass.getDeclaredMethod(methodName);
            method.setAccessible(true);
            Object result = method.invoke(null);
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
            return false;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to call client method: " + className + "#" + methodName, ex);
        }
    }

    /** Calls a public static no-arg method on a client-only class. Logs and swallows a failure. */
    public static void callStatic(String className, String methodName) {
        callStatic(className, methodName, new Class<?>[0], new Object[0]);
    }

    /**
     * Calls a public static method on a client-only class. Logs and swallows a failure.
     *
     * <p>Every parameter type must be dist-shared, so the call site never has to name a client-only
     * type to describe the signature.
     */
    public static void callStatic(String className, String methodName, Class<?>[] parameterTypes, Object[] args) {
        try {
            Class.forName(className).getMethod(methodName, parameterTypes).invoke(null, args);
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("[WizardsAndBeasts] Client hook '{}#{}' failed to invoke", className, methodName, e);
        }
    }
}
