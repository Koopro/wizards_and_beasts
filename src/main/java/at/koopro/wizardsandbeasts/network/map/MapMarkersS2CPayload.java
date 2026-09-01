package at.koopro.wizardsandbeasts.network.map;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.map.MapMarker;
import at.koopro.wizardsandbeasts.map.MapMarkerSource;
import at.koopro.wizardsandbeasts.map.MapMarkerTypes;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The full marker list for a map, filtered on the server to what this viewer may see.
 *
 * <p>Sent whole rather than as a diff. Markers change rarely — a discovery, a pin, a death — and
 * there are at most a few hundred, so the list is small; a diff protocol would add ordering,
 * acknowledgement and resync-on-desync for a payload that fits comfortably in one packet.
 *
 * <p>Filtering happens before encoding, never on the client: another player's private pins must not
 * be on the wire at all. A client-side filter is a client-side filter.
 */
public record MapMarkersS2CPayload(
        List<MapMarker> markers
) implements CustomPacketPayload {

    public static final Type<MapMarkersS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "map_markers"));

    private static final Identifier OVERWORLD =
            Identifier.fromNamespaceAndPath("minecraft", "overworld");

    public static final StreamCodec<ByteBuf, MapMarkersS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MapMarkersS2CPayload decode(ByteBuf buf) {
            int count = PacketCodecUtils.readBoundedCount(buf, PacketCodecUtils.MAX_MAP_MARKERS,
                    "map-markers");
            List<MapMarker> markers = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                UUID id = PacketCodecUtils.readUUID(buf);
                Identifier type = PacketCodecUtils.readIdentifier(buf, MapMarkerTypes.WAYPOINT);
                Identifier dimension = PacketCodecUtils.readIdentifier(buf, OVERWORLD);
                int x = buf.readInt();
                int z = buf.readInt();
                String label = PacketCodecUtils.readString(buf);
                boolean translatable = buf.readBoolean();
                MapMarkerSource source = sourceById(buf.readByte());
                boolean owned = buf.readBoolean();
                Optional<UUID> owner = owned
                        ? Optional.of(PacketCodecUtils.readUUID(buf))
                        : Optional.empty();
                boolean hidden = buf.readBoolean();
                long discoveredAt = buf.readLong();
                markers.add(new MapMarker(id, type, dimension, x, z, label, translatable, source,
                        owner, hidden, discoveredAt));
            }
            return new MapMarkersS2CPayload(markers);
        }

        @Override
        public void encode(ByteBuf buf, MapMarkersS2CPayload pkt) {
            buf.writeInt(pkt.markers.size());
            for (MapMarker marker : pkt.markers) {
                PacketCodecUtils.writeUUID(buf, marker.id());
                PacketCodecUtils.writeIdentifier(buf, marker.type());
                PacketCodecUtils.writeIdentifier(buf, marker.dimension());
                buf.writeInt(marker.x());
                buf.writeInt(marker.z());
                PacketCodecUtils.writeString(buf, marker.label());
                buf.writeBoolean(marker.translatable());
                buf.writeByte(marker.source().ordinal());
                buf.writeBoolean(marker.owner().isPresent());
                marker.owner().ifPresent(owner -> PacketCodecUtils.writeUUID(buf, owner));
                buf.writeBoolean(marker.hidden());
                buf.writeLong(marker.discoveredAt());
            }
        }
    };

    /**
     * Maps a wire ordinal onto a source, treating anything unknown as a discovery.
     *
     * <p>A discovery is the read-only kind, so an out-of-range byte lands on the variant the client
     * refuses to let anyone edit rather than on one it will happily delete.
     */
    private static MapMarkerSource sourceById(byte id) {
        MapMarkerSource[] values = MapMarkerSource.values();
        return id >= 0 && id < values.length ? values[id] : MapMarkerSource.DISCOVERY;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
