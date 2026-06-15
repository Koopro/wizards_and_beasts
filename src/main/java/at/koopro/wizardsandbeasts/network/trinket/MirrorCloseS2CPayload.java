package at.koopro.wizardsandbeasts.network.trinket;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server tells the client the mirror connection ended, with a reason to display. */
public record MirrorCloseS2CPayload(String reason) implements CustomPacketPayload {

    public static final Type<MirrorCloseS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "mirror_close_s2c"));

    public static final StreamCodec<ByteBuf, MirrorCloseS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MirrorCloseS2CPayload decode(ByteBuf buf) {
            return new MirrorCloseS2CPayload(PacketCodecUtils.readString(buf));
        }

        @Override
        public void encode(ByteBuf buf, MirrorCloseS2CPayload pkt) {
            PacketCodecUtils.writeString(buf, pkt.reason);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
