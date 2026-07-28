package at.koopro.wizardsandbeasts.module;

import at.koopro.wizardsandbeasts.module.data.ModuleStateData;
import at.koopro.wizardsandbeasts.module.settings.ModuleSettingsSchema;
import at.koopro.wizardsandbeasts.module.settings.ModuleSettingsValues;
import at.koopro.wizardsandbeasts.module.settings.SettingDefinition;
import at.koopro.wizardsandbeasts.network.module.ModuleStateSyncPayload;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The one place module state changes. The command tree and the network packet are two doors into this
 * method — validation, persistence, cache refresh and broadcast all happen here, so neither entry point can
 * skip a step the other performs.
 */
@NullMarked
public final class ModuleStateService {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Why a change was refused. */
    public enum Result {
        OK,
        /** Target or current state is {@code COMING_SOON} — a roadmap marker, not an admin toggle. */
        COMING_SOON_LOCKED,
        /** The setting key is not in the module's schema. */
        UNKNOWN_SETTING,
        /** The value did not parse as the setting's type. */
        BAD_VALUE,
        /** No server/level available. */
        UNAVAILABLE;

        public boolean ok() {
            return this == OK;
        }
    }

    private ModuleStateService() {}

    /** Pushes the authoritative world state into the read cache and out to every client. */
    public static void refreshAndBroadcast(MinecraftServer server) {
        ModuleStateData data = ModuleStateData.get(server.overworld());
        ModuleManager.acceptAuthoritative(data.allStates());
        ModuleManager.acceptAuthoritativeSettings(data.allSettings());
        ModuleStateSyncPayload.broadcast(server);
    }

    /** Sends the current snapshot to one player — used on join. */
    public static void syncTo(ServerPlayer player) {
        ModuleStateSyncPayload.sendTo(player);
    }

    /**
     * Changes a module's state.
     *
     * <p>Refuses any transition that touches {@code COMING_SOON} in either direction, operator or not: it
     * says "this is planned", which is a statement about the build rather than a server setting.
     */
    public static Result setState(MinecraftServer server, Module module, ModuleState target) {
        ModuleStateData data = ModuleStateData.get(server.overworld());
        ModuleState current = data.state(module);

        if (!current.isOperatorSettable() || !target.isOperatorSettable()) {
            LOGGER.warn("[Modules] Refused {} {} -> {}: COMING_SOON is not operator-settable",
                    module.name(), current.getSerializedName(), target.getSerializedName());
            return Result.COMING_SOON_LOCKED;
        }

        data.setState(module, target);
        refreshAndBroadcast(server);
        reloadDatapacks(server, module, target);
        LOGGER.info("[Modules] {} {} -> {}", module.name(), current.getSerializedName(), target.getSerializedName());
        return Result.OK;
    }

    /**
     * Re-reads the datapacks so recipes gated by {@code wizards_and_beasts:module_enabled} match the state
     * that was just set.
     *
     * <p>{@code ICondition}s are evaluated once, while a datapack is being read, and the result is baked
     * into the recipe manager. Without this the mod's own gate was half-live: {@link ModuleManager}'s cache
     * updated immediately, JEI's viewer filter re-ran on the sync packet and hid or showed entries — but the
     * recipes themselves kept whatever answer the condition gave at world load. Enabling a module left its
     * recipes uncraftable until the next {@code /reload}, and disabling one left them craftable, with the
     * viewer confidently disagreeing with the crafting table in both directions.
     *
     * <p>Only on a state change. Settings cannot appear in a condition, so
     * {@link #setSetting} deliberately does not pay this cost.
     */
    private static void reloadDatapacks(MinecraftServer server, Module module, ModuleState target) {
        server.reloadResources(server.getPackRepository().getSelectedIds()).exceptionally(throwable -> {
            // A failed reload leaves the previous resources in place, which is the safe direction: the
            // module flag itself is already stored and broadcast, so only recipe visibility lags.
            LOGGER.error("[Modules] Datapack reload after {} -> {} failed; recipe conditions still reflect "
                    + "the previous state until /reload", module.name(), target.getSerializedName(), throwable);
            return null;
        });
    }

    /**
     * Changes one setting, parsing {@code rawValue} through the definition's own codec so the value is
     * validated and clamped against the schema rather than trusted.
     */
    public static Result setSetting(MinecraftServer server, Module module, Identifier settingKey, String rawValue) {
        SettingDefinition<?> definition = ModuleSettingsSchema.of(module).get(settingKey);
        if (definition == null) {
            LOGGER.warn("[Modules] Refused setting {} on {}: not in schema", settingKey, module.name());
            return Result.UNKNOWN_SETTING;
        }
        ModuleStateData data = ModuleStateData.get(server.overworld());
        ModuleSettingsValues updated = applyParsed(definition, data.settings(module), rawValue);
        if (updated == null) {
            LOGGER.warn("[Modules] Refused setting {} on {}: '{}' is not a valid value",
                    settingKey, module.name(), rawValue);
            return Result.BAD_VALUE;
        }
        data.setSettings(module, updated);
        refreshAndBroadcast(server);
        LOGGER.info("[Modules] {} setting {} = {}", module.name(), settingKey, rawValue);
        return Result.OK;
    }

    /** Parses and stores in one generic step so {@code T} stays captured. Null when the value is invalid. */
    @Nullable
    private static <T> ModuleSettingsValues applyParsed(SettingDefinition<T> definition,
                                                        ModuleSettingsValues current,
                                                        String rawValue) {
        com.google.gson.JsonElement json;
        try {
            json = com.google.gson.JsonParser.parseString(rawValue);
        } catch (RuntimeException ex) {
            // Bare words like `true` parse fine; anything genuinely malformed lands here.
            return null;
        }
        java.util.Optional<T> parsed =
                definition.valueCodec().parse(com.mojang.serialization.JsonOps.INSTANCE, json).result();
        return parsed.map(value -> current.with(definition, value)).orElse(null);
    }
}
