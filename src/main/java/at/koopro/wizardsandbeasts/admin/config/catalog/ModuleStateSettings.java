package at.koopro.wizardsandbeasts.admin.config.catalog;

import at.koopro.wizardsandbeasts.admin.config.AdminSetting;
import at.koopro.wizardsandbeasts.admin.config.AdminSettingRegistry;
import at.koopro.wizardsandbeasts.admin.config.SettingBinding;
import at.koopro.wizardsandbeasts.admin.config.SettingTypes;
import at.koopro.wizardsandbeasts.admin.module.ModuleAdminInfo;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleDefaults;
import at.koopro.wizardsandbeasts.module.ModuleDependencies;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.module.ModuleState;
import at.koopro.wizardsandbeasts.module.ModuleStateService;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jspecify.annotations.NullMarked;

/**
 * Every module's state as an admin setting, {@code module_<id>}, bound straight to {@link ModuleStateService} — the
 * module system's own "one door" — so the Control Center adds a view, not a second copy of module state.
 *
 * <p>One setting per {@link Module} constant, discovered from the enum: a new module appears in the Modules browser
 * and in commands with no edit here. Most are filed under Modules; a system with its own page keeps its switch there
 * ({@link ModuleAdminInfo#home}).
 *
 * <p>Rules, all judged by the server:
 * <ul>
 *   <li>{@code COMING_SOON} is a roadmap marker: refused as a target and as a source (as the service refuses it);</li>
 *   <li>opening a module whose required module is off is refused, with the sentence that names both
 *       ({@link ModuleDependencies.Edge#blockedKey});</li>
 *   <li>a change asks for confirmation when it opens a sealed module, when it closes a foundational one, and when it
 *       closes a module others depend on — the warning is the dependency's own sentence ("Disabling Player abilities
 *       will also disable Apparition."). The service then closes those dependants in the same step.</li>
 * </ul>
 */
@NullMarked
public final class ModuleStateSettings {

    /**
     * Modules whose closing takes something away from every existing character or every player's belongings, so
     * closing them asks too: the character systems, the bank every fee and fine is drawn from, and the pocket
     * dimensions players keep their things in.
     */
    private static final java.util.Set<Module> FOUNDATIONAL = java.util.EnumSet.of(Module.HERITAGE, Module.PLAYER_STATS,
            Module.GRINGOTTS, Module.POCKET_DIMENSIONS);

    private ModuleStateSettings() {}

    public static void contribute(AdminSettingRegistry registry) {
        for (Module module : Module.values()) {
            registry.register(setting(module));
        }
    }

    private static AdminSetting<ModuleState> setting(Module module) {
        return AdminSetting.builder(ModuleAdminInfo.settingId(module),
                        SettingTypes.enumeration(ModuleState.class), new ModuleStateBinding(module))
                .category(ModuleAdminInfo.home(module))
                .applyMode(ModuleAdminInfo.applyMode(module))
                .validator((candidate, registry) -> {
                    if (candidate == ModuleState.COMING_SOON || ModuleManager.state(module) == ModuleState.COMING_SOON) {
                        return "admin.wizards_and_beasts.conflict.module_coming_soon";
                    }
                    ModuleDependencies.Check check = ModuleDependencies.check(ModuleManager.snapshot(), module, candidate);
                    return check.allowed() ? null : check.blockedBy().get(0).blockedKey();
                })
                .dangerRule((previous, candidate, authored, s) -> {
                    ModuleDependencies.Check check = ModuleDependencies.check(ModuleManager.snapshot(), module, candidate);
                    if (!check.cascade().isEmpty()) {
                        // Name the first dependant that goes with it; the detail page lists them all.
                        Module first = check.cascade().get(check.cascade().size() - 1);
                        for (ModuleDependencies.Edge edge : ModuleDependencies.dependantsOf(module)) {
                            if (edge.dependent() == first) {
                                return edge.effectKey();
                            }
                        }
                    }
                    if (!check.weakened().isEmpty()) {
                        return check.weakened().get(0).effectKey();
                    }
                    // Opening a sealed module to every player is the dangerous direction; for the systems every
                    // character is built on, so is sealing one that is open.
                    return (previous == ModuleState.DISABLED) != (candidate == ModuleState.DISABLED)
                            && (previous == ModuleState.DISABLED || FOUNDATIONAL.contains(module))
                            ? s.warningKey() : null;
                })
                .build();
    }

    private record ModuleStateBinding(Module module) implements SettingBinding<ModuleState> {
        @Override
        public ModuleState get() {
            return ModuleManager.state(module);
        }

        @Override
        public void set(ModuleState value) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null || !ModuleStateService.setState(server, module, value).ok()) {
                throw new IllegalStateException("module state change refused for " + module);
            }
        }

        @Override
        public ModuleState defaultValue() {
            return ModuleDefaults.configuredDefault(module);
        }

        @Override
        public boolean available() {
            return ServerLifecycleHooks.getCurrentServer() != null;
        }
    }
}
