package at.koopro.wizardsandbeasts.network.ministry;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * "Open the licence screen for the scroll in this hand."
 *
 * <p>Carries a hand and nothing else. The document itself is a network-synchronised data component on
 * a stack the client already has, so shipping the licence in the packet would be sending the client
 * something it is holding — and worse, would let the screen and the tooltip disagree the moment the
 * scroll changed. The hand is the whole message; the client reads the paper.
 *
 * @param offHand true when the scroll being read is in the off hand
 */
public record LicenceOpenS2CPayload(boolean offHand) implements CustomPacketPayload {

    public static final Type<LicenceOpenS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "licence_open"));

    public static final StreamCodec<ByteBuf, LicenceOpenS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public LicenceOpenS2CPayload decode(ByteBuf buf) {
            return new LicenceOpenS2CPayload(buf.readBoolean());
        }

        @Override
        public void encode(ByteBuf buf, LicenceOpenS2CPayload pkt) {
            buf.writeBoolean(pkt.offHand);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void open(ServerPlayer player, InteractionHand hand) {
        PacketDistributor.sendToPlayer(player, new LicenceOpenS2CPayload(hand == InteractionHand.OFF_HAND));
    }
}
