package at.koopro.wizardsandbeasts.network.map;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.item.map.MaraudersMapItem;
import at.koopro.wizardsandbeasts.map.MapSessions;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
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
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            MapSessions.close(player.getUUID());

            // Mischief managed. This is the only moment the fold is actually seen -- while
            // the map is open the screen covers the hand holding it -- so the animation is
            // played here rather than anywhere else the session can be closed. The logout,
            // respawn and dimension paths close the session without it, because in none of
            // them is there a hand left to watch.
            if (player.level() instanceof ServerLevel level) {
                ItemStack stack = MaraudersMapItem.findCarried(player);
                if (!stack.isEmpty()) {
                    MaraudersMapItem.fold(player, stack, level);
                }
            }
        });
    }
}
