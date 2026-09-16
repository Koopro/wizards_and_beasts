package at.koopro.wizardsandbeasts.network.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NonNull;

/**
 * The state of a Shield Charm being charged, for the caster's own screen.
 *
 * <p>Sent only to the player doing the holding, and only while they hold: the vignette and the wand's
 * glow are private feedback about a decision in progress. Everyone else sees the charge through the
 * particles and the chimes the server already broadcasts.
 *
 * <p>There is no "charge ended" packet. The client treats the state as stale a few ticks after the
 * last one arrives, so a release, a death, a disconnect or a dropped packet all end it the same way
 * and none of them can leave a vignette painted on the screen.
 *
 * @param tier     the shape that would be raised right now
 * @param progress 0-1 towards the next shape; 1 when this caster can reach no further
 * @param planting true when releasing now would plant the dome instead of carrying it
 * @param capped   true when the hold has already reached this caster's ceiling
 */
public record ProtegoChargeS2CPayload(int tier, float progress, boolean planting, boolean capped)
        implements CustomPacketPayload {

    public static final Type<ProtegoChargeS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "protego_charge"));

    public static final StreamCodec<ByteBuf, ProtegoChargeS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ProtegoChargeS2CPayload decode(ByteBuf buf) {
            return new ProtegoChargeS2CPayload(buf.readInt(), buf.readFloat(), buf.readBoolean(), buf.readBoolean());
        }

        @Override
        public void encode(ByteBuf buf, ProtegoChargeS2CPayload pkt) {
            buf.writeInt(pkt.tier);
            buf.writeFloat(pkt.progress);
            buf.writeBoolean(pkt.planting);
            buf.writeBoolean(pkt.capped);
        }
    };

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendTo(ServerPlayer caster, int tier, float progress, boolean planting, boolean capped) {
        PacketDistributor.sendToPlayer(caster, new ProtegoChargeS2CPayload(tier, progress, planting, capped));
    }
}
