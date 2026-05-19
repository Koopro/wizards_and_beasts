package at.koopro.wizardsandbeasts.network.floo;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
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

    public static void handleClient(@NonNull FlooArrivalEffectsS2CPayload packet, @NonNull IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            double cx = packet.pos.getX() + 0.5;
            double cy = packet.pos.getY() + 0.5;
            double cz = packet.pos.getZ() + 0.5;
            mc.level.playLocalSound(packet.pos, ModSounds.FLOO_WHOOSH.get(), SoundSource.BLOCKS, 0.9f, 1.0f, false);
            for (int i = 0; i < 24; i++) {
                double ox = (mc.level.random.nextDouble() - 0.5) * 0.8;
                double oz = (mc.level.random.nextDouble() - 0.5) * 0.8;
                mc.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, cx + ox, cy, cz + oz, 0, 0.05, 0);
            }
        });
    }
}
