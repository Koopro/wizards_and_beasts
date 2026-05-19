package at.koopro.wizardsandbeasts.network.trunk;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PocketStatusS2CPayload(String message) implements CustomPacketPayload {
    public static final Type<PocketStatusS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "pocket_status"));

    public static final StreamCodec<ByteBuf, PocketStatusS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public PocketStatusS2CPayload decode(ByteBuf buf) {
            return new PocketStatusS2CPayload(PacketCodecUtils.readString(buf));
        }

        @Override
        public void encode(ByteBuf buf, PocketStatusS2CPayload packet) {
            PacketCodecUtils.writeString(buf, packet.message);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(ServerPlayer player, String message) {
        PacketDistributor.sendToPlayer(player, new PocketStatusS2CPayload(message));
    }

    public static void handleClient(PocketStatusS2CPayload packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.literal(packet.message), true);
            }
        });
    }
}
