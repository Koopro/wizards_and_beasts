package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Wands, as four tabs: <b>Woods</b> and <b>Cores</b> ({@link WandPartsPanel} — each part, its pairings, lore and
 * numbers), <b>Generator</b> ({@link WandGeneratorPanel} — the production model of any make, checked by the server) and
 * <b>Rules</b> (the generic {@link SettingsPanel} over allegiance and affinity).
 */
@NullMarked
final class WandsPanel extends TabbedPanel {

    enum Tab { WOODS, CORES, GENERATOR, RULES }

    static Tab tab = Tab.WOODS;

    WandsPanel() {
        super(AdminCategory.WANDS, List.of(
                new TabbedPanel.Tab("admin.wizards_and_beasts.tab.woods", () -> new WandPartsPanel(true)),
                new TabbedPanel.Tab("admin.wizards_and_beasts.tab.cores", () -> new WandPartsPanel(false)),
                new TabbedPanel.Tab("admin.wizards_and_beasts.tab.generator", WandGeneratorPanel::new),
                new TabbedPanel.Tab("admin.wizards_and_beasts.tab.rules", () -> new SettingsPanel(AdminCategory.WANDS))),
                () -> tab.ordinal(), index -> tab = Tab.values()[index]);
    }
}
