package at.koopro.wizardsandbeasts.network.floo;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NonNull;

public record FlooBlockSyncS2CPayload(@NonNull BlockPos pos, boolean isLit,
                                      int litTicksRemaining) implements CustomPacketPayload {

    public static final Type<FlooBlockSyncS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "floo_block_sync_s2c"));

    public static final StreamCodec<ByteBuf, FlooBlockSyncS2CPayload> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeLong(pkt.pos.asLong());
                ByteBufCodecs.BOOL.encode(buf, pkt.isLit);
                ByteBufCodecs.VAR_INT.encode(buf, pkt.litTicksRemaining);
            },
            buf -> new FlooBlockSyncS2CPayload(
                    BlockPos.of(buf.readLong()),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf)
            )
    );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendToNear(@NonNull ServerLevel level, @NonNull BlockPos pos,
                                   boolean isLit, int litTicksRemaining) {
        FlooBlockSyncS2CPayload packet = new FlooBlockSyncS2CPayload(pos, isLit, litTicksRemaining);
        for (ServerPlayer nearby : level.players()) {
            if (nearby.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 64.0 * 64.0) {
                PacketDistributor.sendToPlayer(nearby, packet);
            }
        }
    }
}
