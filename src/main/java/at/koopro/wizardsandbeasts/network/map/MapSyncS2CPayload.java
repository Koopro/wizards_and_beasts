package at.koopro.wizardsandbeasts.network.map;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.map.TrackedEntityEntry;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The moving dots, resent in full every sweep.
 *
 * <p>Whole-list rather than delta because everything in it is moving: a delta of a list where most
 * entries changed is the list plus bookkeeping.
 */
public record MapSyncS2CPayload(
        List<TrackedEntityEntry> entries
) implements CustomPacketPayload {

    public static final Type<MapSyncS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "map_sync"));

    public static final StreamCodec<ByteBuf, MapSyncS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MapSyncS2CPayload decode(ByteBuf buf) {
            int count = PacketCodecUtils.readBoundedCount(buf, PacketCodecUtils.MAX_MAP_ENTRIES, "map-entries");
            List<TrackedEntityEntry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                UUID uuid = PacketCodecUtils.readUUID(buf);
                double x = buf.readDouble();
                double y = buf.readDouble();
                double z = buf.readDouble();
                float yaw = buf.readFloat();
                String name = PacketCodecUtils.readString(buf);
                byte category = buf.readByte();
                entries.add(new TrackedEntityEntry(uuid, x, y, z, yaw, name, category));
            }
            return new MapSyncS2CPayload(entries);
        }

        @Override
        public void encode(ByteBuf buf, MapSyncS2CPayload pkt) {
            // The bound is asserted on the way out as well as on the way in. It used to be checked
            // only on decode, so a sweep that returned more than the cap wrote a count the reader
            // then rejected -- and the rejection came mid-stream, after the header, which
            // desynchronises the connection rather than dropping one packet.
            if (pkt.entries.size() > PacketCodecUtils.MAX_MAP_ENTRIES) {
                throw new IllegalArgumentException(
                        "Too many map entries for one packet: " + pkt.entries.size());
            }
            buf.writeInt(pkt.entries.size());
            for (TrackedEntityEntry e : pkt.entries) {
                PacketCodecUtils.writeUUID(buf, e.uuid());
                buf.writeDouble(e.x());
                buf.writeDouble(e.y());
                buf.writeDouble(e.z());
                buf.writeFloat(e.yaw());
                PacketCodecUtils.writeString(buf, e.displayName());
                buf.writeByte(e.category());
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
