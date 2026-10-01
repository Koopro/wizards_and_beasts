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

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

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
        UNAVAILABLE,
        /** A module it {@link ModuleDependencies.Kind#REQUIRES} is off; see {@link Change#check()}. */
        DEPENDENCY_MISSING;

        public boolean ok() {
            return this == OK;
        }
    }

    /**
     * The outcome of a state change, with what it took along: {@code alsoDisabled} are the dependants that went off
     * with it (REQUIRES edges), {@code check} the full dependency answer it was judged on.
     */
    public record Change(Result result, List<Module> alsoDisabled, ModuleDependencies.Check check) {
        public boolean ok() {
            return result.ok();
        }
    }

    /** Test seam: true only inside {@link #withoutDatapackReload}. */
    private static boolean reloadSuppressed;

    private ModuleStateService() {}

    /**
     * Runs {@code body} with the datapack reload a state change normally triggers held back. For game tests only: a
     * reload mid-batch would re-run every reload listener under other tests' feet. Scoped to one synchronous call.
     */
    public static <T> T withoutDatapackReload(Supplier<T> body) {
        reloadSuppressed = true;
        try {
            return body.get();
        } finally {
            reloadSuppressed = false;
        }
    }

    /** Pushes the authoritative world state into the read cache and out to every client. */
    public static void refreshAndBroadcast(MinecraftServer server) {
        ModuleStateData data = ModuleStateData.get(server.overworld());
        Map<Module, ModuleState> before = ModuleManager.snapshot();
        ModuleManager.acceptAuthoritative(data.allStates());
        ModuleManager.acceptAuthoritativeSettings(data.allSettings());
        ModuleStateSyncPayload.broadcast(server);
        if (!before.equals(ModuleManager.snapshot()) && !reloadSuppressed) {
            reloadDatapacks(server);
        }
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
        return change(server, module, target).result();
    }

    /**
     * Changes a module's state, judged against {@link ModuleDependencies}:
     * <ul>
     *   <li>opening a module whose REQUIRES dependency is off is refused ({@link Result#DEPENDENCY_MISSING});</li>
     *   <li>closing a module closes its REQUIRES dependants with it, in one persisted step, and reports them in
     *       {@link Change#alsoDisabled()} — callers say so before and after; nothing breaks silently;</li>
     *   <li>PARTIAL dependants are left as they are; the change carries them in its check for the warning.</li>
     * </ul>
     */
    public static Change change(MinecraftServer server, Module module, ModuleState target) {
        ModuleStateData data = ModuleStateData.get(server.overworld());
        ModuleState current = data.state(module);
        ModuleDependencies.Check check = ModuleDependencies.check(data.allStates(), module, target);

        if (!current.isOperatorSettable() || !target.isOperatorSettable()) {
            LOGGER.warn("[Modules] Refused {} {} -> {}: COMING_SOON is not operator-settable",
                    module.name(), current.getSerializedName(), target.getSerializedName());
            return new Change(Result.COMING_SOON_LOCKED, List.of(), check);
        }
        if (!check.allowed()) {
            LOGGER.warn("[Modules] Refused {} {} -> {}: requires {}", module.name(), current.getSerializedName(),
                    target.getSerializedName(), check.blockedBy().stream().map(e -> e.dependency().name()).toList());
            return new Change(Result.DEPENDENCY_MISSING, List.of(), check);
        }

        for (Module dependant : check.cascade()) {
            data.setState(dependant, ModuleState.DISABLED);
        }
        data.setState(module, target);
        refreshAndBroadcast(server);
        LOGGER.info("[Modules] {} {} -> {}{}", module.name(), current.getSerializedName(), target.getSerializedName(),
                check.cascade().isEmpty() ? "" : " (also disabled: " + check.cascade() + ")");
        return new Change(Result.OK, check.cascade(), check);
    }

    /**
     * Re-reads the datapacks so recipes gated by {@code wizards_and_beasts:module_enabled} match the states
     * now in the cache.
     *
     * <p>{@code ICondition}s are evaluated once, while a datapack is being read, and the answer is baked
     * into the recipe manager. Without this the gate was half-live: {@link ModuleManager}'s cache updated at
     * once and JEI's viewer filter re-ran on the sync packet, but the recipes kept whatever the condition
     * said when the pack was last read. Enabling a module left its recipes uncraftable until the next
     * {@code /reload} and disabling one left them craftable, with the viewer confidently disagreeing with
     * the crafting table both ways.
     *
     * <p><b>This matters at startup too, not just for operator commands.</b> Datapacks are read before
     * {@code ServerStartedEvent}, which is where world state first reaches the cache — so a world whose
     * stored state differs from the build's shipped defaults loaded its recipes against the defaults.
     * Driving the reload off "did any state actually change" covers both, and costs nothing on the common
     * path where a world agrees with the build.
     *
     * <p>Settings cannot appear in a condition, so a settings-only change never reaches here.
     */
    /**
     * One datapack reload after a batch of module changes made with the reload held back (a profile applied) — so a
     * batch of N module switches costs one reload, not N. Honours {@link #withoutDatapackReload} like every reload.
     */
    public static void reloadAfterBatch(MinecraftServer server) {
        if (!reloadSuppressed) {
            reloadDatapacks(server);
        }
    }

    private static void reloadDatapacks(MinecraftServer server) {
        server.reloadResources(server.getPackRepository().getSelectedIds()).exceptionally(throwable -> {
            // A failed reload leaves the previous resources in place, which is the safe direction: the
            // module flags are already stored and broadcast, so only recipe availability lags.
            LOGGER.error("[Modules] Datapack reload failed; recipe conditions still reflect the previous "
                    + "module states until /reload", throwable);
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
