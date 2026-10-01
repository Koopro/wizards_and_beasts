package at.koopro.wizardsandbeasts.network.admin;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.AdminSettings;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.debug.CastDiagnostics;
import at.koopro.wizardsandbeasts.admin.debug.DebugLeases;
import at.koopro.wizardsandbeasts.admin.debug.LiveDiagnostics;
import at.koopro.wizardsandbeasts.admin.debug.ModLogLevel;
import at.koopro.wizardsandbeasts.admin.perf.PerformanceMetrics;
import at.koopro.wizardsandbeasts.admin.perf.PerformancePresets;
import at.koopro.wizardsandbeasts.admin.profile.ProfileApplier;
import at.koopro.wizardsandbeasts.client.admin.AdminClientHandlers;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * The Performance and Debug sections on the wire. Metrics and diagnostics are read on request by an open page (at most
 * every {@link #MIN_TICKS_BETWEEN} ticks per player; faster requests are dropped, so a client cannot make the server walk
 * every entity each tick). Presets go through {@link PerformancePresets} and the setting service; debug tools through
 * {@link DebugLeases}, which reverts them when the Control Center closes ({@link LeaseRequest}).
 */
@NullMarked
public final class AdminOpsPayloads {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int MIN_TICKS_BETWEEN = 10;
    private static final int MAX_LIST = 256;
    private static final PlayerScopedState<Long> LAST_PERF = PlayerScopedState.create("admin-perf-requests");
    private static final PlayerScopedState<Long> LAST_DIAG = PlayerScopedState.create("admin-diag-requests");

    private AdminOpsPayloads() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }

    private static <T> void writeList(ByteBuf buf, List<T> list, BiConsumer<ByteBuf, T> writer) {
        int count = Math.min(list.size(), MAX_LIST);
        buf.writeInt(count);
        for (int i = 0; i < count; i++) {
            writer.accept(buf, list.get(i));
        }
    }

    private static <T> List<T> readList(ByteBuf buf, String what, Function<ByteBuf, T> reader) {
        int count = PacketCodecUtils.readBoundedCount(buf, MAX_LIST, what);
        List<T> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(reader.apply(buf));
        }
        return List.copyOf(out);
    }

    private static String clip(String text) {
        return text.length() <= 200 ? text : text.substring(0, 200);
    }

    private static <E extends Enum<E>> E readEnum(ByteBuf buf, E[] values, String what) {
        int ordinal = buf.readByte();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new DecoderException("unknown " + what + " " + ordinal);
        }
        return values[ordinal];
    }

    /** Whether this request may run now; refreshes the stamp when it may. */
    private static boolean due(PlayerScopedState<Long> stamps, ServerPlayer player) {
        long now = player.level().getServer().getTickCount();
        Long last = stamps.get(player);
        if (last != null && now - last < MIN_TICKS_BETWEEN) {
            return false;
        }
        stamps.put(player, now);
        return true;
    }

    // ── authority ──

    public static boolean mayReadPerformance(AdminContext actor) {
        return actor.canRead() && actor.canModify(AdminCategory.PERFORMANCE.defaultCapability());
    }

    // ── server side ──

    public static boolean sendMetrics(ServerPlayer player) {
        AdminContext actor = AdminContext.of(player);
        if (!mayReadPerformance(actor)) {
            LOGGER.warn("[Admin] Dropped metrics request from unauthorised {} ({})", player.getName().getString(),
                    player.getUUID());
            return false;
        }
        if (!due(LAST_PERF, player)) {
            return false;
        }
        PerformancePresets.Preset preset = PerformancePresets.current(AdminSettings.service().registry());
        PacketDistributor.sendToPlayer(player, new MetricsReply(PerformanceMetrics.collect(player.level().getServer()),
                preset == null ? "custom" : preset.id()));
        return true;
    }

    public static ProfileApplier.@org.jspecify.annotations.Nullable Outcome applyPreset(ServerPlayer player, PresetRequest request) {
        AdminContext actor = AdminContext.of(player);
        if (!mayReadPerformance(actor) || !request.confirmed()) {
            PacketDistributor.sendToPlayer(player, new PresetReply(request.preset(), false, 0,
                    request.confirmed() ? "unauthorized" : "confirmation_required"));
            return null;
        }
        // The service checks each setting's own capability again; the applier is all or nothing.
        ProfileApplier.Outcome outcome = PerformancePresets.apply(AdminSettings.service(), actor, request.preset(),
                player.level().getServer());
        PacketDistributor.sendToPlayer(player, new PresetReply(request.preset(), outcome.applied(), outcome.changed(),
                outcome.applied() || outcome.failures().isEmpty() ? ""
                        : outcome.failures().get(0).code() + " " + outcome.failures().get(0).subject()));
        if (outcome.applied() && outcome.changed() > 0) {
            AdminNetworkService.refreshFor(player, (AdminCategory) null);
        }
        return outcome;
    }

    public static boolean sendDiagnostics(ServerPlayer player, boolean force) {
        AdminContext actor = AdminContext.of(player);
        if (!DebugLeases.authorised(actor)) {
            LOGGER.warn("[Admin] Dropped diagnostics request from unauthorised {} ({})", player.getName().getString(),
                    player.getUUID());
            return false;
        }
        if (!force && !due(LAST_DIAG, player)) {
            return false;
        }
        DebugLeases.renew(player.getUUID());
        PacketDistributor.sendToPlayer(player, new DiagnosticsReply(
                LiveDiagnostics.collect(player.level().getServer()), DebugLeases.state(player.getUUID())));
        return true;
    }

    public static DebugLeases.Outcome toggle(ServerPlayer player, ToggleRequest request) {
        DebugLeases.Outcome outcome = DebugLeases.set(AdminContext.of(player), player.level().getServer(),
                request.tool(), request.value());
        PacketDistributor.sendToPlayer(player, new ToolReply(request.tool(), outcome.success(), outcome.code()));
        if (outcome.success()) {
            sendDiagnostics(player, true);
        }
        return outcome;
    }

    // ── codecs for the snapshots ──

    static void writeMetrics(ByteBuf buf, PerformanceMetrics.Snapshot s) {
        buf.writeFloat(s.targetTps());
        buf.writeDouble(s.tps());
        buf.writeDouble(s.msptAvg());
        buf.writeDouble(s.msptMax());
        writeList(buf, s.dimensions(), (b, d) -> {
            PacketCodecUtils.writeString(b, clip(d.id()));
            b.writeInt(d.entities());
            b.writeInt(d.modEntities());
            b.writeInt(d.spellEntities());
            b.writeInt(d.loadedChunks());
            b.writeInt(d.players());
            b.writeDouble(d.msptAvg());
        });
        buf.writeInt(s.players());
        buf.writeInt(s.modEffectsOnPlayers());
        buf.writeInt(s.beams());
        buf.writeInt(s.castSessions());
        buf.writeInt(s.beamScanInterval());
        buf.writeInt(s.beamEffectInterval());
        buf.writeFloat(s.packetsSent());
        buf.writeFloat(s.packetsReceived());
        buf.writeDouble(s.latencyAvgMs());
        buf.writeBoolean(s.networkMeasured());
    }

    static PerformanceMetrics.Snapshot readMetrics(ByteBuf buf) {
        float target = buf.readFloat();
        double tps = buf.readDouble();
        double avg = buf.readDouble();
        double max = buf.readDouble();
        List<PerformanceMetrics.Dimension> dimensions = readList(buf, "admin-perf-dimensions", b ->
                new PerformanceMetrics.Dimension(PacketCodecUtils.readString(b), b.readInt(), b.readInt(), b.readInt(),
                        b.readInt(), b.readInt(), b.readDouble()));
        return new PerformanceMetrics.Snapshot(target, tps, avg, max, dimensions, buf.readInt(), buf.readInt(),
                buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readFloat(), buf.readFloat(),
                buf.readDouble(), buf.readBoolean());
    }

    static void writeDiagnostics(ByteBuf buf, LiveDiagnostics.Snapshot s) {
        writeList(buf, s.beams(), (b, x) -> {
            PacketCodecUtils.writeString(b, clip(x.player()));
            PacketCodecUtils.writeString(b, clip(x.spell()));
            b.writeInt(x.ticks());
            b.writeBoolean(x.hasTarget());
        });
        writeList(buf, s.sessions(), (b, x) -> {
            PacketCodecUtils.writeString(b, clip(x.player()));
            b.writeLong(x.id());
            PacketCodecUtils.writeString(b, clip(x.spell()));
            b.writeLong(x.ageTicks());
            b.writeBoolean(x.releaseConsumed());
            b.writeBoolean(x.vanillaRelease());
            b.writeBoolean(x.clashHold());
        });
        writeList(buf, s.recentCasts(), (b, x) -> {
            b.writeLong(x.timeMillis());
            b.writeLong(x.gameTick());
            PacketCodecUtils.writeString(b, clip(x.player()));
            PacketCodecUtils.writeString(b, clip(x.event()));
            PacketCodecUtils.writeString(b, clip(x.detail()));
        });
        writeList(buf, s.castCounts(), AdminOpsPayloads::writeCount);
        writeList(buf, s.entityTypes(), AdminOpsPayloads::writeCount);
        buf.writeInt(s.modEntities());
        writeList(buf, s.modules(), (b, x) -> {
            PacketCodecUtils.writeString(b, clip(x.module()));
            PacketCodecUtils.writeString(b, clip(x.state()));
        });
        writeList(buf, s.connections(), (b, x) -> {
            PacketCodecUtils.writeString(b, clip(x.player()));
            b.writeInt(x.latencyMs());
            b.writeFloat(x.sentPerSecond());
            b.writeFloat(x.receivedPerSecond());
        });
        writeList(buf, s.holdings(), (b, x) -> {
            PacketCodecUtils.writeString(b, clip(x.admin()));
            PacketCodecUtils.writeString(b, clip(x.tools()));
            b.writeLong(x.idleSeconds());
        });
    }

    static LiveDiagnostics.Snapshot readDiagnostics(ByteBuf buf) {
        List<LiveDiagnostics.Beam> beams = readList(buf, "admin-diag-beams", b -> new LiveDiagnostics.Beam(
                PacketCodecUtils.readString(b), PacketCodecUtils.readString(b), b.readInt(), b.readBoolean()));
        List<LiveDiagnostics.Session> sessions = readList(buf, "admin-diag-sessions", b -> new LiveDiagnostics.Session(
                PacketCodecUtils.readString(b), b.readLong(), PacketCodecUtils.readString(b), b.readLong(), b.readBoolean(),
                b.readBoolean(), b.readBoolean()));
        List<CastDiagnostics.Event> casts = readList(buf, "admin-diag-casts", b -> new CastDiagnostics.Event(
                b.readLong(), b.readLong(), PacketCodecUtils.readString(b), PacketCodecUtils.readString(b),
                PacketCodecUtils.readString(b)));
        List<LiveDiagnostics.Count> counts = readList(buf, "admin-diag-counts", AdminOpsPayloads::readCount);
        List<LiveDiagnostics.Count> types = readList(buf, "admin-diag-types", AdminOpsPayloads::readCount);
        int modEntities = buf.readInt();
        List<LiveDiagnostics.ModuleRow> modules = readList(buf, "admin-diag-modules", b -> new LiveDiagnostics.ModuleRow(
                PacketCodecUtils.readString(b), PacketCodecUtils.readString(b)));
        List<LiveDiagnostics.Connection> connections = readList(buf, "admin-diag-net", b -> new LiveDiagnostics.Connection(
                PacketCodecUtils.readString(b), b.readInt(), b.readFloat(), b.readFloat()));
        List<LiveDiagnostics.Holding> holdings = readList(buf, "admin-diag-holdings", b -> new LiveDiagnostics.Holding(
                PacketCodecUtils.readString(b), PacketCodecUtils.readString(b), b.readLong()));
        return new LiveDiagnostics.Snapshot(beams, sessions, casts, counts, types, modEntities, modules, connections, holdings);
    }

    private static void writeCount(ByteBuf buf, LiveDiagnostics.Count count) {
        PacketCodecUtils.writeString(buf, clip(count.key()));
        buf.writeInt(count.count());
    }

    private static LiveDiagnostics.Count readCount(ByteBuf buf) {
        return new LiveDiagnostics.Count(PacketCodecUtils.readString(buf), buf.readInt());
    }

    static void writeState(ByteBuf buf, DebugLeases.State state) {
        buf.writeBoolean(state.myDebugMode());
        buf.writeBoolean(state.allDebugMode());
        buf.writeBoolean(state.spellLogging());
        buf.writeByte(state.logLevel().ordinal());
        int mask = 0;
        for (DebugLeases.Tool tool : state.held()) {
            mask |= 1 << tool.ordinal();
        }
        buf.writeInt(mask);
    }

    static DebugLeases.State readState(ByteBuf buf) {
        boolean mine = buf.readBoolean();
        boolean all = buf.readBoolean();
        boolean spells = buf.readBoolean();
        ModLogLevel.Choice level = readEnum(buf, ModLogLevel.Choice.values(), "log level");
        int mask = buf.readInt();
        Set<DebugLeases.Tool> held = EnumSet.noneOf(DebugLeases.Tool.class);
        for (DebugLeases.Tool tool : DebugLeases.Tool.values()) {
            if ((mask & (1 << tool.ordinal())) != 0) {
                held.add(tool);
            }
        }
        return new DebugLeases.State(mine, all, spells, level, held);
    }

    // ── payloads ──

    public record MetricsRequest() implements CustomPacketPayload {
        public static final MetricsRequest INSTANCE = new MetricsRequest();
        public static final Type<MetricsRequest> TYPE = new Type<>(id("admin_metrics_request"));
        public static final StreamCodec<ByteBuf, MetricsRequest> STREAM_CODEC = PacketCodecUtils.noPayloadCodec(() -> INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(MetricsRequest payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    sendMetrics(player);
                }
            });
        }
    }

    /** One reading, and which preset the settings match ({@code low|medium|high|custom}). */
    public record MetricsReply(PerformanceMetrics.Snapshot metrics, String preset) implements CustomPacketPayload {
        public static final Type<MetricsReply> TYPE = new Type<>(id("admin_metrics"));
        public static final StreamCodec<ByteBuf, MetricsReply> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    writeMetrics(buf, p.metrics);
                    PacketCodecUtils.writeString(buf, p.preset);
                },
                buf -> new MetricsReply(readMetrics(buf), PacketCodecUtils.readString(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handleClient(MetricsReply payload, IPayloadContext context) {
            context.enqueueWork(() -> AdminClientHandlers.onMetrics(payload));
        }
    }

    /** Applies a preset; sent only after the administrator confirmed in a dialog. */
    public record PresetRequest(PerformancePresets.Preset preset, boolean confirmed) implements CustomPacketPayload {
        public static final Type<PresetRequest> TYPE = new Type<>(id("admin_perf_preset"));
        public static final StreamCodec<ByteBuf, PresetRequest> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeByte(p.preset.ordinal());
                    buf.writeBoolean(p.confirmed);
                },
                buf -> new PresetRequest(readEnum(buf, PerformancePresets.Preset.values(), "preset"), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(PresetRequest payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    applyPreset(player, payload);
                }
            });
        }
    }

    /** A preset's or a debug tool's outcome. {@code detail}: why not, when it did not apply. */
    public record PresetReply(PerformancePresets.Preset preset, boolean applied, int changed, String detail)
            implements CustomPacketPayload {
        public static final Type<PresetReply> TYPE = new Type<>(id("admin_perf_preset_reply"));
        public static final StreamCodec<ByteBuf, PresetReply> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeByte(p.preset.ordinal());
                    buf.writeBoolean(p.applied);
                    buf.writeInt(p.changed);
                    PacketCodecUtils.writeString(buf, clip(p.detail));
                },
                buf -> new PresetReply(readEnum(buf, PerformancePresets.Preset.values(), "preset"), buf.readBoolean(),
                        buf.readInt(), PacketCodecUtils.readString(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handleClient(PresetReply payload, IPayloadContext context) {
            context.enqueueWork(() -> AdminClientHandlers.onPresetReply(payload));
        }
    }

    public record DiagnosticsRequest() implements CustomPacketPayload {
        public static final DiagnosticsRequest INSTANCE = new DiagnosticsRequest();
        public static final Type<DiagnosticsRequest> TYPE = new Type<>(id("admin_diagnostics_request"));
        public static final StreamCodec<ByteBuf, DiagnosticsRequest> STREAM_CODEC =
                PacketCodecUtils.noPayloadCodec(() -> INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(DiagnosticsRequest payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    sendDiagnostics(player, false);
                }
            });
        }
    }

    public record DiagnosticsReply(LiveDiagnostics.Snapshot diagnostics, DebugLeases.State state)
            implements CustomPacketPayload {
        public static final Type<DiagnosticsReply> TYPE = new Type<>(id("admin_diagnostics"));
        public static final StreamCodec<ByteBuf, DiagnosticsReply> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    writeDiagnostics(buf, p.diagnostics);
                    writeState(buf, p.state);
                },
                buf -> new DiagnosticsReply(readDiagnostics(buf), readState(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handleClient(DiagnosticsReply payload, IPayloadContext context) {
            context.enqueueWork(() -> AdminClientHandlers.onDiagnostics(payload));
        }
    }

    /** Switches one debug tool; {@code value} {@code true|false} or a log level id. */
    public record ToggleRequest(DebugLeases.Tool tool, String value) implements CustomPacketPayload {
        public static final Type<ToggleRequest> TYPE = new Type<>(id("admin_debug_toggle"));
        public static final StreamCodec<ByteBuf, ToggleRequest> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeByte(p.tool.ordinal());
                    PacketCodecUtils.writeString(buf, clip(p.value));
                },
                buf -> new ToggleRequest(readEnum(buf, DebugLeases.Tool.values(), "debug tool"),
                        clip(PacketCodecUtils.readString(buf))));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(ToggleRequest payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    toggle(player, payload);
                }
            });
        }
    }

    /** A debug tool's outcome: {@code ok}, {@code unauthorized}, {@code panel_only}, {@code invalid_value}. */
    public record ToolReply(DebugLeases.Tool tool, boolean success, String code) implements CustomPacketPayload {
        public static final Type<ToolReply> TYPE = new Type<>(id("admin_debug_toggle_reply"));
        public static final StreamCodec<ByteBuf, ToolReply> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeByte(p.tool.ordinal());
                    buf.writeBoolean(p.success);
                    PacketCodecUtils.writeString(buf, clip(p.code));
                },
                buf -> new ToolReply(readEnum(buf, DebugLeases.Tool.values(), "debug tool"), buf.readBoolean(),
                        PacketCodecUtils.readString(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handleClient(ToolReply payload, IPayloadContext context) {
            context.enqueueWork(() -> AdminClientHandlers.onToolReply(payload));
        }
    }

    /**
     * The Control Center's lease heartbeat: {@code release=false} renews this administrator's debug tools while the
     * screen is open, {@code release=true} (sent when it closes) puts them all back.
     */
    public record LeaseRequest(boolean release) implements CustomPacketPayload {
        public static final Type<LeaseRequest> TYPE = new Type<>(id("admin_debug_lease"));
        public static final StreamCodec<ByteBuf, LeaseRequest> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeBoolean(p.release),
                buf -> new LeaseRequest(buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(LeaseRequest payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    // Releasing is always allowed (it can only turn things off); renewing touches nothing but a clock.
                    if (payload.release) {
                        DebugLeases.release(player.getUUID(), player.level().getServer());
                    } else {
                        DebugLeases.renew(player.getUUID());
                    }
                }
            });
        }
    }
}
