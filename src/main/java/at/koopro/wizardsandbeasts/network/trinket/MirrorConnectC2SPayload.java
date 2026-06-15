package at.koopro.wizardsandbeasts.network.trinket;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.mirror.MirrorSessionManager;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** Client asks to open a two-way-mirror connection by speaking the recipient's name. */
public record MirrorConnectC2SPayload(UUID pairId, String spokenName) implements CustomPacketPayload {

    public static final Type<MirrorConnectC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "mirror_connect"));

    public static final StreamCodec<ByteBuf, MirrorConnectC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MirrorConnectC2SPayload decode(ByteBuf buf) {
            UUID pairId = PacketCodecUtils.readUUID(buf);
            String name = PacketCodecUtils.readString(buf);
            return new MirrorConnectC2SPayload(pairId, name);
        }

        @Override
        public void encode(ByteBuf buf, MirrorConnectC2SPayload pkt) {
            PacketCodecUtils.writeUUID(buf, pkt.pairId);
            PacketCodecUtils.writeString(buf, pkt.spokenName);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MirrorConnectC2SPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            MirrorSessionManager.requestConnect(player, pkt.pairId, pkt.spokenName);
        });
    }
}
