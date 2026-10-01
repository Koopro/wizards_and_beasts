package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTheme;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleDependencies;
import at.koopro.wizardsandbeasts.module.ModuleIds;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.module.profile.ModuleProfile;
import at.koopro.wizardsandbeasts.module.profile.ModuleProfilePlanner;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * Modules → Profiles: groundwork, read-only. Lists the reserved profile names; for a defined profile (only Default,
 * the build's shipped states, today) it shows the plan that would reach it from the server's current states — the
 * ordered changes, any locked modules, any dependency it would break. Nothing is applied from here yet.
 */
@NullMarked
final class ModuleProfilesPanel extends AdminInfoPanel {

    private static final String KEY = "admin.wizards_and_beasts.module_profiles.";

    @Override
    public AdminCategory section() {
        return AdminCategory.MODULES;
    }

    @Override
    protected Component title() {
        return Component.translatable(KEY + "title");
    }

    @Override
    protected Component summary() {
        return Component.translatable(KEY + "summary");
    }

    @Override
    protected List<Block> blocks() {
        List<Block> out = new ArrayList<>();
        List<Module> starved = ModuleBrowserPanel.starved();
        if (!starved.isEmpty()) {
            out.add(heading(KEY + "inconsistent"));
            for (Module module : starved) {
                out.add(new Para(Component.literal("✖ ").append(ModuleIds.displayName(module)), AdminTheme.BAD));
            }
        }
        for (ModuleProfile profile : ModuleProfile.values()) {
            out.add(new Heading(Component.translatable(profile.nameKey())));
            out.add(note(profile.descriptionKey()));
            if (!profile.defined()) {
                out.add(new Para(Component.translatable(KEY + "undefined"), AdminTheme.INK_3));
                continue;
            }
            ModuleProfilePlanner.Plan plan = ModuleProfilePlanner.plan(ModuleManager.snapshot(), profile.states());
            if (plan.steps().isEmpty() && plan.locked().isEmpty()) {
                out.add(new Para(Component.translatable(KEY + "matches"), AdminTheme.GOOD));
            } else {
                out.add(para(KEY + "changes", plan.steps().size()));
                for (ModuleProfilePlanner.Step step : plan.steps()) {
                    out.add(row(ModuleIds.displayName(step.module()), Component.empty()
                            .append(step.from().displayName()).append(" → ").append(step.to().displayName())));
                }
                for (Module module : plan.locked()) {
                    out.add(row(ModuleIds.displayName(module), Component.translatable(KEY + "locked")));
                }
                for (ModuleDependencies.Edge edge : plan.broken()) {
                    out.add(new Para(Component.translatable(edge.blockedKey()), AdminTheme.BAD));
                }
            }
            out.add(note(KEY + "not_applied"));
        }
        return out;
    }
}
