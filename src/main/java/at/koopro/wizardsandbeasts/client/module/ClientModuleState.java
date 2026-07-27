package at.koopro.wizardsandbeasts.client.module;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.module.ModuleState;
import at.koopro.wizardsandbeasts.module.settings.ModuleSettingsValues;
import at.koopro.wizardsandbeasts.network.module.ModuleStateSyncPayload;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Client mirror of the server's module state. Purely a receiver: it pushes what the server sent into the
 * {@link ModuleManager} read cache and never decides anything itself, so a client cannot talk itself into
 * believing a module is on.
 *
 * <p>Class-init-safe — only common types — so referencing it from payload registration does not load a
 * client-only class on a dedicated server.
 */
@NullMarked
public final class ClientModuleState {

    private ClientModuleState() {}

    /**
     * Run after every applied sync. A plain {@link Runnable} rather than a direct call so that display
     * code — which is client-only — can react without this class naming a client-only type; see the class
     * note on staying init-safe for a dedicated server.
     */
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    public static void whenApplied(Runnable listener) {
        LISTENERS.add(listener);
    }

    public static void accept(ModuleStateSyncPayload payload) {
        Map<Module, ModuleState> states = payload.resolvedStates();
        Map<Module, ModuleSettingsValues> settings = payload.resolvedSettings();
        ModuleManager.acceptAuthoritative(states);
        ModuleManager.acceptAuthoritativeSettings(settings);
        LISTENERS.forEach(Runnable::run);
    }
}
