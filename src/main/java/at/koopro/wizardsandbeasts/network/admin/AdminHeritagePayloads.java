package at.koopro.wizardsandbeasts.network.admin;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.heritage.HeritageAdminService;
import at.koopro.wizardsandbeasts.client.admin.AdminClientHandlers;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Heritages section's player payloads. Heritage <em>rules</em> are not here — they are ordinary settings and
 * travel as {@link AdminChangeSettingC2SPayload}s. These cover the player tools only: list, inspect, assign, reset
 * onboarding. The client names players by UUID and heritages by id; it never sends a value to store.
 */
@NullMarked
public final class AdminHeritagePayloads {

    private static final int MAX_FACTS = 32;

    private AdminHeritagePayloads() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }

    // ── client → server ──

    /** "Send me the online players and their heritages." */
    public record PlayersRequest() implements CustomPacketPayload {
        public static final PlayersRequest INSTANCE = new PlayersRequest();
        public static final Type<PlayersRequest> TYPE = new Type<>(id("admin_heritage_players_request"));
        public static final StreamCodec<ByteBuf, PlayersRequest> STREAM_CODEC = PacketCodecUtils.noPayloadCodec(() -> INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(PlayersRequest payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    AdminHeritageNetworkService.sendPlayers(player);
                }
            });
        }
    }

    /** "Show me this player's heritage and stats." */
    public record InspectRequest(UUID player) implements CustomPacketPayload {
        public static final Type<InspectRequest> TYPE = new Type<>(id("admin_heritage_inspect_request"));
        public static final StreamCodec<ByteBuf, InspectRequest> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> PacketCodecUtils.writeUUID(buf, p.player),
                buf -> new InspectRequest(PacketCodecUtils.readUUID(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(InspectRequest payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    AdminHeritageNetworkService.sendInspection(player, payload.player);
                }
            });
        }
    }

    /**
     * "Give this player this heritage." {@code confirmed}: the administrator confirmed it in a dialog; the server
     * refuses an unconfirmed request, so a client that skips the dialog changes nothing.
     */
    public record AssignRequest(UUID player, String heritageId, String variantId, boolean confirmed)
            implements CustomPacketPayload {
        public static final Type<AssignRequest> TYPE = new Type<>(id("admin_heritage_assign"));
        public static final StreamCodec<ByteBuf, AssignRequest> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    PacketCodecUtils.writeUUID(buf, p.player);
                    PacketCodecUtils.writeString(buf, p.heritageId);
                    PacketCodecUtils.writeString(buf, p.variantId);
                    buf.writeBoolean(p.confirmed);
                },
                buf -> new AssignRequest(PacketCodecUtils.readUUID(buf), PacketCodecUtils.readString(buf),
                        PacketCodecUtils.readString(buf), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(AssignRequest payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    AdminHeritageNetworkService.assign(player, payload);
                }
            });
        }
    }

    /** "Send this one player back to the onboarding gate." Confirmed, as {@link AssignRequest}. */
    public record ResetOnboardingRequest(UUID player, boolean confirmed) implements CustomPacketPayload {
        public static final Type<ResetOnboardingRequest> TYPE = new Type<>(id("admin_heritage_reset_onboarding"));
        public static final StreamCodec<ByteBuf, ResetOnboardingRequest> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    PacketCodecUtils.writeUUID(buf, p.player);
                    buf.writeBoolean(p.confirmed);
                },
                buf -> new ResetOnboardingRequest(PacketCodecUtils.readUUID(buf), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(ResetOnboardingRequest payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    AdminHeritageNetworkService.resetOnboarding(player, payload);
                }
            });
        }
    }

    // ── server → client ──

    public record PlayersReply(List<HeritageAdminService.PlayerRow> players) implements CustomPacketPayload {
        public static final Type<PlayersReply> TYPE = new Type<>(id("admin_heritage_players"));
        public static final StreamCodec<ByteBuf, PlayersReply> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    int count = Math.min(p.players.size(), HeritageAdminService.MAX_PLAYERS);
                    buf.writeInt(count);
                    for (int i = 0; i < count; i++) {
                        HeritageAdminService.PlayerRow row = p.players.get(i);
                        PacketCodecUtils.writeUUID(buf, row.id());
                        PacketCodecUtils.writeString(buf, row.name());
                        PacketCodecUtils.writeString(buf, row.heritageId());
                        PacketCodecUtils.writeString(buf, row.variantId());
                    }
                },
                buf -> {
                    int count = PacketCodecUtils.readBoundedCount(buf, HeritageAdminService.MAX_PLAYERS, "admin-heritage-players");
                    List<HeritageAdminService.PlayerRow> rows = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        rows.add(new HeritageAdminService.PlayerRow(PacketCodecUtils.readUUID(buf),
                                PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf),
                                PacketCodecUtils.readString(buf)));
                    }
                    return new PlayersReply(List.copyOf(rows));
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handleClient(PlayersReply payload, IPayloadContext context) {
            context.enqueueWork(() -> AdminClientHandlers.onHeritagePlayers(payload));
        }
    }

    public record InspectReply(HeritageAdminService.Inspection inspection) implements CustomPacketPayload {
        public static final Type<InspectReply> TYPE = new Type<>(id("admin_heritage_inspect"));
        public static final StreamCodec<ByteBuf, InspectReply> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    HeritageAdminService.Inspection i = p.inspection;
                    PacketCodecUtils.writeUUID(buf, i.id());
                    PacketCodecUtils.writeString(buf, i.name());
                    PacketCodecUtils.writeString(buf, i.heritageId());
                    PacketCodecUtils.writeString(buf, i.variantId());
                    writeFacts(buf, i.identity());
                    writeFacts(buf, i.stats());
                    writeFacts(buf, i.derived());
                },
                buf -> new InspectReply(new HeritageAdminService.Inspection(PacketCodecUtils.readUUID(buf),
                        PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf),
                        readFacts(buf), readFacts(buf), readFacts(buf))));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handleClient(InspectReply payload, IPayloadContext context) {
            context.enqueueWork(() -> AdminClientHandlers.onHeritageInspection(payload));
        }
    }

    /** The outcome of an assignment or reset, for the panel's status line. */
    public record ActionReply(boolean success, String messageKey, String detail) implements CustomPacketPayload {
        public static final Type<ActionReply> TYPE = new Type<>(id("admin_heritage_action"));
        public static final StreamCodec<ByteBuf, ActionReply> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBoolean(p.success);
                    PacketCodecUtils.writeString(buf, p.messageKey);
                    PacketCodecUtils.writeString(buf, p.detail);
                },
                buf -> new ActionReply(buf.readBoolean(), PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handleClient(ActionReply payload, IPayloadContext context) {
            context.enqueueWork(() -> AdminClientHandlers.onHeritageAction(payload));
        }
    }

    private static void writeFacts(ByteBuf buf, List<AdminSpellFact> facts) {
        int count = Math.min(facts.size(), MAX_FACTS);
        buf.writeInt(count);
        for (int i = 0; i < count; i++) {
            AdminSpellFact.write(buf, facts.get(i));
        }
    }

    private static List<AdminSpellFact> readFacts(ByteBuf buf) {
        int count = PacketCodecUtils.readBoundedCount(buf, MAX_FACTS, "admin-heritage-facts");
        List<AdminSpellFact> facts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            facts.add(AdminSpellFact.read(buf));
        }
        return List.copyOf(facts);
    }
}
