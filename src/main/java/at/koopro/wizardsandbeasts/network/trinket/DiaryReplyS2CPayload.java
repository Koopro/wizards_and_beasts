package at.koopro.wizardsandbeasts.network.trinket;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Riddle's written reply. {@code possess} flips the client into the possession takeover screen. */
public record DiaryReplyS2CPayload(String line, int tier, boolean possess) implements CustomPacketPayload {

    public static final Type<DiaryReplyS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "diary_reply"));

    public static final StreamCodec<ByteBuf, DiaryReplyS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public DiaryReplyS2CPayload decode(ByteBuf buf) {
            String line = PacketCodecUtils.readString(buf);
            int tier = buf.readInt();
            boolean possess = buf.readBoolean();
            return new DiaryReplyS2CPayload(line, tier, possess);
        }

        @Override
        public void encode(ByteBuf buf, DiaryReplyS2CPayload pkt) {
            PacketCodecUtils.writeString(buf, pkt.line);
            buf.writeInt(pkt.tier);
            buf.writeBoolean(pkt.possess);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
