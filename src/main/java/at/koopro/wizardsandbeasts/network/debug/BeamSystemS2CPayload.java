package at.koopro.wizardsandbeasts.network.debug;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NullMarked;

/**
 * Server -> Client: picks which beam renderer draws — the entity-based {@code client.beam} system
 * or the legacy immediate-mode one. Sent by {@code /wandb beam system}.
 *
 * <p>Relayed rather than flipped through a static like {@code debugForceBeam}: which renderer runs
 * is client-side state, and a static set on the server only reaches the client when they share a
 * JVM. That works in singleplayer and silently does nothing on a dedicated server.
 */
@NullMarked
public record BeamSystemS2CPayload(boolean useNewSystem) implements CustomPacketPayload {

    public static final Type<BeamSystemS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "beam_system"));

    public static final StreamCodec<ByteBuf, BeamSystemS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BeamSystemS2CPayload decode(ByteBuf buf) {
            return new BeamSystemS2CPayload(buf.readBoolean());
        }

        @Override
        public void encode(ByteBuf buf, BeamSystemS2CPayload payload) {
            buf.writeBoolean(payload.useNewSystem);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendToPlayer(ServerPlayer player, boolean useNewSystem) {
        PacketDistributor.sendToPlayer(player, new BeamSystemS2CPayload(useNewSystem));
    }
}
