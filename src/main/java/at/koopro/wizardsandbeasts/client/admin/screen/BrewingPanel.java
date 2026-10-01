package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Brewing, as two tabs: <b>Brews</b> (the {@link BrewBrowserPanel} — every brew, its effects, recipe and preview) and
 * <b>Rules</b> (the generic {@link SettingsPanel} over the global brewing rules).
 */
@NullMarked
final class BrewingPanel extends TabbedPanel {

    enum Tab { BREWS, RULES }

    static Tab tab = Tab.BREWS;

    BrewingPanel() {
        super(AdminCategory.BREWING, List.of(
                new TabbedPanel.Tab("admin.wizards_and_beasts.tab.brews", BrewBrowserPanel::new),
                new TabbedPanel.Tab("admin.wizards_and_beasts.tab.rules", () -> new SettingsPanel(AdminCategory.BREWING))),
                () -> tab.ordinal(), index -> tab = Tab.values()[index]);
    }
}
