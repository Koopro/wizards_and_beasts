package at.koopro.wizardsandbeasts.network.trinket;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Periodic presence heartbeat carrying the other party's live look direction. */
public record MirrorPresenceS2CPayload(float yaw, float pitch) implements CustomPacketPayload {

    public static final Type<MirrorPresenceS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "mirror_presence"));

    public static final StreamCodec<ByteBuf, MirrorPresenceS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MirrorPresenceS2CPayload decode(ByteBuf buf) {
            return new MirrorPresenceS2CPayload(buf.readFloat(), buf.readFloat());
        }

        @Override
        public void encode(ByteBuf buf, MirrorPresenceS2CPayload pkt) {
            buf.writeFloat(pkt.yaw);
            buf.writeFloat(pkt.pitch);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
