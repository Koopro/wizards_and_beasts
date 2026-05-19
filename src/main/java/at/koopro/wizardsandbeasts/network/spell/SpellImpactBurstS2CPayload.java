package at.koopro.wizardsandbeasts.network.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.spell.SpellVfxClient;
import at.koopro.wizardsandbeasts.spell.core.SpellFamily;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server-authoritative impact burst; renders tinted particles on tracking clients only.
 */
public record SpellImpactBurstS2CPayload(
        double x,
        double y,
        double z,
        int argb,
        int familyOrdinal,
        int count,
        float spread
) implements CustomPacketPayload {

    public static final Type<SpellImpactBurstS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "spell_impact_burst"));

    public static final StreamCodec<ByteBuf, SpellImpactBurstS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SpellImpactBurstS2CPayload decode(ByteBuf buf) {
            return new SpellImpactBurstS2CPayload(
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readInt(),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    buf.readFloat());
        }

        @Override
        public void encode(ByteBuf buf, SpellImpactBurstS2CPayload pkt) {
            buf.writeDouble(pkt.x);
            buf.writeDouble(pkt.y);
            buf.writeDouble(pkt.z);
            buf.writeInt(pkt.argb);
            ByteBufCodecs.VAR_INT.encode(buf, pkt.familyOrdinal);
            ByteBufCodecs.VAR_INT.encode(buf, pkt.count);
            buf.writeFloat(pkt.spread);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(SpellImpactBurstS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Vec3 pos = new Vec3(pkt.x, pkt.y, pkt.z);
            SpellFamily family = SpellFamily.byOrdinal(pkt.familyOrdinal);
            SpellVfxClient.spawnTintBurst(pos, family, pkt.argb, Math.max(1, pkt.count), pkt.spread);
        });
    }

    public static void sendToTracking(Entity anchor, Vec3 pos, SpellFamily family, int argb, int count, float spread) {
        int c = Math.min(256, Math.max(1, count));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                anchor,
                new SpellImpactBurstS2CPayload(
                        pos.x, pos.y, pos.z,
                        argb,
                        family.ordinal(),
                        c,
                        spread));
    }

    public static void sendToTracking(ServerPlayer anchor, Vec3 pos, SpellFamily family, int argb, int count,
                                      float spread) {
        sendToTracking((Entity) anchor, pos, family, argb, count, spread);
    }
}
