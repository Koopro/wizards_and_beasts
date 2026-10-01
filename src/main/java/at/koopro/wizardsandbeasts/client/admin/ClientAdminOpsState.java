package at.koopro.wizardsandbeasts.client.admin;

import at.koopro.wizardsandbeasts.admin.debug.DebugLeases;
import at.koopro.wizardsandbeasts.admin.debug.LiveDiagnostics;
import at.koopro.wizardsandbeasts.admin.perf.PerformanceMetrics;
import at.koopro.wizardsandbeasts.network.admin.AdminOpsPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The Performance and Debug sections as last answered by the server, plus the two things measured on this client:
 * frame rate and particle count, sampled at most once a second (never per frame). Also owns this client's side of
 * the debug leases: the hitbox overlay (a vanilla debug entry that vanilla saves to disk, so it is put back exactly as
 * it was) and the release sent when the Control Center closes.
 */
@NullMarked
public final class ClientAdminOpsState {

    /** Lease heartbeat while the Control Center is open and a tool may be held. */
    private static final int RENEW_TICKS = 60 * 20;
    /** Client sampling interval. */
    private static final int SAMPLE_TICKS = 20;

    private static AdminOpsPayloads.@Nullable MetricsReply metrics;
    private static AdminOpsPayloads.@Nullable DiagnosticsReply diagnostics;
    private static AdminOpsPayloads.@Nullable PresetReply lastPreset;
    private static AdminOpsPayloads.@Nullable ToolReply lastTool;
    private static int version;
    /** True from the first tool switched on until the release is sent. */
    private static boolean leaseMaybeHeld;
    private static int ticksSinceRenew;
    private static @Nullable DebugScreenEntryStatus hitboxesBefore;

    private static int fps = -1;
    private static int particles = -1;
    private static int clientEntities = -1;
    private static int ticksSinceSample = SAMPLE_TICKS;

    private ClientAdminOpsState() {}

    static void accept(AdminOpsPayloads.MetricsReply reply) {
        metrics = reply;
        version++;
    }

    static void accept(AdminOpsPayloads.DiagnosticsReply reply) {
        diagnostics = reply;
        if (!reply.state().held().isEmpty()) {
            leaseMaybeHeld = true;
        }
        version++;
    }

    static void accept(AdminOpsPayloads.PresetReply reply) {
        lastPreset = reply;
        version++;
    }

    static void accept(AdminOpsPayloads.ToolReply reply) {
        lastTool = reply;
        version++;
    }

    public static PerformanceMetrics.@Nullable Snapshot metrics() {
        return metrics == null ? null : metrics.metrics();
    }

    /** {@code low|medium|high|custom}, or empty before the first answer. */
    public static String preset() {
        return metrics == null ? "" : metrics.preset();
    }

    public static LiveDiagnostics.@Nullable Snapshot diagnostics() {
        return diagnostics == null ? null : diagnostics.diagnostics();
    }

    public static DebugLeases.@Nullable State debugState() {
        return diagnostics == null ? null : diagnostics.state();
    }

    public static AdminOpsPayloads.@Nullable PresetReply lastPreset() {
        return lastPreset;
    }

    public static AdminOpsPayloads.@Nullable ToolReply lastTool() {
        return lastTool;
    }

    public static int version() {
        return version;
    }

    // ── this client's own measurements ──

    /** Frames per second as Minecraft counts them, or -1 before the first sample. */
    public static int fps() {
        return fps;
    }

    /** Live particles in this client's particle engine, or -1 before the first sample. */
    public static int particles() {
        return particles;
    }

    public static int clientEntities() {
        return clientEntities;
    }

    // ── requests ──

    public static void requestMetrics() {
        ClientPacketDistributor.sendToServer(AdminOpsPayloads.MetricsRequest.INSTANCE);
    }

    public static void requestDiagnostics() {
        ClientPacketDistributor.sendToServer(AdminOpsPayloads.DiagnosticsRequest.INSTANCE);
    }

    /** Sent only after the administrator confirmed in a dialog. */
    public static void applyPreset(at.koopro.wizardsandbeasts.admin.perf.PerformancePresets.Preset preset) {
        ClientPacketDistributor.sendToServer(new AdminOpsPayloads.PresetRequest(preset, true));
    }

    public static void setTool(DebugLeases.Tool tool, String value) {
        leaseMaybeHeld = true;
        ClientPacketDistributor.sendToServer(new AdminOpsPayloads.ToggleRequest(tool, value));
    }

    // ── hitboxes: this client only, put back as they were ──

    public static boolean hitboxes() {
        return Minecraft.getInstance().debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES);
    }

    public static void setHitboxes(boolean on) {
        Minecraft minecraft = Minecraft.getInstance();
        if (hitboxesBefore == null) {
            hitboxesBefore = minecraft.debugEntries.getStatus(DebugScreenEntries.ENTITY_HITBOXES);
        }
        minecraft.debugEntries.setStatus(DebugScreenEntries.ENTITY_HITBOXES,
                on ? DebugScreenEntryStatus.ALWAYS_ON : DebugScreenEntryStatus.NEVER);
        version++;
    }

    /** Whether the panel changed the hitbox overlay and has not put it back yet. */
    public static boolean hitboxesChanged() {
        return hitboxesBefore != null;
    }

    private static void restoreHitboxes() {
        if (hitboxesBefore != null) {
            Minecraft.getInstance().debugEntries.setStatus(DebugScreenEntries.ENTITY_HITBOXES, hitboxesBefore);
            hitboxesBefore = null;
        }
    }

    // ── lifecycle ──

    /** Every client tick while the Control Center is open: sampling and the lease heartbeat. */
    public static void tickOpen() {
        if (++ticksSinceSample >= SAMPLE_TICKS) {
            ticksSinceSample = 0;
            Minecraft minecraft = Minecraft.getInstance();
            fps = minecraft.getFps();
            try {
                particles = Integer.parseInt(minecraft.particleEngine.countParticles());
            } catch (NumberFormatException e) {
                particles = -1;
            }
            clientEntities = minecraft.level == null ? -1 : minecraft.level.getEntityCount();
        }
        if (leaseMaybeHeld && ++ticksSinceRenew >= RENEW_TICKS) {
            ticksSinceRenew = 0;
            ClientPacketDistributor.sendToServer(new AdminOpsPayloads.LeaseRequest(false));
        }
    }

    /** "Release all now": the same release closing the Control Center sends, without closing it. */
    public static void releaseNow() {
        restoreHitboxes();
        ClientPacketDistributor.sendToServer(new AdminOpsPayloads.LeaseRequest(true));
        leaseMaybeHeld = false;
        requestDiagnostics();
    }

    /**
     * The Control Center closed (any way): put the hitbox overlay back and tell the server to release every debug tool
     * this administrator switched on.
     */
    public static void onControlCenterClosed() {
        restoreHitboxes();
        if (leaseMaybeHeld && Minecraft.getInstance().getConnection() != null) {
            ClientPacketDistributor.sendToServer(new AdminOpsPayloads.LeaseRequest(true));
        }
        leaseMaybeHeld = false;
        ticksSinceRenew = 0;
    }

    static void clear() {
        restoreHitboxes();
        metrics = null;
        diagnostics = null;
        lastPreset = null;
        lastTool = null;
        leaseMaybeHeld = false;
    }
}
