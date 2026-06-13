package at.koopro.wizardsandbeasts.network.debug;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NullMarked;

/**
 * Server -> Client: applies a beam-render performance preset on the executing player's client.
 * Sent by {@code /wandb debug beam preset <low|medium|high>}; beam rendering settings are
 * client-side state, so the server only relays the chosen preset name.
 */
@NullMarked
public record BeamPresetS2CPayload(String presetName) implements CustomPacketPayload {

    public static final Type<BeamPresetS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "beam_preset"));

    public static final StreamCodec<ByteBuf, BeamPresetS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BeamPresetS2CPayload decode(ByteBuf buf) {
            return new BeamPresetS2CPayload(PacketCodecUtils.readString(buf));
        }

        @Override
        public void encode(ByteBuf buf, BeamPresetS2CPayload payload) {
            PacketCodecUtils.writeString(buf, payload.presetName);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendToPlayer(ServerPlayer player, String presetName) {
        PacketDistributor.sendToPlayer(player, new BeamPresetS2CPayload(presetName));
    }
}
