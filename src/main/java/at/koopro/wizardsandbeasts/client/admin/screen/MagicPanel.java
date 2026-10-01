package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Magic, as two tabs over one section: <b>Rules</b> (the server-wide spell settings, the generic
 * {@link SettingsPanel} over the MAGIC category) and <b>Spells</b> (the {@link SpellBrowserPanel}).
 */
@NullMarked
final class MagicPanel extends TabbedPanel {

    enum Tab { RULES, SPELLS }

    /** Remembered across visits; {@link DarkArtsPanel} opens the Spells tab to show a spell. */
    static Tab tab = Tab.RULES;

    MagicPanel() {
        super(AdminCategory.MAGIC, List.of(
                new TabbedPanel.Tab("admin.wizards_and_beasts.tab.rules", () -> new SettingsPanel(AdminCategory.MAGIC)),
                new TabbedPanel.Tab("admin.wizards_and_beasts.tab.spells", SpellBrowserPanel::new)),
                () -> tab.ordinal(), index -> tab = Tab.values()[index]);
    }
}
