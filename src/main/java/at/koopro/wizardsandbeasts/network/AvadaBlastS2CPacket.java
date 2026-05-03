package at.koopro.wizardsandbeasts.network;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> Client one-shot Avada visual blast.
 * Fired only when the server confirms Avada killed its target.
 */
public record AvadaBlastS2CPacket(
        double startX,
        double startY,
        double startZ,
        double endX,
        double endY,
        double endZ) implements CustomPacketPayload {

    private static final int AVADA_RGB = 0x00FF00;
    public static final Type<AvadaBlastS2CPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "avada_blast"));

    public static final StreamCodec<ByteBuf, AvadaBlastS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AvadaBlastS2CPacket decode(ByteBuf buf) {
            return new AvadaBlastS2CPacket(
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble());
        }

        @Override
        public void encode(ByteBuf buf, AvadaBlastS2CPacket pkt) {
            buf.writeDouble(pkt.startX);
            buf.writeDouble(pkt.startY);
            buf.writeDouble(pkt.startZ);
            buf.writeDouble(pkt.endX);
            buf.writeDouble(pkt.endY);
            buf.writeDouble(pkt.endZ);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(AvadaBlastS2CPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return;
            }

            Vec3 from = new Vec3(pkt.startX, pkt.startY, pkt.startZ);
            Vec3 to = new Vec3(pkt.endX, pkt.endY, pkt.endZ);
            Vec3 delta = to.subtract(from);
            double distance = delta.length();
            if (distance < 0.01) {
                return;
            }
            Vec3 dir = delta.scale(1.0 / distance);

            int steps = Math.max(10, (int) (distance * 14.0));
            for (int i = 0; i <= steps; i++) {
                double t = i / (double) steps;
                Vec3 p = from.lerp(to, t);
                double jitter = 0.025 + mc.level.random.nextDouble() * 0.03;
                double dx = (mc.level.random.nextDouble() - 0.5) * jitter;
                double dy = (mc.level.random.nextDouble() - 0.5) * jitter;
                double dz = (mc.level.random.nextDouble() - 0.5) * jitter;
                mc.level.addParticle(
                        new DustParticleOptions(AVADA_RGB, 1.25f),
                        p.x, p.y, p.z, dx, dy, dz);
            }

            for (int i = 0; i < 20; i++) {
                Vec3 burst = to.add(
                        (mc.level.random.nextDouble() - 0.5) * 0.8,
                        (mc.level.random.nextDouble() - 0.5) * 0.8,
                        (mc.level.random.nextDouble() - 0.5) * 0.8);
                Vec3 velocity = dir.scale(0.02 + mc.level.random.nextDouble() * 0.05)
                        .add(
                                (mc.level.random.nextDouble() - 0.5) * 0.08,
                                (mc.level.random.nextDouble() - 0.5) * 0.08,
                                (mc.level.random.nextDouble() - 0.5) * 0.08);
                mc.level.addParticle(
                        new DustParticleOptions(AVADA_RGB, Mth.lerp(mc.level.random.nextFloat(), 0.9f, 1.5f)),
                        burst.x, burst.y, burst.z,
                        velocity.x, velocity.y, velocity.z);
            }
        });
    }

    public static void sendToTracking(ServerPlayer caster, Vec3 from, Vec3 to) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                caster,
                new AvadaBlastS2CPacket(from.x, from.y, from.z, to.x, to.y, to.z));
    }
}
