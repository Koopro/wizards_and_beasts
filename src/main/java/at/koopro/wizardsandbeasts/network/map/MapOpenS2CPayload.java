package at.koopro.wizardsandbeasts.network.map;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.map.MapClientHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MapOpenS2CPayload(
        BlockPos center,
        int radius,
        Identifier dimension
) implements CustomPacketPayload {

    public static final Type<MapOpenS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "map_open"));

    public static final StreamCodec<ByteBuf, MapOpenS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MapOpenS2CPayload decode(ByteBuf buf) {
            int x = buf.readInt();
            int z = buf.readInt();
            int radius = buf.readInt();
            String dim = PacketCodecUtils.normalizeIdentifier(PacketCodecUtils.readString(buf));
            Identifier dimension = Identifier.tryParse(dim);
            if (dimension == null) {
                dimension = Identifier.fromNamespaceAndPath("minecraft", "overworld");
            }
            return new MapOpenS2CPayload(
                    new BlockPos(x, 0, z),
                    radius,
                    dimension);
        }

        @Override
        public void encode(ByteBuf buf, MapOpenS2CPayload pkt) {
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

    public static void handleClient(MapOpenS2CPayload pkt, IPayloadContext ctx) {
        MapClientHandler.handleMapOpen(pkt, ctx);
    }
}
