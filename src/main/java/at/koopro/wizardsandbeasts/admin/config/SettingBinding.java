package at.koopro.wizardsandbeasts.admin.config;

import org.jspecify.annotations.NullMarked;

/**
 * Where a setting's value actually lives.
 *
 * <p>The admin layer owns no values of its own. A binding adapts an existing store — a
 * {@code ModConfigSpec} entry ({@link ConfigSpecBinding}), and later a module's world data or a datapack
 * override — so exposing a setting never creates a second copy that could drift from the one gameplay
 * reads.
 *
 * <p>Called on the server thread only. {@link #set} receives a value the service has already parsed and
 * validated; a binding does not re-check it.
 *
 * @param <T> the value type
 */
@NullMarked
public interface SettingBinding<T> {

    /** The stored value right now. */
    T get();

    /** Stores {@code value} and makes it take effect. */
    void set(T value);

    T defaultValue();

    /** False while the backing store cannot be read or written (a config file not loaded yet). */
    default boolean available() {
        return true;
    }

    /** Whether the backing store itself says a change needs a restart. */
    default boolean storeRequiresRestart() {
        return false;
    }
}
