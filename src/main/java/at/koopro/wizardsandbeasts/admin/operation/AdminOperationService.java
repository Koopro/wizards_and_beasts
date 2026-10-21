package at.koopro.wizardsandbeasts.admin.operation;

import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.operation.AdminOperation.Outcome;
import at.koopro.wizardsandbeasts.admin.operation.AdminOperation.Request;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

/**
 * The one way to run an {@link AdminOperation}, for the Control Center's payload and {@code /wandb admin op} alike.
 *
 * <p>Pipeline: administrator at all (before the id is looked up, so ids cannot be probed) → operation exists →
 * capability → bounded input → {@link AdminOperation#validate} → a destructive one is confirmed → execute (an
 * exception is a {@code failed} outcome, never a crash) → {@link AdminAuditLog} entry + INFO log. Attempts without
 * authority go to the server log only.
 */
@NullMarked
public final class AdminOperationService {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int MAX_SUBJECT = 64;
    public static final int MAX_ARGUMENT = 64;

    private AdminOperationService() {}

    public static Outcome perform(AdminContext actor, MinecraftServer server, String operationId, String subject,
                                  String argument, boolean confirmed) {
        if (!actor.canRead()) {
            LOGGER.warn("[Admin] Refused operation {} from non-administrator {}", operationId, actor.actorName());
            return Outcome.refused("unauthorized", operationId);
        }
        AdminOperation operation = AdminOperations.validId(operationId) ? AdminOperations.byId(operationId) : null;
        if (operation == null) {
            return Outcome.refused("unknown", operationId);
        }
        if (!actor.canModify(operation.capability())) {
            LOGGER.warn("[Admin] Refused operation {} on {} from unauthorised {}", operationId, subject, actor.actorName());
            return Outcome.refused("unauthorized", operationId);
        }
        AdminAuditLog log = AdminAuditLog.get(server);
        if (subject.length() > MAX_SUBJECT || argument.length() > MAX_ARGUMENT) {
            return audit(log, actor, operation, subject, argument, Outcome.refused("invalid_argument", "too long"));
        }
        Request request = new Request(actor, server, subject.trim(), argument.trim(), confirmed);
        String problem;
        try {
            problem = operation.validate(request);
        } catch (RuntimeException e) {
            problem = e.getClass().getSimpleName();
        }
        if (problem != null) {
            return audit(log, actor, operation, subject, argument, Outcome.refused("invalid_argument", problem));
        }
        if (operation.destructive() && !confirmed) {
            return audit(log, actor, operation, subject, argument, Outcome.refused("confirm_required", subject));
        }
        Outcome outcome;
        try {
            outcome = operation.execute(request);
        } catch (RuntimeException e) {
            LOGGER.error("[Admin] Operation {} on {} failed", operationId, subject, e);
            outcome = Outcome.refused("failed", e.getClass().getSimpleName());
        }
        if (outcome.success()) {
            LOGGER.info("[Admin] {} ran {} on {} ({}): {}", actor.actorName(), operationId, subject, argument,
                    outcome.detail());
        }
        return audit(log, actor, operation, subject, argument, outcome);
    }

    private static Outcome audit(AdminAuditLog log, AdminContext actor, AdminOperation operation, String subject,
                                 String argument, Outcome outcome) {
        String result = outcome.success() ? "ok"
                : outcome.code().equals("failed") ? "failed:" + outcome.detail() : "refused:" + outcome.code();
        AdminAuditLog.Entry entry = log.append(actor.actorId(), actor.actorName(), operation.section().id(),
                operation.id(), subject, argument, result, outcome.detail());
        return outcome.withSequence(entry.sequence());
    }

    /**
     * Records a one-shot admin action that runs through its own service (a market reset, a preset delete) so it
     * shows in the same audit timeline. Call only after the caller checked authority.
     */
    public static void record(MinecraftServer server, AdminContext actor, String section, String action, String subject,
                              String argument, boolean ok, String detail) {
        AdminAuditLog.get(server).append(actor.actorId(), actor.actorName(), section, action, subject, argument,
                ok ? "ok" : "refused", detail);
    }
}
