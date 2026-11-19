package at.koopro.wizardsandbeasts.network.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
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

/**
 * Server-authoritative impact burst; renders tinted particles on tracking clients only.
 *
 * <p>{@code shake} carries the camera kick: true for a hit, false for the steady sparks of a held channel (Leviosa,
 * Aguamenti), which used to re-arm every onlooker's screen shake twenty times a second.
 */
public record SpellImpactBurstS2CPayload(
        double x,
        double y,
        double z,
        int argb,
        int familyOrdinal,
        int count,
        float spread,
        boolean shake
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
                    buf.readFloat(),
                    buf.readBoolean());
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
            buf.writeBoolean(pkt.shake);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Most a burst may ask for: the authoring cap ({@code SpellVfx.MAX_IMPACT_COUNT}). It was 256. */
    public static final int MAX_COUNT = at.koopro.wizardsandbeasts.spell.def.SpellVfx.MAX_IMPACT_COUNT;

    public static void sendToTracking(Entity anchor, Vec3 pos, SpellFamily family, int argb, int count, float spread) {
        sendToTracking(anchor, pos, family, argb, count, spread, true);
    }

    public static void sendToTracking(Entity anchor, Vec3 pos, SpellFamily family, int argb, int count, float spread,
                                      boolean shake) {
        int c = Math.min(MAX_COUNT, Math.max(1, count));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                anchor,
                new SpellImpactBurstS2CPayload(
                        pos.x, pos.y, pos.z,
                        argb,
                        family.ordinal(),
                        c,
                        spread,
                        shake));
    }

    public static void sendToTracking(ServerPlayer anchor, Vec3 pos, SpellFamily family, int argb, int count,
                                      float spread) {
        sendToTracking((Entity) anchor, pos, family, argb, count, spread);
    }
}
