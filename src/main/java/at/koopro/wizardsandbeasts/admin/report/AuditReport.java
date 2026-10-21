package at.koopro.wizardsandbeasts.admin.report;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.AdminSettings;
import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.admin.history.AdminChangeRecord;
import at.koopro.wizardsandbeasts.admin.operation.AdminAuditLog;
import at.koopro.wizardsandbeasts.admin.player.PlayerActionLog;
import at.koopro.wizardsandbeasts.admin.report.ReportView.Tone;
import org.jspecify.annotations.NullMarked;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * One timeline of everything administrators did: setting changes ({@code AdminChangeHistory}), player actions
 * ({@link PlayerActionLog}) and operations ({@link AdminAuditLog}), newest first, read from the three stores — none
 * is copied into another. The subject narrows it: {@code settings}, {@code players}, {@code operations} or
 * {@code money} (every deposit and withdrawal an administrator made).
 */
@NullMarked
final class AuditReport implements AdminReport {

    static final String ID = "audit";
    static final int LIMIT = 200;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss", Locale.ROOT)
            .withZone(ZoneId.systemDefault());

    @Override
    public String id() {
        return ID;
    }

    @Override
    public AdminCategory section() {
        return AdminCategory.DIAGNOSTICS;
    }

    @Override
    public AdminCapability capability() {
        return AdminCapability.CONFIG;
    }

    private record Event(long time, String source, String who, String what, String result, boolean ok) {}

    @Override
    public void build(Request request, ReportBuilder out) {
        String filter = request.subject();
        for (String option : List.of("settings", "players", "operations", "money")) {
            out.subjectOption(option, option, "");
        }
        List<Event> events = new ArrayList<>();
        if (filter.isEmpty() || filter.equals("settings")) {
            for (AdminChangeRecord record : AdminSettings.service().history().recent(LIMIT)) {
                events.add(new Event(record.timestampMillis(), "setting", record.actorName(),
                        record.settingId().getPath() + ": " + record.oldValue() + " → " + record.newValue()
                                + (record.group() == null ? "" : " [" + record.group() + "]"),
                        record.applied() ? (record.undone() ? "undone" : "ok")
                                : "refused:" + (record.rejection() == null ? "?" : record.rejection().name().toLowerCase(Locale.ROOT)),
                        record.applied()));
            }
        }
        boolean money = filter.equals("money");
        if (filter.isEmpty() || filter.equals("players") || money) {
            for (PlayerActionLog.Entry entry : PlayerActionLog.get(request.server()).recent(null, LIMIT)) {
                if (money && !entry.action().startsWith("money_")) {
                    continue;
                }
                events.add(new Event(entry.timeMillis(), "player", entry.adminName(), entry.action() + " " + entry.playerName()
                        + (entry.argument().isEmpty() ? "" : " (" + entry.argument() + ")")
                        + (entry.detail().isEmpty() ? "" : " · " + entry.detail()), entry.result(), entry.ok()));
            }
        }
        if (filter.isEmpty() || filter.equals("operations") || money) {
            for (AdminAuditLog.Entry entry : AdminAuditLog.get(request.server()).recent(LIMIT)) {
                if (money && !entry.section().equals(AdminCategory.ECONOMY.id())) {
                    continue;
                }
                events.add(new Event(entry.timeMillis(), entry.section(), entry.adminName(), entry.operation()
                        + (entry.subject().isEmpty() ? "" : " " + entry.subject())
                        + (entry.argument().isEmpty() ? "" : " (" + entry.argument() + ")")
                        + (entry.detail().isEmpty() ? "" : " · " + entry.detail()), entry.result(), entry.ok()));
            }
        }
        events.sort(Comparator.comparingLong(Event::time).reversed());
        out.section("timeline");
        if (events.isEmpty()) {
            out.note("empty");
        }
        for (Event event : events.subList(0, Math.min(LIMIT, events.size()))) {
            out.literal(TIME.format(Instant.ofEpochMilli(event.time())) + " " + event.who() + " · " + event.source(),
                    event.what() + " · " + event.result(),
                    event.ok() ? Tone.NORMAL : event.result().startsWith("failed") ? Tone.BAD : Tone.WARN);
        }
    }
}
