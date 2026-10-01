package at.koopro.wizardsandbeasts.network.admin;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.visual.BeamVisualAdminService;
import at.koopro.wizardsandbeasts.admin.visual.BeamVisualAdminService.BeamSummary;
import at.koopro.wizardsandbeasts.admin.visual.BeamVisualAdminService.PresetOp;
import at.koopro.wizardsandbeasts.client.admin.AdminClientHandlers;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import at.koopro.wizardsandbeasts.visual.beam.BeamPreset;
import at.koopro.wizardsandbeasts.visual.beam.BeamVisualService;
import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * The Visuals section's beam page: the beam spells with their looks, the preset library, and preset operations.
 * Every value edit is an ordinary {@link AdminChangeSettingC2SPayload} with a beam setting id. Only administrators
 * with the visual capability are answered.
 */
@NullMarked
public final class AdminVisualPayloads {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_BEAMS = 64;
    private static final int MAX_PRESETS = BeamPreset.MAX_CUSTOM + MAX_BEAMS;
    private static final int MAX_VALUES = 64;

    private AdminVisualPayloads() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }

    // ── wire helpers ──

    private static <T> void writeList(ByteBuf buf, List<T> list, int max, BiConsumer<ByteBuf, T> writer) {
        int count = Math.min(list.size(), max);
        buf.writeInt(count);
        for (int i = 0; i < count; i++) {
            writer.accept(buf, list.get(i));
        }
    }

    private static <T> List<T> readList(ByteBuf buf, int max, String what, Function<ByteBuf, T> reader) {
        int count = PacketCodecUtils.readBoundedCount(buf, max, what);
        List<T> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(reader.apply(buf));
        }
        return List.copyOf(out);
    }

    static void writeValues(ByteBuf buf, Map<String, String> values) {
        int count = Math.min(values.size(), MAX_VALUES);
        buf.writeInt(count);
        values.entrySet().stream().limit(count).forEach(entry -> {
            PacketCodecUtils.writeString(buf, entry.getKey());
            PacketCodecUtils.writeString(buf, entry.getValue());
        });
    }

    static Map<String, String> readValues(ByteBuf buf) {
        int count = PacketCodecUtils.readBoundedCount(buf, MAX_VALUES, "admin-beam-values");
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            out.put(PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf));
        }
        return out;
    }

    static void writeSummary(ByteBuf buf, BeamSummary s) {
        PacketCodecUtils.writeString(buf, s.spell());
        buf.writeInt(s.color());
        buf.writeBoolean(s.enabled());
        buf.writeBoolean(s.overridden());
        PacketCodecUtils.writeString(buf, s.preset());
        writeValues(buf, s.authored());
        writeValues(buf, s.effective());
    }

    static BeamSummary readSummary(ByteBuf buf) {
        return new BeamSummary(PacketCodecUtils.readString(buf), buf.readInt(), buf.readBoolean(), buf.readBoolean(),
                PacketCodecUtils.readString(buf), readValues(buf), readValues(buf));
    }

    static void writePreset(ByteBuf buf, BeamPreset p) {
        PacketCodecUtils.writeString(buf, p.id());
        PacketCodecUtils.writeString(buf, p.name());
        buf.writeBoolean(p.builtin());
        writeValues(buf, p.values());
    }

    static BeamPreset readPreset(ByteBuf buf) {
        return new BeamPreset(PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf), buf.readBoolean(),
                readValues(buf));
    }

    // ── server side ──

    /** The listing, when the sender may read it. */
    public static boolean sendList(ServerPlayer player) {
        AdminContext actor = AdminContext.of(player);
        if (!BeamVisualAdminService.authorised(actor)) {
            LOGGER.warn("[Admin] Dropped beam visual list request from unauthorised {} ({})",
                    player.getName().getString(), player.getUUID());
            return false;
        }
        PacketDistributor.sendToPlayer(player, new ListReply(BeamVisualAdminService.list(player.level().getServer(), actor)));
        return true;
    }

    /** Runs a preset operation for an authorised sender and answers with its outcome and a fresh listing. */
    public static String runPreset(ServerPlayer player, PresetRequest request) {
        AdminContext actor = AdminContext.of(player);
        if (!BeamVisualAdminService.authorised(actor)) {
            LOGGER.warn("[Admin] Refused beam preset {} from unauthorised {} ({})", request.op(),
                    player.getName().getString(), player.getUUID());
            PacketDistributor.sendToPlayer(player, new ActionReply(request.op(), "unauthorized", ""));
            return "unauthorized";
        }
        BeamVisualService.PresetResult result = BeamVisualAdminService.run(player.level().getServer(), request.op(),
                request.presetId(), request.name(), request.values());
        String outcome = result.outcome().name().toLowerCase(java.util.Locale.ROOT);
        PacketDistributor.sendToPlayer(player, new ActionReply(request.op(), outcome, result.presetId()));
        if (result.ok()) {
            // Every open Visuals page, the requester's included, lists the library afresh.
            for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
                if (BeamVisualAdminService.authorised(AdminContext.of(other))) {
                    sendList(other);
                }
            }
        }
        return outcome;
    }

    // ── payloads ──

    public record ListRequest() implements CustomPacketPayload {
        public static final ListRequest INSTANCE = new ListRequest();
        public static final Type<ListRequest> TYPE = new Type<>(id("admin_beam_list_request"));
        public static final StreamCodec<ByteBuf, ListRequest> STREAM_CODEC = PacketCodecUtils.noPayloadCodec(() -> INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(ListRequest payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    sendList(player);
                }
            });
        }
    }

    public record ListReply(BeamVisualAdminService.Listing listing) implements CustomPacketPayload {
        public static final Type<ListReply> TYPE = new Type<>(id("admin_beam_list"));
        public static final StreamCodec<ByteBuf, ListReply> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    writeList(buf, p.listing.beams(), MAX_BEAMS, AdminVisualPayloads::writeSummary);
                    writeList(buf, p.listing.presets(), MAX_PRESETS, AdminVisualPayloads::writePreset);
                    writeList(buf, p.listing.settings(), MAX_BEAMS * MAX_VALUES, AdminSettingDescriptor::write);
                },
                buf -> new ListReply(new BeamVisualAdminService.Listing(
                        readList(buf, MAX_BEAMS, "admin-beams", AdminVisualPayloads::readSummary),
                        readList(buf, MAX_PRESETS, "admin-beam-presets", AdminVisualPayloads::readPreset),
                        readList(buf, MAX_BEAMS * MAX_VALUES, "admin-beam-settings", AdminSettingDescriptor::read))));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handleClient(ListReply payload, IPayloadContext context) {
            context.enqueueWork(() -> AdminClientHandlers.onBeamList(payload));
        }
    }

    /**
     * A preset operation. {@code presetId}: the preset acted on (source of a duplicate); {@code name}: new name (create,
     * duplicate, rename); {@code values}: the look to store (create, save). Everything is re-validated on the server.
     */
    public record PresetRequest(PresetOp op, String presetId, String name, Map<String, String> values)
            implements CustomPacketPayload {
        public static final Type<PresetRequest> TYPE = new Type<>(id("admin_beam_preset"));
        public static final StreamCodec<ByteBuf, PresetRequest> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeByte(p.op.ordinal());
                    PacketCodecUtils.writeString(buf, p.presetId);
                    PacketCodecUtils.writeString(buf, p.name);
                    writeValues(buf, p.values);
                },
                buf -> {
                    int ordinal = buf.readByte();
                    PresetOp[] ops = PresetOp.values();
                    if (ordinal < 0 || ordinal >= ops.length) {
                        throw new io.netty.handler.codec.DecoderException("unknown beam preset op " + ordinal);
                    }
                    return new PresetRequest(ops[ordinal], PacketCodecUtils.readString(buf),
                            PacketCodecUtils.readString(buf), readValues(buf));
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(PresetRequest payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    runPreset(player, payload);
                }
            });
        }
    }

    /** The outcome of a preset operation ({@code ok}, {@code name_taken}, {@code builtin}, …) and the preset's id. */
    public record ActionReply(PresetOp op, String outcome, String presetId) implements CustomPacketPayload {
        public static final Type<ActionReply> TYPE = new Type<>(id("admin_beam_preset_reply"));
        public static final StreamCodec<ByteBuf, ActionReply> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeByte(p.op.ordinal());
                    PacketCodecUtils.writeString(buf, p.outcome);
                    PacketCodecUtils.writeString(buf, p.presetId);
                },
                buf -> {
                    int ordinal = buf.readByte();
                    PresetOp[] ops = PresetOp.values();
                    PresetOp op = ordinal >= 0 && ordinal < ops.length ? ops[ordinal] : PresetOp.CREATE;
                    return new ActionReply(op, PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf));
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handleClient(ActionReply payload, IPayloadContext context) {
            context.enqueueWork(() -> AdminClientHandlers.onBeamAction(payload));
        }
    }
}
