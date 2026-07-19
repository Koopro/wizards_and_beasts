package at.koopro.wizardsandbeasts.network.apparition;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.apparition.ApparitionPoint;
import at.koopro.wizardsandbeasts.apparition.PlayerApparitionPoints;
import at.koopro.wizardsandbeasts.client.apparition.state.ClientApparitionPointsState;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * Pushes the player's memorised Apparition destinations to their own client so the selector can list them
 * without a round-trip. Sent on login/respawn and whenever the list changes.
 */
@NullMarked
public record ApparitionPointsSyncS2CPayload(List<ApparitionPoint> points) implements CustomPacketPayload {

    public static final Type<ApparitionPointsSyncS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "apparition_points_sync"));

    public static final StreamCodec<ByteBuf, ApparitionPointsSyncS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ApparitionPointsSyncS2CPayload decode(ByteBuf buf) {
            int count = PacketCodecUtils.readBoundedCount(buf, PlayerApparitionPoints.MAX_POINTS, "apparition-points");
            List<ApparitionPoint> points = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String name = PacketCodecUtils.readString(buf);
                ResourceKey<net.minecraft.world.level.Level> dimension =
                        ResourceKey.create(Registries.DIMENSION, Identifier.parse(PacketCodecUtils.readString(buf)));
                Vec3 position = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
                points.add(new ApparitionPoint(name, dimension, position, buf.readFloat()));
            }
            return new ApparitionPointsSyncS2CPayload(points);
        }

        @Override
        public void encode(ByteBuf buf, ApparitionPointsSyncS2CPayload payload) {
            int count = Math.min(payload.points.size(), PlayerApparitionPoints.MAX_POINTS);
            buf.writeInt(count);
            for (int i = 0; i < count; i++) {
                ApparitionPoint point = payload.points.get(i);
                PacketCodecUtils.writeString(buf, point.name());
                PacketCodecUtils.writeString(buf, point.dimension().identifier().toString());
                buf.writeDouble(point.position().x);
                buf.writeDouble(point.position().y);
                buf.writeDouble(point.position().z);
                buf.writeFloat(point.yaw());
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ApparitionPointsSyncS2CPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> ClientApparitionPointsState.accept(payload.points()));
    }

    public static void syncToPlayer(ServerPlayer player) {
        PlayerApparitionPoints data = player.getData(ModAttachments.APPARITION_POINTS.get());
        PacketDistributor.sendToPlayer(player, new ApparitionPointsSyncS2CPayload(data.points()));
    }
}
