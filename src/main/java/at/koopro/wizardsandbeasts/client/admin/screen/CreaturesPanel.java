package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Creatures, as three tabs: <b>Creatures</b> (the {@link CreatureBrowserPanel} — roster, pages, variants, test spawns,
 * the Entity Viewer), <b>Spawning</b> (every natural-spawn rule as one checklist) and <b>Rules</b> (the generic {@link SettingsPanel} over the section's global settings: the
 * Creatures module and the natural-spawn roster setting).
 */
@NullMarked
final class CreaturesPanel extends TabbedPanel {

    enum Tab { CREATURES, SPAWNING, RULES, LIVE }

    static Tab tab = Tab.CREATURES;

    CreaturesPanel() {
        super(AdminCategory.CREATURES, List.of(
                new TabbedPanel.Tab("admin.wizards_and_beasts.tab.creatures", CreatureBrowserPanel::new),
                new TabbedPanel.Tab("admin.wizards_and_beasts.tab.spawning", CreatureSpawningPanel::new),
                new TabbedPanel.Tab("admin.wizards_and_beasts.tab.rules", () -> new SettingsPanel(AdminCategory.CREATURES)),
                new TabbedPanel.Tab("admin.wizards_and_beasts.tab.creature_live",
                        () -> new AdminReportPanel(AdminCategory.CREATURES, "creature_live"))),
                () -> tab.ordinal(), index -> tab = Tab.values()[index]);
    }
}
