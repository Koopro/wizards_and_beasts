package at.koopro.wizardsandbeasts.admin.config;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A rule a value must satisfy beyond its type's bounds — typically one that relates it to other settings
 * ("the damage ceiling must stay above the knee").
 *
 * <p>Runs on the server after parsing and the bounds check, before anything is stored. Reads the other
 * settings it depends on through the registry, never through {@code Config}, so it sees exactly what the
 * service sees.
 *
 * @param <T> the value type of the setting being changed
 */
@NullMarked
@FunctionalInterface
public interface SettingValidator<T> {

    /**
     * @return null when {@code candidate} is acceptable, otherwise a translation key explaining why not
     */
    @Nullable String check(T candidate, AdminSettingRegistry registry);
}
