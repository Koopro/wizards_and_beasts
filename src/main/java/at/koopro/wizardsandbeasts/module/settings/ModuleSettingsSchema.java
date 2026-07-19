package at.koopro.wizardsandbeasts.module.settings;

import at.koopro.wizardsandbeasts.module.Module;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The ordered set of settings a module exposes, declared in code beside the module itself.
 *
 * <p>Order is display order — the admin screen and {@code /wandb module list} both render a schema in the
 * sequence it was declared, so declaration order is a UI decision, not an accident.
 *
 * <p><b>Every schema is empty in this prompt.</b> The framework is deliberately shipped before any real
 * setting so that the storage, validation and sync path can be proven on its own; wiring a setting to
 * gameplay is a separate, per-module job.
 */
@NullMarked
public final class ModuleSettingsSchema {

    private static final Map<Module, ModuleSettingsSchema> SCHEMAS = new EnumMap<>(Module.class);
    private static final ModuleSettingsSchema EMPTY = new ModuleSettingsSchema(List.of());

    private final List<SettingDefinition<?>> ordered;
    private final Map<Identifier, SettingDefinition<?>> byKey;

    private ModuleSettingsSchema(List<SettingDefinition<?>> ordered) {
        this.ordered = List.copyOf(ordered);
        Map<Identifier, SettingDefinition<?>> map = new LinkedHashMap<>();
        for (SettingDefinition<?> definition : this.ordered) {
            if (map.put(definition.key(), definition) != null) {
                throw new IllegalArgumentException("Duplicate setting key: " + definition.key());
            }
        }
        this.byKey = Collections.unmodifiableMap(map);
    }

    /** Declares (or replaces) a module's schema. Called from module bootstrap, never at runtime. */
    public static void register(Module module, List<SettingDefinition<?>> definitions) {
        SCHEMAS.put(module, new ModuleSettingsSchema(new ArrayList<>(definitions)));
    }

    /** The module's schema, or an empty one — every module has a schema, most of them have no entries. */
    public static ModuleSettingsSchema of(Module module) {
        return SCHEMAS.getOrDefault(module, EMPTY);
    }

    /** Test/debug seam: drops a registered schema so a test can register its own without leaking. */
    public static void clear(Module module) {
        SCHEMAS.remove(module);
    }

    public List<SettingDefinition<?>> definitions() {
        return ordered;
    }

    public boolean isEmpty() {
        return ordered.isEmpty();
    }

    @Nullable
    public SettingDefinition<?> get(Identifier key) {
        return byKey.get(key);
    }

    public boolean contains(Identifier key) {
        return byKey.containsKey(key);
    }
}
