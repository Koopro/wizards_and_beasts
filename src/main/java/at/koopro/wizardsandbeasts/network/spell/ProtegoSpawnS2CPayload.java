package at.koopro.wizardsandbeasts.network.spell;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

public record ProtegoSpawnS2CPayload(
        int entityId,
        int tier,
        UUID casterUUID,
        double x,
        double y,
        double z
) implements CustomPacketPayload {
    public static final Type<ProtegoSpawnS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "protego_spawn"));

    public static final StreamCodec<ByteBuf, ProtegoSpawnS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ProtegoSpawnS2CPayload decode(ByteBuf buf) {
            return new ProtegoSpawnS2CPayload(
                    buf.readInt(),
                    buf.readInt(),
                    PacketCodecUtils.readUUID(buf),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble());
        }

        @Override
        public void encode(ByteBuf buf, ProtegoSpawnS2CPayload pkt) {
            buf.writeInt(pkt.entityId);
            buf.writeInt(pkt.tier);
            PacketCodecUtils.writeUUID(buf, pkt.casterUUID);
            buf.writeDouble(pkt.x);
            buf.writeDouble(pkt.y);
            buf.writeDouble(pkt.z);
        }
    };

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
