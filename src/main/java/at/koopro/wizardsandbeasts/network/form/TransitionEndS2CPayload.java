package at.koopro.wizardsandbeasts.network.form;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.form.state.ClientTransitionTracker;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Server → Client: signals a transformation transition has completed.
 */
public record TransitionEndS2CPayload(UUID playerUUID, String newFormId) implements CustomPacketPayload {

    public static final Type<TransitionEndS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "transition_end"));

    public static final StreamCodec<ByteBuf, TransitionEndS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TransitionEndS2CPayload decode(ByteBuf buf) {
            UUID uuid = PacketCodecUtils.readUUID(buf);
            String formId = PacketCodecUtils.readString(buf);
            return new TransitionEndS2CPayload(uuid, formId);
        }

        @Override
        public void encode(ByteBuf buf, TransitionEndS2CPayload pkt) {
            PacketCodecUtils.writeUUID(buf, pkt.playerUUID);
            PacketCodecUtils.writeString(buf, pkt.newFormId);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(TransitionEndS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientTransitionTracker.endTransition(pkt.playerUUID);
        });
    }

    public static void sendToTracking(ServerPlayer player, String newFormId) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new TransitionEndS2CPayload(player.getUUID(), newFormId));
    }
}
