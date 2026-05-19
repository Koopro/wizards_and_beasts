package at.koopro.wizardsandbeasts.network.map;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.map.MaraudersMapTracker;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MapCloseC2SPayload() implements CustomPacketPayload {

    public static final Type<MapCloseC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "map_close"));

    public static final StreamCodec<ByteBuf, MapCloseC2SPayload> STREAM_CODEC =
            PacketCodecUtils.noPayloadCodec(MapCloseC2SPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MapCloseC2SPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (player != null) {
                MaraudersMapTracker.removePlayer(player.getUUID());
            }
        });
    }
}
