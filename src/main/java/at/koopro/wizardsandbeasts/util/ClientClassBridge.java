package at.koopro.wizardsandbeasts.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public final class ClientClassBridge {
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
}
