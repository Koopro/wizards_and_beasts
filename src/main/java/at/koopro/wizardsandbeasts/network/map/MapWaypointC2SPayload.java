package at.koopro.wizardsandbeasts.network.map;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.map.MapAtlas;
import at.koopro.wizardsandbeasts.map.MapMarkerService;
import at.koopro.wizardsandbeasts.map.MapMarkerTypes;
import at.koopro.wizardsandbeasts.map.MapSession;
import at.koopro.wizardsandbeasts.map.MapSessions;
import at.koopro.wizardsandbeasts.map.MaraudersMapAtlasStore;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Every waypoint edit, in one payload.
 *
 * <p>One message with an action rather than five messages, because the authorization is identical
 * across all of them and the difference between them is two fields. Five payloads would be five
 * registrations, five handlers and five chances to forget the session check.
 *
 * <p>Nothing here is trusted. The client sends what the player asked for and the server decides,
 * via {@link MapMarkerService}, whether it happens — including whether the player has a map open at
 * all, which is what stops a crafted packet from editing a map the sender is not holding.
 */
public record MapWaypointC2SPayload(
        Action action,
        UUID markerId,
        Identifier markerType,
        int x,
        int z,
        String label,
        boolean hidden
) implements CustomPacketPayload {

    /** The zero UUID, sent for {@link Action#CREATE} where there is no marker to name yet. */
    private static final UUID NO_MARKER = new UUID(0L, 0L);

    public enum Action {
        CREATE,
        RENAME,
        RETYPE,
        SET_HIDDEN,
        DELETE
    }

    public static final Type<MapWaypointC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "map_waypoint"));

    public static MapWaypointC2SPayload create(Identifier type, int x, int z, String label) {
        return new MapWaypointC2SPayload(Action.CREATE, NO_MARKER, type, x, z, label, false);
    }

    public static MapWaypointC2SPayload rename(UUID markerId, String label) {
        return new MapWaypointC2SPayload(Action.RENAME, markerId, MapMarkerTypes.WAYPOINT, 0, 0,
                label, false);
    }

    public static MapWaypointC2SPayload retype(UUID markerId, Identifier type) {
        return new MapWaypointC2SPayload(Action.RETYPE, markerId, type, 0, 0, "", false);
    }

    public static MapWaypointC2SPayload setHidden(UUID markerId, boolean hidden) {
        return new MapWaypointC2SPayload(Action.SET_HIDDEN, markerId, MapMarkerTypes.WAYPOINT, 0, 0,
                "", hidden);
    }

    public static MapWaypointC2SPayload delete(UUID markerId) {
        return new MapWaypointC2SPayload(Action.DELETE, markerId, MapMarkerTypes.WAYPOINT, 0, 0,
                "", false);
    }

    public static final StreamCodec<ByteBuf, MapWaypointC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MapWaypointC2SPayload decode(ByteBuf buf) {
            Action action = actionById(buf.readByte());
            UUID markerId = PacketCodecUtils.readUUID(buf);
            Identifier type = PacketCodecUtils.readIdentifier(buf, MapMarkerTypes.WAYPOINT);
            int x = buf.readInt();
            int z = buf.readInt();
            String label = PacketCodecUtils.readString(buf);
            boolean hidden = buf.readBoolean();
            return new MapWaypointC2SPayload(action, markerId, type, x, z, label, hidden);
        }

        @Override
        public void encode(ByteBuf buf, MapWaypointC2SPayload pkt) {
            buf.writeByte(pkt.action.ordinal());
            PacketCodecUtils.writeUUID(buf, pkt.markerId);
            PacketCodecUtils.writeIdentifier(buf, pkt.markerType);
            buf.writeInt(pkt.x);
            buf.writeInt(pkt.z);
            PacketCodecUtils.writeString(buf, pkt.label);
            buf.writeBoolean(pkt.hidden);
        }
    };

    private static Action actionById(byte id) {
        Action[] values = Action.values();
        if (id < 0 || id >= values.length) {
            throw new IllegalArgumentException("Invalid map-waypoint action: " + id);
        }
        return values[id];
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MapWaypointC2SPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            MapSession session = MapSessions.get(player.getUUID());
            if (session == null) {
                return; // no map open: nothing to edit
            }
            MaraudersMapAtlasStore store = MaraudersMapAtlasStore.get(level);
            MapAtlas atlas = store.peek(session.mapId());
            if (atlas == null) {
                return;
            }

            MapMarkerService.Result result = switch (pkt.action) {
                case CREATE -> MapMarkerService.createWaypoint(player, atlas, pkt.markerType,
                        session.dimension(), pkt.x, pkt.z, pkt.label);
                case RENAME -> MapMarkerService.rename(player, atlas, pkt.markerId, pkt.label);
                case RETYPE -> MapMarkerService.retype(player, atlas, pkt.markerId, pkt.markerType);
                case SET_HIDDEN -> MapMarkerService.setHidden(player, atlas, pkt.markerId, pkt.hidden);
                case DELETE -> MapMarkerService.delete(player, atlas, pkt.markerId);
            };

            if (result == MapMarkerService.Result.OK) {
                store.markChanged();
                MapSessions.invalidateMarkers(session.mapId());
            } else {
                refuse(player, result);
            }
        });
    }

    /**
     * Tells the player why the ink refused.
     *
     * <p>A silent no-op here is the defect this mod already fixed once for spell casting: a control
     * that does nothing and says nothing reads as broken. {@link MapMarkerService.Result#NOT_FOUND}
     * is the exception — it means the client acted on a marker the server has already removed,
     * which the incoming marker resync corrects on its own.
     */
    private static void refuse(ServerPlayer player, MapMarkerService.Result result) {
        String key = switch (result) {
            case FULL -> "map.wizards_and_beasts.waypoint.refused.full";
            case NOT_EDITABLE -> "map.wizards_and_beasts.waypoint.refused.not_editable";
            case DENIED -> "map.wizards_and_beasts.waypoint.refused.denied";
            case NOT_FOUND, OK -> null;
        };
        if (key == null) {
            return;
        }
        PlayerFeedback.toast(player, NoticeKind.FAIL,
                Component.translatable("map.wizards_and_beasts.waypoint.refused"),
                Component.translatable(key, MapAtlas.MAX_WAYPOINTS_PER_PLAYER));
    }
}
