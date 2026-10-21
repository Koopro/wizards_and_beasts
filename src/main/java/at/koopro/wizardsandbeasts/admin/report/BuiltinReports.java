package at.koopro.wizardsandbeasts.admin.report;

import org.jspecify.annotations.NullMarked;

/** The reports the mod ships. One line per family; each family's class registers its own. */
@NullMarked
final class BuiltinReports {

    private BuiltinReports() {}

    static void registerAll() {
        AdminReports.register(new CreatureLiveReport());
        WorldReports.registerAll();
        MinistryReports.registerAll();
        DiagnosticReports.registerAll();
        EconomyReports.registerAll();
        AdminReports.register(new AuditReport());
    }
}
