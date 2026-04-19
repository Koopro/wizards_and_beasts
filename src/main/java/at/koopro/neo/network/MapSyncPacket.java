package at.koopro.neo.network;

import at.koopro.neo.Neo;
import at.koopro.neo.client.map.MapClientHandler;
import at.koopro.neo.item.map.TrackedEntityEntry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record MapSyncPacket(
        List<TrackedEntityEntry> entries
) implements CustomPacketPayload {

    public static final Type<MapSyncPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Neo.MODID, "map_sync"));

    public static final StreamCodec<ByteBuf, MapSyncPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MapSyncPacket decode(ByteBuf buf) {
            int count = buf.readInt();
            List<TrackedEntityEntry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                UUID uuid = PacketCodecUtils.readUUID(buf);
                double x = buf.readDouble();
                double z = buf.readDouble();
                float yaw = buf.readFloat();
                String name = PacketCodecUtils.readString(buf);
                byte category = buf.readByte();
                entries.add(new TrackedEntityEntry(uuid, x, z, yaw, name, category));
            }
            return new MapSyncPacket(entries);
        }

        @Override
        public void encode(ByteBuf buf, MapSyncPacket pkt) {
            buf.writeInt(pkt.entries.size());
            for (TrackedEntityEntry e : pkt.entries) {
                PacketCodecUtils.writeUUID(buf, e.uuid());
                buf.writeDouble(e.x());
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

    public static void handleClient(MapSyncPacket pkt, IPayloadContext ctx) {
        MapClientHandler.handleMapSync(pkt, ctx);
    }
}
