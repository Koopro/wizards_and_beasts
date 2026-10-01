package at.koopro.wizardsandbeasts.client.admin;

import at.koopro.wizardsandbeasts.admin.wand.WandAdminService;
import at.koopro.wizardsandbeasts.network.admin.AdminWandPayloads;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The Wands section's state, as last answered by the server: the catalog, the generator's last preview, and the last
 * test-wand answer. The withdrawal switches live in {@link ClientAdminState} as setting descriptors. Client thread only.
 */
@NullMarked
public final class ClientAdminWandState {

    public static final String PAGE = "wands";

    private static WandAdminService.@Nullable Catalog catalog;
    private static boolean stale = true;
    private static AdminWandPayloads.@Nullable PreviewReply preview;
    private static AdminWandPayloads.@Nullable ActionReply lastAction;
    private static long lastActionAt;

    private ClientAdminWandState() {}

    static void accept(AdminWandPayloads.CatalogReply reply) {
        catalog = reply.catalog();
        stale = false;
        ClientAdminState.acceptPageSettings(PAGE, reply.catalog().settings());
    }

    static void accept(AdminWandPayloads.PreviewReply reply) {
        preview = reply;
    }

    static void accept(AdminWandPayloads.ActionReply reply) {
        lastAction = reply;
        lastActionAt = System.currentTimeMillis();
    }

    public static WandAdminService.@Nullable Catalog catalog() {
        return catalog;
    }

    public static boolean stale() {
        return stale;
    }

    static void markRequested() {
        stale = false;
    }

    /** A wand rule changed: the catalog's pairings and the preview's verdict may differ now. */
    static void markStale() {
        stale = true;
        preview = null;
    }

    /** The preview for exactly this request, or null while none has come back for it. */
    public static AdminWandPayloads.@Nullable Preview previewFor(WandAdminService.Request request) {
        AdminWandPayloads.PreviewReply reply = preview;
        return reply != null && reply.request().equals(request) ? reply.preview() : null;
    }

    public static AdminWandPayloads.@Nullable ActionReply lastAction() {
        return lastAction;
    }

    public static long lastActionAt() {
        return lastActionAt;
    }

    static void clear() {
        catalog = null;
        stale = true;
        preview = null;
        lastAction = null;
        lastActionAt = 0L;
    }
}
