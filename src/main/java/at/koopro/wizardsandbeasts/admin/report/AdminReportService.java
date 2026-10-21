package at.koopro.wizardsandbeasts.admin.report;

import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

import java.util.Locale;

/**
 * The one way to read an {@link AdminReport}, for the Control Center's request payload and the command alike.
 *
 * <p>Order: is the actor an administrator at all (before the id is looked up, so an outsider cannot probe which
 * reports exist) → does the report exist → does the actor hold its capability → build. A report that throws is a
 * {@code FAILED} answer and a server log line, never a crash.
 */
@NullMarked
public final class AdminReportService {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** Longest subject accepted; anything longer is not a name, UUID or id any report takes. */
    public static final int MAX_SUBJECT = 64;

    private AdminReportService() {}

    public enum Status {
        OK, UNAUTHORIZED, UNKNOWN, FAILED;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public record Result(Status status, String reportId, ReportView view) {
        public boolean ok() {
            return status == Status.OK;
        }
    }

    public static Result read(AdminContext actor, MinecraftServer server, String reportId, String subject) {
        if (!actor.canRead()) {
            return new Result(Status.UNAUTHORIZED, reportId, ReportView.EMPTY);
        }
        AdminReport report = AdminReports.validId(reportId) ? AdminReports.byId(reportId) : null;
        if (report == null) {
            return new Result(Status.UNKNOWN, reportId, ReportView.EMPTY);
        }
        if (!actor.canModify(report.capability())) {
            return new Result(Status.UNAUTHORIZED, reportId, ReportView.EMPTY);
        }
        String cleanSubject = subject.length() > MAX_SUBJECT ? subject.substring(0, MAX_SUBJECT) : subject.trim();
        ReportBuilder out = new ReportBuilder(report.id()).subject(cleanSubject);
        try {
            report.build(new AdminReport.Request(actor, server, cleanSubject), out);
            return new Result(Status.OK, reportId, out.build());
        } catch (RuntimeException e) {
            LOGGER.error("[Admin] Report {} failed for {}", reportId, actor.actorName(), e);
            return new Result(Status.FAILED, reportId, ReportView.EMPTY);
        }
    }
}
