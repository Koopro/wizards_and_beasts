package at.koopro.wizardsandbeasts.client.admin;

import at.koopro.wizardsandbeasts.admin.creature.CreatureAdminService;
import at.koopro.wizardsandbeasts.network.admin.AdminCreaturePayloads;
import net.minecraft.util.Util;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The Creatures section's state, as last answered by the server: the roster rows, the open creature's page, the
 * last action's outcome. Rule values are not here — they are settings in {@link ClientAdminState}. Client thread only.
 */
@NullMarked
public final class ClientAdminCreatureState {

    private static List<AdminCreaturePayloads.CreatureSummary> creatures = List.of();
    private static boolean listStale = true;
    private static CreatureAdminService.@Nullable Detail detail;
    private static AdminCreaturePayloads.@Nullable ActionReply lastAction;
    private static long lastActionAt;

    private static boolean received;
    private ClientAdminCreatureState() {}

    /** Whether the server has answered with a list at least once since joining — an empty list is then really empty. */
    public static boolean received() {
        return received;
    }

    static void accept(AdminCreaturePayloads.ListReply reply) {
        creatures = reply.creatures();
        received = true;
        listStale = false;
    }

    static void accept(AdminCreaturePayloads.DetailReply reply) {
        detail = reply.detail();
    }

    static void accept(AdminCreaturePayloads.ActionReply reply) {
        lastAction = reply;
        lastActionAt = Util.getMillis();
    }

    public static List<AdminCreaturePayloads.CreatureSummary> creatures() {
        return creatures;
    }

    public static boolean listStale() {
        return listStale;
    }

    static void markListRequested() {
        listStale = false;
    }

    /** A rule changed somewhere: the rows summarise rules, so ask again next time the list is shown. */
    public static void markStale() {
        listStale = true;
    }

    public static CreatureAdminService.@Nullable Detail detail() {
        return detail;
    }

    public static AdminCreaturePayloads.@Nullable ActionReply lastAction() {
        return lastAction;
    }

    public static long lastActionAt() {
        return lastActionAt;
    }

    static void clear() {
        creatures = List.of();
        received = false;
        listStale = true;
        detail = null;
        lastAction = null;
    }
}
