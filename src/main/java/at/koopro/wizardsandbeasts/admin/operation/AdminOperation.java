package at.koopro.wizardsandbeasts.admin.operation;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * One administrative action outside the settings and the player pipeline — something done once to the world (scale
 * a creature, discard it) rather than a value kept. The Control Center's buttons and {@code /wandb admin op} both run
 * it through {@link AdminOperationService#perform}, which checks authority, validation and confirmation and writes
 * the {@link AdminAuditLog}; the operation itself only validates its input and acts through the owning system.
 *
 * <p>To add one: implement this, register it in {@link BuiltinOperations}, add
 * {@code admin.wizards_and_beasts.operation.<id>} (+ {@code .warning} for a destructive one), and offer it from a
 * report with {@code ReportBuilder.action}. Nothing in the network, command or client code changes.
 */
@NullMarked
public interface AdminOperation {

    /** Stable id: wire, command argument, audit entry. {@code [a-z0-9_]}. */
    String id();

    AdminCategory section();

    default AdminCapability capability() {
        return section().defaultCapability();
    }

    /** Whether it cannot be undone and so must be confirmed. The server enforces this; the page only asks first. */
    boolean destructive();

    /** Null when {@code request} is well-formed and its subject exists; otherwise what is wrong, in a few words. */
    @Nullable String validate(Request request);

    /** Acts. Only called after authority, {@link #validate} and confirmation passed. */
    Outcome execute(Request request);

    /**
     * One attempt.
     *
     * @param subject  what it acts on (an entity UUID, a player name …)
     * @param argument its one parameter ({@code ""} for none)
     */
    record Request(AdminContext actor, MinecraftServer server, String subject, String argument, boolean confirmed) {}

    /**
     * What happened. {@code code}: {@code ok} or why not ({@code unauthorized}, {@code unknown},
     * {@code invalid_argument}, {@code confirm_required}, {@code failed}, or one of the operation's own);
     * {@code detail}: what changed or why, in a few words.
     */
    record Outcome(boolean success, String code, String detail, long auditSequence) {
        public static Outcome ok(String detail) {
            return new Outcome(true, "ok", detail, 0);
        }

        public static Outcome refused(String code, String detail) {
            return new Outcome(false, code, detail, 0);
        }

        Outcome withSequence(long sequence) {
            return new Outcome(success, code, detail, sequence);
        }
    }
}
