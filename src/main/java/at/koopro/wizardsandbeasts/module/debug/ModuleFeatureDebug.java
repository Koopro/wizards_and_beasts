package at.koopro.wizardsandbeasts.module.debug;

import at.koopro.wizardsandbeasts.command.debug.feature.FeatureDebugSection;
import at.koopro.wizardsandbeasts.command.debug.report.DebugReport;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.module.ModuleState;
import at.koopro.wizardsandbeasts.util.ChatPalette;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * Every switch, and how many of them are off.
 *
 * <p>Last in the report on purpose. It is not about the player at all — it is the answer to "why
 * does none of the above do anything", and putting it at the end means you read the state first and
 * then find out it was switched off, which is the order that teaches you something.
 */
@NullMarked
public final class ModuleFeatureDebug implements FeatureDebugSection {

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
        return "Every module and its state - the reason a working subsystem can do nothing.";
    }

    @Override
    public void append(DebugReport report, ServerPlayer target, Detail detail) {
        int enabled = 0;
        int preview = 0;
        int disabled = 0;
        for (Module module : Module.values()) {
            ModuleState state = ModuleManager.state(module);
            switch (state) {
                case ENABLED -> enabled++;
                case PREVIEW -> preview++;
                default -> disabled++;
            }
        }
        report.row("  enabled / preview / disabled", enabled + " / " + preview + " / " + disabled);
        if (detail == Detail.BRIEF) {
            return;
        }
        for (Module module : Module.values()) {
            ModuleState state = ModuleManager.state(module);
            report.state("  " + module.name(), String.valueOf(state), colourFor(state));
        }
    }

    private static int colourFor(ModuleState state) {
        return switch (state) {
            case ENABLED -> ChatPalette.OK;
            case PREVIEW -> ChatPalette.WARN;
            default -> ChatPalette.BAD;
        };
    }
}
