package at.koopro.wizardsandbeasts.module;

import com.mojang.logging.LogUtils;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Config-declared seed state per module, appended to the mod's existing spec.
 *
 * <p>Read <b>only</b> when a world with no stored module state is first loaded. Editing it afterwards has
 * no effect on worlds that already exist — those are changed with {@code /wandb module set}, which is the
 * behaviour the per-world design is for.
 *
 * <p>Values are stored as strings and parsed leniently: an unrecognised value logs a warning and falls back
 * to {@link ModuleDefaults#shipped}, so a typo in a config file cannot stop a server booting.
 */
@NullMarked
public final class ModuleConfig {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<Module, ModConfigSpec.ConfigValue<String>> VALUES = new EnumMap<>(Module.class);

    private ModuleConfig() {}

    /**
     * Declares one entry per module inside a {@code moduleDefaults} section. Called from the mod's config
     * builder while the spec is being assembled.
     */
    public static void define(ModConfigSpec.Builder builder) {
        builder.comment(
                        "Seed state for each gameplay module, applied ONLY when a world is first created.",
                        "Existing worlds keep whatever they already have — change those with /wandb module set.",
                        "Valid values: disabled, enabled, preview, coming_soon.",
                        "coming_soon marks a module as planned: it stays off and operators cannot switch it.")
                .push("moduleDefaults");
        for (Module module : Module.values()) {
            String key = module.name().toLowerCase(Locale.ROOT);
            VALUES.put(module, builder.define(key, ModuleDefaults.shipped(module).getSerializedName()));
        }
        builder.pop();
    }

    /** The configured seed for {@code module}, or the shipped default when unset or unparseable. */
    public static ModuleState defaultStateFor(Module module) {
        ModConfigSpec.ConfigValue<String> value = VALUES.get(module);
        if (value == null) {
            return ModuleDefaults.shipped(module);
        }
        String raw;
        try {
            raw = value.get();
        } catch (IllegalStateException ex) {
            // Config not loaded yet (early boot, or a unit test with no config) — fall back to the build's own.
            return ModuleDefaults.shipped(module);
        }
        ModuleState parsed = ModuleState.parse(raw);
        if (parsed == null) {
            LOGGER.warn("[Modules] Unknown state '{}' configured for module {} — using {}",
                    raw, module.name(), ModuleDefaults.shipped(module).getSerializedName());
            return ModuleDefaults.shipped(module);
        }
        return parsed;
    }
}
