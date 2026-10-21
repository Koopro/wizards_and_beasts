package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.report.ReportBuilder;
import at.koopro.wizardsandbeasts.admin.report.ReportView;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminReportState;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminButton;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminConfirmDialog;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminPicker;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTextField;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTheme;
import at.koopro.wizardsandbeasts.network.admin.AdminReportPayloads;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The one page that shows any server {@code AdminReport}: a subject picker when the report can be pointed at
 * something (a player, a creature), a filter over the lines, a refresh, and a button for every operation the report
 * offers on the current subject. Everything it shows comes from the report's last answer; every button sends the
 * operation's id and lets the server decide — a destructive one asks first, and one the server holds for
 * confirmation is asked again. The same report prints through {@code /wandb admin report <id>}.
 *
 * <p>A section shows a report by adding {@code new AdminReportPanel(section, "<report id>")} as a tab; nothing else
 * is needed. The chosen subject and filter are remembered per report while the game runs.
 */
@NullMarked
final class AdminReportPanel extends OpsDocPanel {

    private static final int BUTTON_H = 16;
    private static final String KEY = "admin.wizards_and_beasts.report_page.";
    private static final Map<String, String> SUBJECTS = new HashMap<>();
    private static final Map<String, String> FILTERS = new HashMap<>();

    private final AdminCategory section;
    private final String reportId;
    private final int pollTicks;
    /** Where this page's subject and filter are remembered: the same report on two sections keeps two selections. */
    private final String memory;
    private String controlsSignature = "";
    private AdminReportPayloads.@Nullable OperationReply shownOperation;

    AdminReportPanel(AdminCategory section, String reportId) {
        this(section, reportId, 40);
    }

    /** {@code pollTicks} 0 asks once when shown (Refresh asks again). */
    AdminReportPanel(AdminCategory section, String reportId, int pollTicks) {
        this(section, reportId, pollTicks, "");
    }

    /** {@code defaultSubject}: what the page is pointed at until the administrator picks something else. */
    AdminReportPanel(AdminCategory section, String reportId, int pollTicks, String defaultSubject) {
        this.section = section;
        this.reportId = reportId;
        this.pollTicks = pollTicks;
        this.memory = section.id() + "/" + reportId;
        SUBJECTS.putIfAbsent(memory, defaultSubject);
    }

    private String subject() {
        return SUBJECTS.getOrDefault(memory, "");
    }

    private String filter() {
        return FILTERS.getOrDefault(memory, "");
    }

    private AdminReportPayloads.@Nullable ReportReply reply() {
        return ClientAdminReportState.report(reportId, subject());
    }

    @Override
    public AdminCategory section() {
        return section;
    }

    @Override
    protected Component title() {
        return Component.translatable(ReportBuilder.KEY_ROOT + reportId + ".title");
    }

    @Override
    protected Component summary() {
        return Component.translatable(ReportBuilder.KEY_ROOT + reportId + ".summary");
    }

    private boolean asked;

    @Override
    public void init(AdminPanelHost host, int x, int y, int w, int h) {
        super.init(host, x, y, w, h);
        // A page that does not poll still asks once when it is first shown.
        if (pollTicks == 0 && !asked) {
            asked = true;
            poll();
        }
    }

    @Override
    protected int pollTicks() {
        return pollTicks;
    }

    @Override
    protected void poll() {
        ClientAdminReportState.request(reportId, subject());
    }

    @Override
    protected int stateVersion() {
        return ClientAdminReportState.version();
    }

    @Override
    public @Nullable Component crumb() {
        AdminReportPayloads.ReportReply reply = reply();
        if (reply == null || subject().isEmpty()) {
            return null;
        }
        return reply.view().subjects().stream().filter(s -> s.id().equals(subject())).findFirst()
                .map(s -> (Component) Component.literal(s.label())).orElse(Component.literal(subject()));
    }

    // ── controls ──

    private String signature() {
        AdminReportPayloads.ReportReply reply = reply();
        if (reply == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        reply.view().subjects().forEach(s -> out.append(s.id()).append(',').append(s.label()).append(';'));
        out.append('|');
        reply.view().actions().forEach(a -> out.append(a.operation()).append(',').append(a.argument()).append(',')
                .append(a.labelKey()).append(';'));
        return out.toString();
    }

    @Override
    protected boolean rebuildsControls() {
        // An answer with the same subjects and buttons only re-lays the lines out, so typing in the filter is never
        // interrupted by a poll; a new subject list or button set rebuilds.
        AdminReportPayloads.OperationReply operation = ClientAdminReportState.lastOperation();
        if (operation != null && operation != shownOperation) {
            shownOperation = operation;
            onOperation(operation);
        }
        return !signature().equals(controlsSignature);
    }

    @Override
    protected int controls(AdminPanelHost host, Font font, int left, int top, int width) {
        controlsSignature = signature();
        AdminReportPayloads.ReportReply reply = reply();
        int row = top;
        int refreshW = 54;
        int x = left;
        List<ReportView.Subject> subjects = reply == null ? List.of() : reply.view().subjects();
        if (!subjects.isEmpty() || !subject().isEmpty()) {
            int pickerW = Math.min(180, (width - refreshW - 2 * AdminTheme.GAP) / 2);
            List<AdminPicker.Option> options = new ArrayList<>();
            options.add(new AdminPicker.Option("", Component.translatable(KEY + "no_subject")));
            for (ReportView.Subject s : subjects) {
                options.add(new AdminPicker.Option(s.id(), Component.literal(s.label()), s.note()));
            }
            host.addPanelWidget(new AdminPicker(x, row, pickerW, BUTTON_H, options, subject(),
                    Component.translatable(KEY + "pick"), picked -> {
                SUBJECTS.put(memory, picked);
                ClientAdminReportState.request(reportId, picked);
                host.requestRebuild();
            }));
            x += pickerW + AdminTheme.GAP;
        }
        int filterW = left + width - refreshW - AdminTheme.GAP - x;
        AdminTextField search = host.addPanelWidget(AdminTextField.search(font, x, row + 1, filterW, filter(), text -> {
            FILTERS.put(memory, text);
            relayout();
        }));
        search.setTooltip(Tooltip.create(Component.translatable(KEY + "filter_tip")));
        AdminButton refresh = host.addPanelWidget(new AdminButton(left + width - refreshW, row, refreshW, BUTTON_H,
                Component.translatable(KEY + "refresh"), AdminButton.Tone.QUIET, this::poll));
        refresh.setTooltip(Tooltip.create(Component.translatable(KEY + "refresh_tip")));
        row += BUTTON_H + AdminTheme.GAP;

        List<ReportView.Action> actions = reply == null ? List.of() : reply.view().actions();
        if (!actions.isEmpty()) {
            int bx = left;
            for (ReportView.Action action : actions) {
                Component label = Component.translatable(action.labelKey(), action.argument());
                int bw = Math.min(width, font.width(label) + 14);
                if (bx + bw > left + width) {
                    bx = left;
                    row += BUTTON_H + AdminTheme.GAP;
                }
                AdminButton button = host.addPanelWidget(new AdminButton(bx, row, bw, BUTTON_H, label,
                        action.destructive() ? AdminButton.Tone.DANGER : AdminButton.Tone.NEUTRAL,
                        () -> press(host, action, false)));
                button.setTooltip(Tooltip.create(Component.translatable(
                        "admin.wizards_and_beasts.operation." + action.operation() + ".desc")));
                bx += bw + AdminTheme.GAP;
            }
            row += BUTTON_H;
        }
        return row - top;
    }

    private void press(AdminPanelHost host, ReportView.Action action, boolean serverAsked) {
        if (action.destructive() || serverAsked) {
            confirm(host, action.operation(), action.subject(), action.argument(),
                    Component.translatable(action.labelKey(), action.argument()));
            return;
        }
        ClientAdminReportState.perform(action.operation(), action.subject(), action.argument(), false);
    }

    private void confirm(AdminPanelHost host, String operation, String subject, String argument, Component label) {
        String key = "admin.wizards_and_beasts.operation." + operation;
        host.openDialog(new AdminConfirmDialog(Component.translatable(KEY + "confirm_title", label),
                List.of(Component.translatable(KEY + "confirm_body", label, subjectLabel(subject)),
                        Component.translatable(key + ".warning")),
                label, true,
                () -> {
                    host.closeDialog();
                    ClientAdminReportState.perform(operation, subject, argument, true);
                },
                host::closeDialog));
    }

    private String subjectLabel(String id) {
        AdminReportPayloads.ReportReply reply = reply();
        if (reply != null) {
            for (ReportView.Subject s : reply.view().subjects()) {
                if (s.id().equals(id)) {
                    return s.label();
                }
            }
        }
        return id;
    }

    /** An operation answered: a held one is confirmed through the dialog; anything else refreshes the report. */
    private void onOperation(AdminReportPayloads.OperationReply operation) {
        if (host == null) {
            return;
        }
        if (!operation.success() && operation.code().equals("confirm_required")) {
            confirm(host, operation.operationId(), operation.subject(), operation.argument(),
                    Component.translatable("admin.wizards_and_beasts.operation." + operation.operationId()));
            return;
        }
        // A discarded creature is gone: drop back to the list instead of showing "not loaded".
        if (operation.success() && operation.operationId().endsWith("_discard")) {
            SUBJECTS.put(memory, "");
        }
        poll();
    }

    // ── document ──

    @Override
    protected List<Line> lines() {
        List<Line> out = new ArrayList<>();
        AdminReportPayloads.OperationReply operation = ClientAdminReportState.lastOperation();
        if (operation != null && operation.subject().equals(subject()) && !operation.code().equals("confirm_required")) {
            Component name = Component.translatable("admin.wizards_and_beasts.operation." + operation.operationId());
            out.add(Line.text(operation.success()
                    ? Component.translatable(KEY + "op_ok", name, operation.detail())
                    : Component.translatable(KEY + "op_refused", name,
                    Component.translatable(KEY + "code." + operation.code()), operation.detail()),
                    operation.success() ? AdminTheme.GOOD : AdminTheme.BAD));
        }
        AdminReportPayloads.ReportReply reply = reply();
        if (reply == null) {
            out.add(note("admin.wizards_and_beasts.status.loading"));
            return out;
        }
        if (!reply.status().equals("ok")) {
            out.add(Line.text(Component.translatable(KEY + "status." + reply.status()), AdminTheme.BAD));
            return out;
        }
        String needle = filter().trim().toLowerCase(Locale.ROOT);
        for (ReportView.Section section : reply.view().sections()) {
            Component heading = section.headingKey() ? Component.translatable(section.heading())
                    : Component.literal(section.heading());
            List<Line> body = new ArrayList<>();
            boolean headingMatches = !needle.isEmpty() && heading.getString().toLowerCase(Locale.ROOT).contains(needle);
            for (ReportView.Line line : section.lines()) {
                Component label = line.labelKey() ? Component.translatable(line.label()) : Component.literal(line.label());
                Component text = line.value().isEmpty() ? label : label.copy().append(": ").append(
                        line.valueKey() ? Component.translatable(line.value()) : Component.literal(line.value()));
                if (!needle.isEmpty() && !headingMatches && !text.getString().toLowerCase(Locale.ROOT).contains(needle)) {
                    continue;
                }
                body.add(Line.text(text, colour(line)));
            }
            if (body.isEmpty() && !needle.isEmpty()) {
                continue;
            }
            if (!section.heading().isEmpty()) {
                out.add(Line.heading(heading));
            }
            out.addAll(body);
        }
        if (!needle.isEmpty() && out.isEmpty()) {
            out.add(note(KEY + "no_match", filter()));
        }
        return out;
    }

    private static int colour(ReportView.Line line) {
        if (line.value().isEmpty() && line.tone() == ReportView.Tone.MUTED) {
            return AdminTheme.INK_3;
        }
        return switch (line.tone()) {
            case NORMAL -> AdminTheme.INK;
            case GOOD -> AdminTheme.GOOD;
            case WARN -> AdminTheme.WAX;
            case BAD -> AdminTheme.BAD;
            case MUTED -> AdminTheme.INK_3;
        };
    }
}
