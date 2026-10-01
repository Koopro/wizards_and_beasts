package at.koopro.wizardsandbeasts.client.admin;

import at.koopro.wizardsandbeasts.admin.brew.BrewAdminService;
import at.koopro.wizardsandbeasts.network.admin.AdminBrewPayloads;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The Brewing section's state, as last answered by the server: the brew rows and the open brew's page. The page's
 * editable values live in {@link ClientAdminState} as setting descriptors. Client thread only.
 */
@NullMarked
public final class ClientAdminBrewState {

    private static List<AdminBrewPayloads.BrewSummary> brews = List.of();
    private static boolean listStale = true;
    private static BrewAdminService.@Nullable Detail detail;

    private static boolean received;
    private ClientAdminBrewState() {}

    /** Whether the server has answered with a list at least once since joining — an empty list is then really empty. */
    public static boolean received() {
        return received;
    }

    static void accept(AdminBrewPayloads.ListReply reply) {
        brews = reply.brews();
        received = true;
        listStale = false;
    }

    static void accept(AdminBrewPayloads.DetailReply reply) {
        detail = reply.detail();
        ClientAdminState.acceptBrewSettings(reply.detail().settings());
    }

    public static List<AdminBrewPayloads.BrewSummary> brews() {
        return brews;
    }

    public static boolean listStale() {
        return listStale;
    }

    static void markListRequested() {
        listStale = false;
    }

    /** A brew value changed: rows and page summarise them, so ask again. */
    static void markStale() {
        listStale = true;
    }

    public static BrewAdminService.@Nullable Detail detail() {
        return detail;
    }

    static void clear() {
        brews = List.of();
        received = false;
        listStale = true;
        detail = null;
    }
}
