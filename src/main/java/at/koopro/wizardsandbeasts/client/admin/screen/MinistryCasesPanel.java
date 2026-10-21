package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminOpsState;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTheme;
import at.koopro.wizardsandbeasts.ministry.trace.CaseReport;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * Ministry → Cases: every open case, unsolved incident and posted case, live. Each shows who it is against, how far
 * it has gone, who is working it, its evidence against the bar it has to clear, and every item of evidence with the
 * channel and source it came from — the page an operator reads to tell a real case from a manufactured one. The
 * same rows as {@code /wandb admin ministry cases}.
 */
@NullMarked
final class MinistryCasesPanel extends OpsDocPanel {

    static final String KEY = "admin.wizards_and_beasts.ministry_cases.";

    @Override
    public AdminCategory section() {
        return AdminCategory.MINISTRY;
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
    protected int pollTicks() {
        return 40;
    }

    @Override
    protected void poll() {
        ClientAdminOpsState.requestMinistryCases();
    }

    @Override
    protected List<Line> lines() {
        List<Line> out = new ArrayList<>();
        CaseReport.Snapshot snapshot = ClientAdminOpsState.ministryCases();
        if (snapshot == null) {
            out.add(note("admin.wizards_and_beasts.status.loading"));
            return out;
        }
        out.add(text(KEY + "totals", snapshot.incidents(), snapshot.pending(), snapshot.dossiers()));
        String kind = "";
        for (CaseReport.CaseRow row : snapshot.rows()) {
            if (!row.kind().equals(kind)) {
                kind = row.kind();
                out.add(heading(KEY + "kind." + kind));
            }
            StringBuilder head = new StringBuilder("#").append(row.id());
            if (!row.subject().isEmpty()) {
                head.append(" · ").append(row.subject());
            }
            head.append(" · ").append(row.spell()).append(" (").append(row.legalClass()).append(')');
            if (!row.stage().isEmpty()) {
                head.append(" · ").append(row.stage());
            }
            out.add(plain(head.toString(), AdminTheme.INK));
            out.add(plain("   " + Component.translatable(KEY + "strength", row.strength(), row.bar()).getString()
                    + (row.holder().isEmpty() ? "" : " · "
                    + Component.translatable(KEY + "holder", row.holder()).getString())
                    + " · " + row.where(), row.strength() >= row.bar() ? AdminTheme.BAD : AdminTheme.INK_2));
            out.add(plain("   " + (row.evidence().isEmpty()
                    ? Component.translatable(KEY + "no_evidence").getString() : row.evidence()), AdminTheme.INK_3));
        }
        if (snapshot.rows().isEmpty()) {
            out.add(note(KEY + "empty"));
        }
        out.add(note(KEY + "note"));
        return out;
    }
}
