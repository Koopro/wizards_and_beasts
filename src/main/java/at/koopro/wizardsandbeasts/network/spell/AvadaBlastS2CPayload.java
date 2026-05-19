package at.koopro.wizardsandbeasts.network.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.spell.SpellVfxClient;
import at.koopro.wizardsandbeasts.spell.core.SpellFamily;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> Client one-shot Avada visual blast.
 * Fired only when the server confirms Avada killed its target.
 */
public record AvadaBlastS2CPayload(
        double startX,
        double startY,
        double startZ,
        double endX,
        double endY,
        double endZ) implements CustomPacketPayload {

    private static final int AVADA_ARGB = 0xFF00FF00;
    public static final Type<AvadaBlastS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "avada_blast"));

    public static final StreamCodec<ByteBuf, AvadaBlastS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AvadaBlastS2CPayload decode(ByteBuf buf) {
            return new AvadaBlastS2CPayload(
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble());
        }

        @Override
        public void encode(ByteBuf buf, AvadaBlastS2CPayload pkt) {
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

    public static void handleClient(AvadaBlastS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return;
            }

            Vec3 from = new Vec3(pkt.startX, pkt.startY, pkt.startZ);
            Vec3 to = new Vec3(pkt.endX, pkt.endY, pkt.endZ);
            SpellVfxClient.spawnTintBeam(from, to, SpellFamily.DARK, AVADA_ARGB);
            SpellVfxClient.spawnTintBurst(to, SpellFamily.DARK, AVADA_ARGB, 20, 0.4f);
        });
    }

    public static void sendToTracking(ServerPlayer caster, Vec3 from, Vec3 to) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                caster,
                new AvadaBlastS2CPayload(from.x, from.y, from.z, to.x, to.y, to.z));
    }
}
