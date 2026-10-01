package at.koopro.wizardsandbeasts.client.admin;

import at.koopro.wizardsandbeasts.network.admin.AdminBroomPayloads;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * The Travel section's broom roster, as last answered by the server. Each broom's settings live in
 * {@link ClientAdminState} as setting descriptors. Client thread only.
 */
@NullMarked
public final class ClientAdminBroomState {

    public static final String PAGE = "brooms";

    private static List<AdminBroomPayloads.BroomSummary> brooms = List.of();
    private static boolean stale = true;

    private static boolean received;
    private ClientAdminBroomState() {}

    /** Whether the server has answered with a list at least once since joining — an empty list is then really empty. */
    public static boolean received() {
        return received;
    }

    static void accept(AdminBroomPayloads.ListReply reply) {
        brooms = reply.listing().brooms();
        received = true;
        stale = false;
        ClientAdminState.acceptPageSettings(PAGE, reply.listing().settings());
    }

    public static List<AdminBroomPayloads.BroomSummary> brooms() {
        return brooms;
    }

    public static boolean stale() {
        return stale;
    }

    static void markRequested() {
        stale = false;
    }

    /** A broom value changed: rows show the effective stats, so ask again. */
    static void markStale() {
        stale = true;
    }

    static void clear() {
        brooms = List.of();
        received = false;
        stale = true;
    }
}
