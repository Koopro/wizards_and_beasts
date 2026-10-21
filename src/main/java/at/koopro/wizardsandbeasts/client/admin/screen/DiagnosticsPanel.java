package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Diagnostics: network, performance, registry, data validation, modules and the audit timeline. Every tab but
 * Performance is an {@link AdminReportPanel} over a server report, so each one also prints with
 * {@code /wandb admin report <id>}.
 */
@NullMarked
final class DiagnosticsPanel extends TabbedPanel {

    static int tab;

    DiagnosticsPanel() {
        super(AdminCategory.DIAGNOSTICS, List.of(
                new Tab("admin.wizards_and_beasts.tab.diag_network",
                        () -> new AdminReportPanel(AdminCategory.DIAGNOSTICS, "diag_network")),
                new Tab("admin.wizards_and_beasts.tab.diag_performance", PerfLivePanel::new),
                new Tab("admin.wizards_and_beasts.tab.diag_registry",
                        () -> new AdminReportPanel(AdminCategory.DIAGNOSTICS, "diag_registry", 0)),
                new Tab("admin.wizards_and_beasts.tab.diag_data",
                        () -> new AdminReportPanel(AdminCategory.DIAGNOSTICS, "diag_data", 0)),
                new Tab("admin.wizards_and_beasts.tab.diag_modules",
                        () -> new AdminReportPanel(AdminCategory.DIAGNOSTICS, "diag_modules")),
                new Tab("admin.wizards_and_beasts.tab.audit",
                        () -> new AdminReportPanel(AdminCategory.DIAGNOSTICS, "audit", 100))),
                () -> tab, index -> tab = index);
    }
}
