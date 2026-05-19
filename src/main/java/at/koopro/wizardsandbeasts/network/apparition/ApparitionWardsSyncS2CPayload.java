package at.koopro.wizardsandbeasts.network.apparition;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.apparition.ApparitionWard;
import at.koopro.wizardsandbeasts.apparition.ApparitionWardRegistry;
import at.koopro.wizardsandbeasts.client.apparition.state.ClientApparitionWardState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public record ApparitionWardsSyncS2CPayload(List<WardData> wards) implements CustomPacketPayload {
    public record WardData(Identifier dimensionId, AABB bounds, String message) {
    }

    public static final Type<ApparitionWardsSyncS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "apparition_wards_sync"));

    public static final StreamCodec<ByteBuf, ApparitionWardsSyncS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ApparitionWardsSyncS2CPayload decode(ByteBuf buf) {
            int count = PacketCodecUtils.readBoundedCount(buf, 512, "apparition-wards");
            List<WardData> wards = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                Identifier dimensionId = Identifier.parse(PacketCodecUtils.readString(buf));
                double minX = buf.readDouble();
                double minY = buf.readDouble();
                double minZ = buf.readDouble();
                double maxX = buf.readDouble();
                double maxY = buf.readDouble();
                double maxZ = buf.readDouble();
                String message = PacketCodecUtils.readString(buf);
                wards.add(new WardData(dimensionId, new AABB(minX, minY, minZ, maxX, maxY, maxZ), message));
            }
            return new ApparitionWardsSyncS2CPayload(wards);
        }

        @Override
        public void encode(ByteBuf buf, ApparitionWardsSyncS2CPayload payload) {
            buf.writeInt(payload.wards.size());
            for (WardData ward : payload.wards) {
                PacketCodecUtils.writeString(buf, ward.dimensionId.toString());
                buf.writeDouble(ward.bounds.minX);
                buf.writeDouble(ward.bounds.minY);
                buf.writeDouble(ward.bounds.minZ);
                buf.writeDouble(ward.bounds.maxX);
                buf.writeDouble(ward.bounds.maxY);
                buf.writeDouble(ward.bounds.maxZ);
                PacketCodecUtils.writeString(buf, ward.message == null ? "" : ward.message);
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(ApparitionWardsSyncS2CPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            List<ClientApparitionWardState.WardBox> mapped = payload.wards.stream()
                    .map(w -> new ClientApparitionWardState.WardBox(w.dimensionId, w.bounds, w.message))
                    .toList();
            ClientApparitionWardState.setAll(mapped);
        });
    }

    public static void syncToPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, fromRegistry());
    }

    public static void syncToAllPlayers(MinecraftServer server) {
        ApparitionWardsSyncS2CPayload payload = fromRegistry();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private static ApparitionWardsSyncS2CPayload fromRegistry() {
        List<WardData> wards = new ArrayList<>();
        for (ApparitionWard ward : ApparitionWardRegistry.all()) {
            wards.add(new WardData(ward.dimensionId(), ward.bounds(), ward.blockMessage().getString()));
        }
        return new ApparitionWardsSyncS2CPayload(wards);
    }
}
