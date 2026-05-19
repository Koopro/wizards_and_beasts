package at.koopro.wizardsandbeasts.network.map;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.map.MapClientHandler;
import at.koopro.wizardsandbeasts.map.TrackedEntityEntry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
                double z = buf.readDouble();
                float yaw = buf.readFloat();
                String name = PacketCodecUtils.readString(buf);
                byte category = buf.readByte();
                entries.add(new TrackedEntityEntry(uuid, x, z, yaw, name, category));
            }
            return new MapSyncS2CPayload(entries);
        }

        @Override
        public void encode(ByteBuf buf, MapSyncS2CPayload pkt) {
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

    public static void handleClient(MapSyncS2CPayload pkt, IPayloadContext ctx) {
        MapClientHandler.handleMapSync(pkt, ctx);
    }
}
