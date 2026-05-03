package at.koopro.wizardsandbeasts.network;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.item.map.MaraudersMapTracker;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MapCloseC2SPacket() implements CustomPacketPayload {

    public static final Type<MapCloseC2SPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "map_close"));

    public static final StreamCodec<ByteBuf, MapCloseC2SPacket> STREAM_CODEC =
            PacketCodecUtils.noPayloadCodec(MapCloseC2SPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MapCloseC2SPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (player != null) {
                MaraudersMapTracker.removePlayer(player.getUUID());
            }
        });
    }
}
