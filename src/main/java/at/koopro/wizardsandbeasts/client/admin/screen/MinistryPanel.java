package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Ministry, as three tabs: <b>Rules</b> (the Ministry switch, fines, ageing, notoriety cooling, appeals, licences,
 * casework — every setting the law actually reads), <b>Law</b> (the offences, wanted bands, Trace channels, evidence
 * standards, case ladder and Azkaban as enforced, read-only) and <b>Cases</b> (every case now open, with its evidence
 * and where that evidence came from, live).
 */
@NullMarked
final class MinistryPanel extends TabbedPanel {

    static int tab;

    MinistryPanel() {
        super(AdminCategory.MINISTRY, List.of(
                new Tab("admin.wizards_and_beasts.tab.rules", () -> new SettingsPanel(AdminCategory.MINISTRY, id -> true,
                        null, null).note(Component.translatable("admin.wizards_and_beasts.ministry_rules.note"))),
                new Tab("admin.wizards_and_beasts.tab.ministry_law", MinistryLawPanel::new),
                new Tab("admin.wizards_and_beasts.tab.ministry_cases", MinistryCasesPanel::new),
                new Tab("admin.wizards_and_beasts.tab.ministry_licences",
                        () -> new AdminReportPanel(AdminCategory.MINISTRY, "ministry_licences")),
                new Tab("admin.wizards_and_beasts.tab.ministry_standing",
                        () -> new AdminReportPanel(AdminCategory.MINISTRY, "ministry_standing")),
                new Tab("admin.wizards_and_beasts.tab.ministry_debug",
                        () -> new AdminReportPanel(AdminCategory.MINISTRY, "ministry_debug"))),
                () -> tab, index -> tab = index);
    }
}
