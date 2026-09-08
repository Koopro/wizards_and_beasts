package at.koopro.wizardsandbeasts.command.debug.feature;

import at.koopro.wizardsandbeasts.command.debug.report.DebugReport;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.util.ChatPalette;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every feature that can describe a player, in the order a report reads best.
 *
 * <p>Registration order is print order, and it is deliberate: who the player is, then what they can
 * do, then what the world thinks of them, then the things they are carrying or riding. A dump that
 * opens with broom durability and buries the heritage is a dump nobody reads twice.
 */
@NullMarked
public final class FeatureDebugSections {

    private static final Map<String, FeatureDebugSection> SECTIONS = new LinkedHashMap<>();
    private static boolean bootstrapped;

    private FeatureDebugSections() {}

    public static void bootstrap() {
        if (bootstrapped) return;
        bootstrapped = true;
        // Who they are.
        register(new at.koopro.wizardsandbeasts.heritage.debug.HeritageFeatureDebug());
        register(new at.koopro.wizardsandbeasts.heritage.debug.TransformationFeatureDebug());
        register(new at.koopro.wizardsandbeasts.stats.debug.StatsFeatureDebug());
        // What they can do.
        register(new at.koopro.wizardsandbeasts.spell.debug.SpellFeatureDebug());
        register(new at.koopro.wizardsandbeasts.wand.debug.WandFeatureDebug());
        register(new at.koopro.wizardsandbeasts.skill.debug.SkillFeatureDebug());
        register(new at.koopro.wizardsandbeasts.ability.debug.AbilityFeatureDebug());
        // What the world thinks of them.
        register(new at.koopro.wizardsandbeasts.ministry.debug.MinistryFeatureDebug());
        register(new at.koopro.wizardsandbeasts.standing.debug.StandingFeatureDebug());
        register(new at.koopro.wizardsandbeasts.currency.debug.GringottsFeatureDebug());
        register(new at.koopro.wizardsandbeasts.owl.debug.OwlFeatureDebug());
        // Where they can go.
        register(new at.koopro.wizardsandbeasts.floo.debug.FlooFeatureDebug());
        register(new at.koopro.wizardsandbeasts.trunk.debug.PocketFeatureDebug());
        // What they are carrying, riding and studying.
        register(new at.koopro.wizardsandbeasts.entity.debug.BroomFeatureDebug());
        register(new at.koopro.wizardsandbeasts.item.armor.debug.ArmorFeatureDebug());
        register(new at.koopro.wizardsandbeasts.bestiary.debug.BestiaryFeatureDebug());
        register(new at.koopro.wizardsandbeasts.brew.debug.BrewFeatureDebug());
        // The switches over all of it.
        register(new at.koopro.wizardsandbeasts.module.debug.ModuleFeatureDebug());
    }

    public static void register(FeatureDebugSection section) {
        SECTIONS.put(section.id(), section);
    }

    public static List<FeatureDebugSection> all() {
        bootstrap();
        return List.copyOf(SECTIONS.values());
    }

    public static @Nullable FeatureDebugSection byId(String id) {
        bootstrap();
        return SECTIONS.get(id);
    }

    public static List<String> ids() {
        bootstrap();
        return new ArrayList<>(SECTIONS.keySet());
    }

    /** One section, on its own. */
    public static DebugReport report(FeatureDebugSection section, ServerPlayer target,
                                     FeatureDebugSection.Detail detail) {
        DebugReport report = DebugReport.of(section.title() + " · " + target.getName().getString());
        appendModuleState(report, section);
        section.append(report, target, detail);
        return report;
    }

    /**
     * Every section, in one report.
     *
     * <p>A section that throws is caught and reported as a failed section rather than taking the
     * whole dump down with it. This is diagnostic code run against broken state by definition; the
     * one thing it must not do is stop working exactly when something is wrong.
     */
    public static DebugReport reportAll(ServerPlayer target, FeatureDebugSection.Detail detail) {
        bootstrap();
        DebugReport report = DebugReport.of("Everything · " + target.getName().getString());
        for (FeatureDebugSection section : SECTIONS.values()) {
            report.section(section.title());
            appendModuleState(report, section);
            try {
                section.append(report, target, detail);
            } catch (RuntimeException ex) {
                report.warn("  section '" + section.id() + "' threw: " + ex);
            }
        }
        return report;
    }

    private static void appendModuleState(DebugReport report, FeatureDebugSection section) {
        Module module = section.module();
        if (module == null) {
            return;
        }
        var state = ModuleManager.state(module);
        report.state("  module", module.name() + " · " + state,
                ModuleManager.isEnabled(module) ? ChatPalette.OK : ChatPalette.BAD);
    }
}
