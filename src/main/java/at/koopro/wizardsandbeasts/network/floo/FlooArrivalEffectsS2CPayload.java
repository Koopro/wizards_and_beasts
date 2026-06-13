package at.koopro.wizardsandbeasts.network.floo;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NonNull;

public record FlooArrivalEffectsS2CPayload(@NonNull BlockPos pos) implements CustomPacketPayload {

    public static final Type<FlooArrivalEffectsS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "floo_arrival_effects_s2c"));

    public static final StreamCodec<ByteBuf, FlooArrivalEffectsS2CPayload> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> buf.writeLong(pkt.pos.asLong()),
            buf -> new FlooArrivalEffectsS2CPayload(BlockPos.of(buf.readLong()))
    );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendToNear(@NonNull ServerLevel level, @NonNull BlockPos pos) {
        FlooArrivalEffectsS2CPayload packet = new FlooArrivalEffectsS2CPayload(pos);
        for (ServerPlayer nearby : level.players()) {
            if (nearby.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 32.0 * 32.0) {
                PacketDistributor.sendToPlayer(nearby, packet);
            }
        }
    }
}
