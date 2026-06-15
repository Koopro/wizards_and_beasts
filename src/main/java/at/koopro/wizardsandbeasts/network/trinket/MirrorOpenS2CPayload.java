package at.koopro.wizardsandbeasts.network.trinket;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/** Server tells the client a mirror connection opened; carries the other party's identity. */
public record MirrorOpenS2CPayload(UUID otherUuid, String otherName) implements CustomPacketPayload {

    public static final Type<MirrorOpenS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "mirror_open"));

    public static final StreamCodec<ByteBuf, MirrorOpenS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MirrorOpenS2CPayload decode(ByteBuf buf) {
            UUID uuid = PacketCodecUtils.readUUID(buf);
            String name = PacketCodecUtils.readString(buf);
            return new MirrorOpenS2CPayload(uuid, name);
        }

        @Override
        public void encode(ByteBuf buf, MirrorOpenS2CPayload pkt) {
            PacketCodecUtils.writeUUID(buf, pkt.otherUuid);
            PacketCodecUtils.writeString(buf, pkt.otherName);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
