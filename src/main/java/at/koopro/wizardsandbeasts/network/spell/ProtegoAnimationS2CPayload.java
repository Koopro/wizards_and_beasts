package at.koopro.wizardsandbeasts.network.spell;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;

import at.koopro.wizardsandbeasts.client.spell.network.ProtegoClientPacketHandlers;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

public record ProtegoAnimationS2CPayload(int entityId, String animationTrigger) implements CustomPacketPayload {
    public static final Type<ProtegoAnimationS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "protego_animation"));

    public static final StreamCodec<ByteBuf, ProtegoAnimationS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ProtegoAnimationS2CPayload decode(ByteBuf buf) {
            return new ProtegoAnimationS2CPayload(buf.readInt(), PacketCodecUtils.readString(buf));
        }

        @Override
        public void encode(ByteBuf buf, ProtegoAnimationS2CPayload pkt) {
            buf.writeInt(pkt.entityId);
            PacketCodecUtils.writeString(buf, pkt.animationTrigger);
        }
    };

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(ProtegoAnimationS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ProtegoClientPacketHandlers.handleAnimation(pkt));
    }

    public static void sendToTracking(Entity entity, String trigger) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity,
                new ProtegoAnimationS2CPayload(entity.getId(), trigger));
    }
}
