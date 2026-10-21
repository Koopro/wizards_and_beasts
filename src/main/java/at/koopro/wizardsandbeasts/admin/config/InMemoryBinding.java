package at.koopro.wizardsandbeasts.admin.config;

import org.jspecify.annotations.NullMarked;

/**
 * A binding that stores its value in a field. For tests, and for settings whose value is deliberately
 * server-lifetime only.
 */
@NullMarked
public final class InMemoryBinding<T> implements SettingBinding<T> {

    private final T defaultValue;
    private T value;

    public InMemoryBinding(T defaultValue) {
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    @Override
    public T get() {
        return value;
    }

    @Override
    public void set(T value) {
        this.value = value;
    }

    @Override
    public T defaultValue() {
        return defaultValue;
    }
}
