package at.koopro.wizardsandbeasts.client.admin;

import at.koopro.wizardsandbeasts.network.admin.AdminProfilePayloads;
import net.minecraft.util.Util;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The Profiles section as last answered by the server: the library, importable files, recent history, the last
 * preview asked for and the last action's outcome. Client thread only; nothing here is a value the client decides.
 */
@NullMarked
public final class ClientAdminProfileState {

    private static AdminProfilePayloads.@Nullable Listing listing;
    private static boolean stale = true;
    private static AdminProfilePayloads.@Nullable PreviewReply preview;
    private static AdminProfilePayloads.@Nullable ActionReply lastAction;
    private static long lastActionAt;
    private static int version;

    private ClientAdminProfileState() {}

    static void accept(AdminProfilePayloads.ListReply reply) {
        listing = reply.listing();
        stale = false;
        version++;
    }

    static void accept(AdminProfilePayloads.PreviewReply reply) {
        preview = reply;
        version++;
    }

    static void accept(AdminProfilePayloads.ActionReply reply) {
        lastAction = reply;
        lastActionAt = Util.getMillis();
        // A preview is a picture of "now"; after any action it may be wrong, so it is asked for again when needed.
        preview = null;
        version++;
    }

    public static List<AdminProfilePayloads.ProfileRow> profiles() {
        return listing == null ? List.of() : listing.profiles();
    }

    public static AdminProfilePayloads.@Nullable ProfileRow profile(String id) {
        for (AdminProfilePayloads.ProfileRow row : profiles()) {
            if (row.id().equals(id)) {
                return row;
            }
        }
        return null;
    }

    public static List<String> files() {
        return listing == null ? List.of() : listing.files();
    }

    public static List<AdminProfilePayloads.HistoryRow> history() {
        return listing == null ? List.of() : listing.history();
    }

    public static String folder() {
        return listing == null ? "" : listing.folder();
    }

    public static boolean loaded() {
        return listing != null;
    }

    public static boolean stale() {
        return stale;
    }

    static void markRequested() {
        stale = false;
    }

    /** Settings changed through another door: the history shown is out of date. */
    static void markStale() {
        stale = true;
    }

    /** The preview for {@code profileId}, if that is the one last answered. */
    public static AdminProfilePayloads.@Nullable PreviewReply preview(String profileId) {
        return preview != null && preview.profileId().equals(profileId) ? preview : null;
    }

    public static AdminProfilePayloads.@Nullable ActionReply lastAction() {
        return lastAction;
    }

    /** Bumped on every answer, so a page knows to lay itself out again. */
    public static int version() {
        return version;
    }

    public static long lastActionAt() {
        return lastActionAt;
    }

    static void clear() {
        listing = null;
        stale = true;
        preview = null;
        lastAction = null;
    }
}
