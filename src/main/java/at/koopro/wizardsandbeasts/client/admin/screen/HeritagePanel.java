package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Heritages, as three tabs over one section: <b>Heritages</b> (the {@link HeritageBrowserPanel} — every heritage,
 * its per-heritage rules and preview), <b>Rules</b> (the generic {@link SettingsPanel} over the section's global
 * settings: modules, werewolf and vampire rules) and <b>Players</b> (the {@link HeritagePlayersPanel}).
 */
@NullMarked
final class HeritagePanel extends TabbedPanel {

    enum Tab { HERITAGES, RULES, PLAYERS }

    static Tab tab = Tab.HERITAGES;

    HeritagePanel() {
        super(AdminCategory.HERITAGES, List.of(
                new TabbedPanel.Tab("admin.wizards_and_beasts.tab.heritages", HeritageBrowserPanel::new),
                new TabbedPanel.Tab("admin.wizards_and_beasts.tab.rules", () -> new SettingsPanel(AdminCategory.HERITAGES)),
                new TabbedPanel.Tab("admin.wizards_and_beasts.tab.players", HeritagePlayersPanel::new)),
                () -> tab.ordinal(), index -> tab = Tab.values()[index]);
    }
}
