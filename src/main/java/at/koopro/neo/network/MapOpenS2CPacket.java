package at.koopro.neo.network;

import at.koopro.neo.Neo;
import at.koopro.neo.client.map.MapClientHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MapOpenS2CPacket(
        BlockPos center,
        int radius,
        Identifier dimension
) implements CustomPacketPayload {

    public static final Type<MapOpenS2CPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Neo.MODID, "map_open"));

    public static final StreamCodec<ByteBuf, MapOpenS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MapOpenS2CPacket decode(ByteBuf buf) {
            int x = buf.readInt();
            int z = buf.readInt();
            int radius = buf.readInt();
            String dim = PacketCodecUtils.readString(buf);
            return new MapOpenS2CPacket(
                    new BlockPos(x, 0, z),
                    radius,
                    Identifier.parse(dim));
        }

        @Override
        public void encode(ByteBuf buf, MapOpenS2CPacket pkt) {
            buf.writeInt(pkt.center.getX());
            buf.writeInt(pkt.center.getZ());
            buf.writeInt(pkt.radius);
            PacketCodecUtils.writeString(buf, pkt.dimension.toString());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(MapOpenS2CPacket pkt, IPayloadContext ctx) {
        MapClientHandler.handleMapOpen(pkt, ctx);
    }
}
