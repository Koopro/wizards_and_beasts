package at.koopro.wizardsandbeasts.client.admin;

import at.koopro.wizardsandbeasts.network.admin.AdminReportPayloads;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Admin reports as last answered by the server, keyed by report and subject, and the last operation's outcome. A
 * mirror only: pages draw from it and ask again; nothing here is ever sent back.
 */
@NullMarked
public final class ClientAdminReportState {

    private static final Map<String, AdminReportPayloads.ReportReply> REPORTS = new HashMap<>();
    private static AdminReportPayloads.@Nullable OperationReply lastOperation;
    private static int version;

    private ClientAdminReportState() {}

    private static String key(String reportId, String subject) {
        return reportId + "|" + subject;
    }

    static void accept(AdminReportPayloads.ReportReply reply) {
        REPORTS.put(key(reply.reportId(), reply.subject()), reply);
        version++;
    }

    static void accept(AdminReportPayloads.OperationReply reply) {
        lastOperation = reply;
        version++;
    }

    public static AdminReportPayloads.@Nullable ReportReply report(String reportId, String subject) {
        return REPORTS.get(key(reportId, subject));
    }

    public static AdminReportPayloads.@Nullable OperationReply lastOperation() {
        return lastOperation;
    }

    /** Moves whenever an answer arrives; a page re-lays itself out when it changed. */
    public static int version() {
        return version;
    }

    public static void request(String reportId, String subject) {
        ClientPacketDistributor.sendToServer(new AdminReportPayloads.ReportRequest(reportId, subject));
    }

    public static void perform(String operationId, String subject, String argument, boolean confirmed) {
        ClientPacketDistributor.sendToServer(new AdminReportPayloads.OperationRequest(operationId, subject, argument,
                confirmed));
    }

    static void clear() {
        REPORTS.clear();
        lastOperation = null;
        version++;
    }
}
