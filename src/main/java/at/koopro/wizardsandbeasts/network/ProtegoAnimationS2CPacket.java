package at.koopro.wizardsandbeasts.network;

import at.koopro.wizardsandbeasts.client.network.ProtegoClientPacketHandlers;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.entity.ProtegoShieldEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

public record ProtegoAnimationS2CPacket(int entityId, String animationTrigger) implements CustomPacketPayload {
    public static final Type<ProtegoAnimationS2CPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "protego_animation"));

    public static final StreamCodec<ByteBuf, ProtegoAnimationS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ProtegoAnimationS2CPacket decode(ByteBuf buf) {
            return new ProtegoAnimationS2CPacket(buf.readInt(), PacketCodecUtils.readString(buf));
        }

        @Override
        public void encode(ByteBuf buf, ProtegoAnimationS2CPacket pkt) {
            buf.writeInt(pkt.entityId);
            PacketCodecUtils.writeString(buf, pkt.animationTrigger);
        }
    };

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(ProtegoAnimationS2CPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ProtegoClientPacketHandlers.handleAnimation(pkt));
    }

    public static void sendToTracking(Entity entity, String trigger) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity,
                new ProtegoAnimationS2CPacket(entity.getId(), trigger));
    }
}
