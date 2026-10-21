package at.koopro.wizardsandbeasts.module;

import at.koopro.wizardsandbeasts.module.settings.ModuleSettingsValues;
import org.jspecify.annotations.NullMarked;

import java.util.EnumMap;
import java.util.Map;

/**
 * The gate every feature asks before letting a player reach it. Registration is never gated — content
 * always registers; this controls access only.
 *
 * <p><b>Authority lives in the world, not here.</b> On a server the truth is {@code ModuleStateData} on the
 * overworld; on a client it is whatever the last sync said. This class is a fast read cache in front of
 * that, because the gate call sites include per-tick paths that must not do a level lookup. Nothing writes
 * to it except {@code ModuleStateService} (server, after persisting), the sync payload handler (client) and
 * {@link #overrideCached} (tests).
 */
@NullMarked
public final class ModuleManager {

    private static final Map<Module, ModuleState> CACHE = new EnumMap<>(Module.class);
    private static final Map<Module, ModuleSettingsValues> SETTINGS_CACHE = new EnumMap<>(Module.class);

    static {
        // Before a world loads (or a sync arrives) fall back to the build's own defaults, so main-menu code
        // and unit tests see something sane rather than every feature switched off.
        for (Module module : Module.values()) {
            CACHE.put(module, ModuleDefaults.shipped(module));
        }
    }

    private ModuleManager() {
    }

    // ── reads: signatures unchanged, every existing gate call site depends on these ──

    /** True when the module is reachable — {@code ENABLED} or {@code PREVIEW}. */
    public static boolean isEnabled(Module module) {
        return state(module).grantsAccess();
    }

    /** True only for {@code PREVIEW}, which adds the {@code [Preview]} marker to player-facing copy. */
    public static boolean isPreview(Module module) {
        return state(module) == ModuleState.PREVIEW;
    }

    public static ModuleState state(Module module) {
        return CACHE.getOrDefault(module, ModuleState.DISABLED);
    }

    public static Map<Module, ModuleState> snapshot() {
        return new EnumMap<>(CACHE);
    }

    public static ModuleSettingsValues settings(Module module) {
        return SETTINGS_CACHE.getOrDefault(module, ModuleSettingsValues.EMPTY);
    }

    // ── cache maintenance: not a public mutation API ──

    /**
     * Replaces the cached states wholesale. Called by {@code ModuleStateService} after the authoritative
     * world data changes, and by the client sync handler. Not a way to change module state — a write here
     * without a matching persist is silently reverted on the next refresh.
     */
    public static void acceptAuthoritative(Map<Module, ModuleState> states) {
        for (Module module : Module.values()) {
            CACHE.put(module, states.getOrDefault(module, ModuleState.DISABLED));
        }
    }

    public static void acceptAuthoritativeSettings(Map<Module, ModuleSettingsValues> settings) {
        SETTINGS_CACHE.clear();
        SETTINGS_CACHE.putAll(settings);
    }

    /**
     * Writes one module's cached state and nothing else: not persisted, not broadcast, and reverted by the next
     * refresh. The real mutation path is {@code ModuleStateService.setState}. This exists for game tests and
     * capture scenes, which share one server and must not trigger the datapack reload that path does.
     */
    public static void overrideCached(Module module, ModuleState state) {
        CACHE.put(module, state);
    }
}
