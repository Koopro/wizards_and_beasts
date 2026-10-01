package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Modules, as two tabs: <b>Modules</b> (every registered module, its state, dependencies, dependants, where it runs
 * and when a change takes effect, with Enable / Preview / Disable) and <b>Profiles</b> (groundwork: the reserved
 * profile names and what reaching Default would change).
 */
@NullMarked
final class ModulesPanel extends TabbedPanel {

    static int tab;

    ModulesPanel() {
        super(AdminCategory.MODULES, List.of(
                new Tab("admin.wizards_and_beasts.tab.modules", ModuleBrowserPanel::new),
                new Tab("admin.wizards_and_beasts.tab.profiles", ModuleProfilesPanel::new)),
                () -> tab, index -> tab = index);
    }
}
