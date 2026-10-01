package at.koopro.wizardsandbeasts.client.admin;

import at.koopro.wizardsandbeasts.admin.heritage.HeritageAdminService;
import at.koopro.wizardsandbeasts.network.admin.AdminHeritagePayloads;
import net.minecraft.util.Util;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The Heritages section's player-tool state, as last answered by the server. Kept apart from
 * {@link ClientAdminState} (which holds settings): nothing here is a value the client could edit — it is what the
 * server read off a player when asked. Client thread only.
 */
@NullMarked
public final class ClientAdminHeritageState {

    private static List<HeritageAdminService.PlayerRow> players = List.of();
    private static HeritageAdminService.@Nullable Inspection inspection;
    private static AdminHeritagePayloads.@Nullable ActionReply lastAction;
    private static long lastActionAt;

    private static boolean received;

    private ClientAdminHeritageState() {}

    static void accept(AdminHeritagePayloads.PlayersReply reply) {
        players = reply.players();
        received = true;
        // A player who left takes their inspection with them.
        if (inspection != null && players.stream().noneMatch(row -> row.id().equals(inspection.id()))) {
            inspection = null;
        }
    }

    static void accept(AdminHeritagePayloads.InspectReply reply) {
        inspection = reply.inspection();
    }

    static void accept(AdminHeritagePayloads.ActionReply reply) {
        lastAction = reply;
        lastActionAt = Util.getMillis();
    }

    public static List<HeritageAdminService.PlayerRow> players() {
        return players;
    }

    public static HeritageAdminService.@Nullable Inspection inspection() {
        return inspection;
    }

    public static boolean inspecting(UUID id) {
        return inspection != null && inspection.id().equals(id);
    }

    public static AdminHeritagePayloads.@Nullable ActionReply lastAction() {
        return lastAction;
    }

    public static long lastActionAt() {
        return lastActionAt;
    }

    /** Whether the server has answered with the online-player list since joining. */
    public static boolean received() {
        return received;
    }

    static void clear() {
        received = false;
        players = List.of();
        inspection = null;
        lastAction = null;
    }
}
