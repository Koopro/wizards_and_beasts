package at.koopro.wizardsandbeasts.module.debug;

import at.koopro.wizardsandbeasts.command.debug.dev.DevLog;
import at.koopro.wizardsandbeasts.command.debug.dev.FeatureDevKit;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.module.ModuleState;
import at.koopro.wizardsandbeasts.module.ModuleStateService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * The master switch, run first.
 *
 * <p>A disabled module swallows its subsystem whole, so every other kit that ran before this one
 * would report a gate opened on a feature that still cannot do anything. Enabling everything is
 * therefore the first thing dev setup does, not an afterthought.
 *
 * <p>{@code ENABLED}, not {@code PREVIEW}: preview is a shipping decision about what players are
 * allowed to find, and a developer testing a subsystem wants it on rather than on-with-caveats.
 */
@NullMarked
public final class ModuleDevKit implements FeatureDevKit {

    @Override
    public String id() {
        return "modules";
    }

    @Override
    public String title() {
        return "Modules";
    }

    @Override
    public String summary() {
        return "Switch every module fully on, so nothing below silently swallows its subsystem.";
    }

    @Override
    public void open(ServerPlayer target, DevLog log) {
        MinecraftServer server = target.level().getServer();
        int changed = 0;
        for (Module module : Module.values()) {
            if (ModuleManager.state(module) == ModuleState.ENABLED) {
                continue;
            }
            ModuleStateService.setState(server, module, ModuleState.ENABLED);
            changed++;
        }
        if (changed == 0) {
            log.skip("every module was already ENABLED");
        } else {
            log.changed("modules enabled", changed);
        }
    }

    /**
     * Not implemented, and deliberately so.
     *
     * <p>There is no stored "what it was before", and the shipped defaults are a per-module editorial
     * decision — some modules ship {@code PREVIEW} and two ship {@code DISABLED} because their content
     * is a placeholder. Guessing at that here would quietly rewrite a deliberate configuration.
     * {@code /wandb admin module set} is the honest way back.
     */
    @Override
    public void reset(ServerPlayer target, DevLog log) {
        log.skip("module states are a deliberate configuration - use /wandb admin module set");
    }
}
