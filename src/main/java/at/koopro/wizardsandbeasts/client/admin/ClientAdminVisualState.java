package at.koopro.wizardsandbeasts.client.admin;

import at.koopro.wizardsandbeasts.admin.visual.BeamVisualAdminService.BeamSummary;
import at.koopro.wizardsandbeasts.network.admin.AdminVisualPayloads;
import at.koopro.wizardsandbeasts.visual.beam.BeamPreset;
import net.minecraft.util.Util;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The Visuals section's beam page as last answered by the server: the beam spells, the preset library and the last
 * preset operation's outcome. Each beam's settings live in {@link ClientAdminState} as page settings. Client thread
 * only.
 */
@NullMarked
public final class ClientAdminVisualState {

    public static final String PAGE = "beams";

    private static List<BeamSummary> beams = List.of();
    private static List<BeamPreset> presets = List.of();
    private static boolean stale = true;
    private static AdminVisualPayloads.@Nullable ActionReply lastAction;
    private static long lastActionAt;

    private static boolean received;
    private ClientAdminVisualState() {}

    /** Whether the server has answered with a list at least once since joining — an empty list is then really empty. */
    public static boolean received() {
        return received;
    }

    static void accept(AdminVisualPayloads.ListReply reply) {
        beams = reply.listing().beams();
        received = true;
        presets = reply.listing().presets();
        stale = false;
        ClientAdminState.acceptPageSettings(PAGE, reply.listing().settings());
    }

    static void accept(AdminVisualPayloads.ActionReply reply) {
        lastAction = reply;
        lastActionAt = Util.getMillis();
    }

    public static List<BeamSummary> beams() {
        return beams;
    }

    public static List<BeamPreset> presets() {
        return presets;
    }

    public static @Nullable BeamPreset preset(String id) {
        for (BeamPreset preset : presets) {
            if (preset.id().equals(id)) {
                return preset;
            }
        }
        return null;
    }

    public static boolean stale() {
        return stale;
    }

    static void markRequested() {
        stale = false;
    }

    /** A beam value changed: rows show effective looks and preset labels, so ask again. */
    static void markStale() {
        stale = true;
    }

    public static AdminVisualPayloads.@Nullable ActionReply lastAction() {
        return lastAction;
    }

    public static long lastActionAt() {
        return lastActionAt;
    }

    static void clear() {
        beams = List.of();
        received = false;
        presets = List.of();
        stale = true;
        lastAction = null;
    }
}
