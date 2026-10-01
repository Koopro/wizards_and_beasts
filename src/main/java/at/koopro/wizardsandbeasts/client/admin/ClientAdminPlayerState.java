package at.koopro.wizardsandbeasts.client.admin;

import at.koopro.wizardsandbeasts.admin.player.PlayerActionLog;
import at.koopro.wizardsandbeasts.admin.player.PlayerAdminService.Facet;
import at.koopro.wizardsandbeasts.admin.player.PlayerAdminService.FacetView;
import at.koopro.wizardsandbeasts.admin.player.PlayerAdminService.PlayerRow;
import at.koopro.wizardsandbeasts.network.admin.AdminPlayerPayloads;
import net.minecraft.util.Util;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Players section as last answered by the server: the online list, the facets read for the selected player, that
 * player's action log and the last action's outcome. A view of server answers only: the client holds no player
 * data of its own and decides nothing. Client thread only.
 */
@NullMarked
public final class ClientAdminPlayerState {

    private static List<PlayerRow> rows = List.of();
    private static boolean loaded;
    private static boolean stale = true;
    private static boolean maySeeLocation;
    private static boolean mayChangeMoney;
    private static final Map<Facet, AdminPlayerPayloads.FacetReply> FACETS = new EnumMap<>(Facet.class);
    private static @Nullable UUID facetsFor;
    private static AdminPlayerPayloads.@Nullable LogReply log;
    private static AdminPlayerPayloads.@Nullable ActionReply lastAction;
    private static long lastActionAt;
    private static int version;

    private ClientAdminPlayerState() {}

    static void accept(AdminPlayerPayloads.SearchReply reply) {
        rows = reply.rows();
        maySeeLocation = reply.maySeeLocation();
        mayChangeMoney = reply.mayChangeMoney();
        loaded = true;
        stale = false;
        version++;
    }

    static void accept(AdminPlayerPayloads.FacetReply reply) {
        if (!reply.view().player().equals(facetsFor)) {
            FACETS.clear();
            facetsFor = reply.view().player();
        }
        FACETS.put(reply.view().facet(), reply);
        version++;
    }

    static void accept(AdminPlayerPayloads.LogReply reply) {
        log = reply;
        version++;
    }

    static void accept(AdminPlayerPayloads.ActionReply reply) {
        lastAction = reply;
        lastActionAt = Util.getMillis();
        // Whatever the action touched, every facet read before it may now be wrong.
        FACETS.clear();
        log = null;
        stale = true;
        version++;
    }

    public static List<PlayerRow> rows() {
        return rows;
    }

    public static @Nullable PlayerRow row(UUID id) {
        for (PlayerRow row : rows) {
            if (row.id().equals(id)) {
                return row;
            }
        }
        return null;
    }

    public static boolean loaded() {
        return loaded;
    }

    public static boolean stale() {
        return stale;
    }

    static void markRequested() {
        stale = false;
    }

    public static boolean maySeeLocation() {
        return maySeeLocation;
    }

    public static boolean mayChangeMoney() {
        return mayChangeMoney;
    }

    /** The facet last read for {@code player}, or null when it has not been read (or an action made it stale). */
    public static AdminPlayerPayloads.@Nullable FacetReply facet(UUID player, Facet facet) {
        return player.equals(facetsFor) ? FACETS.get(facet) : null;
    }

    public static @Nullable FacetView view(UUID player, Facet facet) {
        AdminPlayerPayloads.FacetReply reply = facet(player, facet);
        return reply == null ? null : reply.view();
    }

    public static AdminPlayerPayloads.@Nullable LogReply log(UUID player) {
        return log != null && log.player().equals(player) ? log : null;
    }

    public static List<PlayerActionLog.Entry> logEntries(UUID player) {
        AdminPlayerPayloads.LogReply reply = log(player);
        return reply == null ? List.of() : reply.entries();
    }

    public static AdminPlayerPayloads.@Nullable ActionReply lastAction() {
        return lastAction;
    }

    public static long lastActionAt() {
        return lastActionAt;
    }

    /** Bumped on every answer, so a page knows to lay itself out again. */
    public static int version() {
        return version;
    }

    static void clear() {
        rows = List.of();
        loaded = false;
        stale = true;
        FACETS.clear();
        facetsFor = null;
        log = null;
        lastAction = null;
    }
}
