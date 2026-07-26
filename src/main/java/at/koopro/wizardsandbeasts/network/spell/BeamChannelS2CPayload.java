package at.koopro.wizardsandbeasts.network.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server -> Client: a wand beam channel is running (or has stopped) for one caster.
 *
 * <p>Before this, the client inferred channels from {@code player.isUsingItem()} plus its own copy
 * of the active spell — which only ever existed for the local player, so other players' beams were
 * invisible. The server already knows exactly when a channel starts and ends, so it says so.
 *
 * <p>When {@code active} is false, {@code spellId} and {@code range} carry nothing; the caster id is
 * all the client needs to tear the beam down.
 *
 * @param casterId the caster's entity id — the client looks the entity up, so no position is sent
 * @param spellId  active spell, so the client can resolve colour and shape itself
 * @param range    the reach the server resolved (spell range x wand range stat), in blocks; sent
 *                 rather than recomputed so the drawn beam cannot disagree with where damage lands
 */
public record BeamChannelS2CPayload(
        int casterId,
        String spellId,
        float range,
        boolean active) implements CustomPacketPayload {

    public static final Type<BeamChannelS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "beam_channel"));

    public static final StreamCodec<ByteBuf, BeamChannelS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BeamChannelS2CPayload decode(ByteBuf buf) {
            int casterId = buf.readInt();
            boolean active = buf.readBoolean();
            if (!active) {
                return new BeamChannelS2CPayload(casterId, "", 0f, false);
            }
            // normalizeIdentifier: a spell id off the wire is only ever fed to a registry lookup,
            // and anything that is not a plain identifier can never match one.
            String spellId = PacketCodecUtils.normalizeIdentifier(PacketCodecUtils.readString(buf));
            return new BeamChannelS2CPayload(casterId, spellId, buf.readFloat(), true);
        }

        @Override
        public void encode(ByteBuf buf, BeamChannelS2CPayload pkt) {
            buf.writeInt(pkt.casterId);
            buf.writeBoolean(pkt.active);
            if (!pkt.active) {
                return;
            }
            PacketCodecUtils.writeString(buf, pkt.spellId);
            buf.writeFloat(pkt.range);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Announce a running channel. Re-sent periodically, not just on the first tick, so a player who
     * only starts tracking the caster mid-channel still gets a beam; the client treats a repeat for
     * a caster it already draws as a no-op.
     */
    public static void sendStart(ServerPlayer caster, String spellId, float range) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                caster, new BeamChannelS2CPayload(caster.getId(), spellId, range, true));
    }

    public static void sendEnd(ServerPlayer caster) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                caster, new BeamChannelS2CPayload(caster.getId(), "", 0f, false));
    }
}
