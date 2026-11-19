package at.koopro.wizardsandbeasts.network.pose;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/**
 * "This player is making this gesture, for this many ticks." Sent to the player and everyone tracking them.
 *
 * <p>Zero ticks means stop: the gesture lets go on the client's normal fade rather than cutting.
 *
 * @param entityId  the gesturing player
 * @param gestureId a {@link at.koopro.wizardsandbeasts.pose.PlayerGesture} id
 * @param ticks     how long it plays; zero to stop
 */
@NullMarked
public record PlayerGestureS2CPayload(int entityId, String gestureId, int ticks) implements CustomPacketPayload {

    public static final Type<PlayerGestureS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "player_gesture"));

    public static final StreamCodec<ByteBuf, PlayerGestureS2CPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PlayerGestureS2CPayload::entityId,
            ByteBufCodecs.STRING_UTF8, PlayerGestureS2CPayload::gestureId,
            ByteBufCodecs.VAR_INT, PlayerGestureS2CPayload::ticks,
            PlayerGestureS2CPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
