package at.koopro.wizardsandbeasts.network;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.network.ProtegoClientPacketHandlers;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

public record ProtegoSpawnS2CPacket(
        int entityId,
        int tier,
        UUID casterUUID,
        double x,
        double y,
        double z
) implements CustomPacketPayload {
    public static final Type<ProtegoSpawnS2CPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "protego_spawn"));

    public static final StreamCodec<ByteBuf, ProtegoSpawnS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ProtegoSpawnS2CPacket decode(ByteBuf buf) {
            return new ProtegoSpawnS2CPacket(
                    buf.readInt(),
                    buf.readInt(),
                    PacketCodecUtils.readUUID(buf),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble());
        }

        @Override
        public void encode(ByteBuf buf, ProtegoSpawnS2CPacket pkt) {
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

    public static void handleClient(ProtegoSpawnS2CPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ProtegoClientPacketHandlers.handleSpawn(pkt));
    }
}
