package at.koopro.wizardsandbeasts.admin.config;

import com.mojang.logging.LogUtils;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

import java.util.Objects;

/**
 * Binds a setting to an existing {@link ModConfigSpec} entry — the adapter that keeps {@code Config.java}
 * the single owner of its values.
 *
 * <p>{@link #set} writes through NeoForge's own path: {@link ModConfigSpec.ConfigValue#set} then
 * {@link ModConfigSpec#save()}. {@code save()} writes the file <em>and</em> fires
 * {@code ModConfigEvent.Reloading} synchronously, so {@code Config.onLoad} refreshes its public statics (and
 * re-pushes {@code SpellPower}'s bounds) before this method returns. Gameplay therefore sees the change on
 * the very next read, and the value survives a restart because it is in the file.
 *
 * <p><b>The file watcher.</b> FML watches the config file and, on its own thread, reloads it under the mod's config
 * lock by swapping in a freshly read config object. {@link ModConfigSpec.ConfigValue#set} does not take that lock (it
 * is package-private in FML). When a reload triggered by an earlier save lands between {@code set} and {@code save},
 * the value goes into the discarded object and {@code save} writes the reloaded, older value back to disk: the
 * change is lost for good. Several settings saved in a row (a profile applied) make that likely. So {@link #set}
 * reads the value back after saving and writes again until it holds, a few times at most.
 *
 * <p>This class does not name {@code Config}; it is handed the spec and the value. That keeps it loadable in
 * a test JVM that must never run {@code Config}'s static initialiser.
 */
@NullMarked
public final class ConfigSpecBinding<T> implements SettingBinding<T> {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** Writes attempted before giving up; a lost write needs a watcher reload in a microsecond window, twice. */
    private static final int MAX_WRITES = 4;

    private final ModConfigSpec spec;
    private final ModConfigSpec.ConfigValue<T> value;

    public ConfigSpecBinding(ModConfigSpec spec, ModConfigSpec.ConfigValue<T> value) {
        this.spec = spec;
        this.value = value;
    }

    /**
     * The stored value, uncached. {@code get()} would return the value in effect, which for a restart-bound
     * key is the one from before the change; the panel shows what is configured.
     */
    @Override
    public T get() {
        return value.getRaw();
    }

    @Override
    public void set(T newValue) {
        int writes = writeUntilHeld(() -> {
            value.set(newValue);
            spec.save();
        }, value::getRaw, newValue, MAX_WRITES);
        if (writes > MAX_WRITES) {
            // The caller reads the value back and reports what is actually stored, so this is visible, not silent.
            LOGGER.warn("[Admin] Config value {} did not hold after {} writes (concurrent file reloads)", value.getPath(),
                    MAX_WRITES);
        } else if (writes > 1) {
            LOGGER.debug("[Admin] Config value {} rewritten after a concurrent file reload ({} writes)", value.getPath(),
                    writes);
        }
    }

    /**
     * Runs {@code write} until {@code read} returns {@code expected}, at most {@code max} times. Returns how many writes
     * it took, or {@code max + 1} when the value never held.
     */
    static <V> int writeUntilHeld(Runnable write, java.util.function.Supplier<V> read, V expected, int max) {
        for (int attempt = 1; attempt <= max; attempt++) {
            write.run();
            if (Objects.equals(read.get(), expected)) {
                return attempt;
            }
        }
        return max + 1;
    }

    @Override
    public T defaultValue() {
        return value.getDefault();
    }

    @Override
    public boolean available() {
        return spec.isLoaded();
    }

    @Override
    public boolean storeRequiresRestart() {
        return value.getSpec().restartType() != ModConfigSpec.RestartType.NONE;
    }
}
