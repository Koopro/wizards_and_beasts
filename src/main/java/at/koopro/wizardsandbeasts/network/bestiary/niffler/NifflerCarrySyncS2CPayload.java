package at.koopro.wizardsandbeasts.network.bestiary.niffler;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.entity.niffler.CarriedNifflerAttachment;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Syncs the carried Niffler UUID from server to the owning client.
 * Sent when a player picks up or puts down their Niffler.
 */
public record NifflerCarrySyncS2CPayload(@Nullable UUID nifflerUUID) implements CustomPacketPayload {

    public static final Type<NifflerCarrySyncS2CPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "niffler_carry_sync"));

    public static final StreamCodec<ByteBuf, NifflerCarrySyncS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public NifflerCarrySyncS2CPayload decode(ByteBuf buf) {
            boolean hasUUID = buf.readBoolean();
            UUID uuid = hasUUID ? new UUID(buf.readLong(), buf.readLong()) : null;
            return new NifflerCarrySyncS2CPayload(uuid);
        }

        @Override
        public void encode(ByteBuf buf, NifflerCarrySyncS2CPayload pkt) {
            buf.writeBoolean(pkt.nifflerUUID != null);
            if (pkt.nifflerUUID != null) {
                buf.writeLong(pkt.nifflerUUID.getMostSignificantBits());
                buf.writeLong(pkt.nifflerUUID.getLeastSignificantBits());
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleClient(NifflerCarrySyncS2CPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.setData(ModAttachments.CARRIED_NIFFLER.get(),
                        new CarriedNifflerAttachment(payload.nifflerUUID));
            }
        });
    }

    public static void sendToPlayer(ServerPlayer player) {
        CarriedNifflerAttachment att = player.getData(ModAttachments.CARRIED_NIFFLER.get());
        PacketDistributor.sendToPlayer(player, new NifflerCarrySyncS2CPayload(att.nifflerUUID()));
    }
}
