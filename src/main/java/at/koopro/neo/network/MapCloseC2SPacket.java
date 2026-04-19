package at.koopro.neo.network;

import at.koopro.neo.Neo;
import at.koopro.neo.item.map.MaraudersMapTracker;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MapCloseC2SPacket() implements CustomPacketPayload {

    public static final Type<MapCloseC2SPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Neo.MODID, "map_close"));

    public static final StreamCodec<ByteBuf, MapCloseC2SPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MapCloseC2SPacket decode(ByteBuf buf) {
            return new MapCloseC2SPacket();
        }

        @Override
        public void encode(ByteBuf buf, MapCloseC2SPacket pkt) {
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(MapCloseC2SPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (player != null) {
                MaraudersMapTracker.removePlayer(player.getUUID());
            }
        });
    }
}
