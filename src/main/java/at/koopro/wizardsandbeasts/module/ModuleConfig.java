package at.koopro.wizardsandbeasts.module;

import com.mojang.logging.LogUtils;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.EnumMap;
import java.util.Locale;
import java.util.List;
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

    /**
     * Ship in {@code moduleAdminUuids} by default so the mod's authors can administer modules on their own
     * servers without configuring anything.
     *
     * <p>Note what this means on a server run by anyone else: because the list is a closed allow-list, these
     * are the <b>only</b> accounts that can change module state there until an operator edits the config
     * from the console. Server owners who do not want that should replace these entries with their own.
     */
    private static final List<String> OWNER_UUIDS = List.of(
            "d3c9ca09-8f15-4e59-96dd-1c0fab339fcd",
            "c9e6dcee-95f6-4d14-a980-5008ff72676e");

    private static final Map<Module, ModConfigSpec.ConfigValue<String>> VALUES = new EnumMap<>(Module.class);

    @Nullable
    private static ConfigValue<List<? extends String>> ADMIN_UUIDS;

    private ModuleConfig() {}

    /**
     * Declares one entry per module inside a {@code moduleDefaults} section. Called from the mod's config
     * builder while the spec is being assembled.
     */
    public static void define(ModConfigSpec.Builder builder) {
        ADMIN_UUIDS = builder.comment(
                        "Player UUIDs permitted to change module state, in game or through the admin screen.",
                        "Ships with the mod authors' UUIDs. Replace them with your own, or clear the list to",
                        "fall back to the usual operator permission.",
                        "When ANY valid UUID is listed this becomes a closed allow-list: operators who are not",
                        "on it are refused. The server console and command blocks always qualify, so a bad",
                        "entry here can always be corrected from the console.",
                        "Example: [\"123e4567-e89b-12d3-a456-426614174000\"]")
                .defineList("moduleAdminUuids", OWNER_UUIDS,
                        () -> "", entry -> entry instanceof String);

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

    /** Configured admin UUIDs; empty when unset or before the config has loaded. */
    public static List<? extends String> adminUuids() {
        if (ADMIN_UUIDS == null) {
            return List.of();
        }
        try {
            return ADMIN_UUIDS.get();
        } catch (IllegalStateException ex) {
            return List.of(); // config not loaded yet — behave as unconfigured
        }
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
