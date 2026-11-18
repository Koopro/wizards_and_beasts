package at.koopro.wizardsandbeasts.pose;

import at.koopro.wizardsandbeasts.network.pose.PlayerGestureS2CPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NullMarked;

/**
 * The one door for starting a {@link PlayerGesture}: server side, at the gameplay moment.
 *
 * <p>Presentation only. Nothing reads a gesture back, so a gesture can never gate or delay gameplay; a hook that
 * calls this has already done its work, or is about to do it on its own timer.
 *
 * <p>Sent to trackers <em>and</em> the player, as {@code SpellCastAnimationS2CPayload.send} does:
 * {@code sendToPlayersTrackingEntity} excludes the entity's own player.
 */
@NullMarked
public final class PlayerGestures {

    private PlayerGestures() {}

    public static void play(ServerPlayer player, PlayerGesture gesture) {
        play(player, gesture, gesture.ticks());
    }

    public static void play(ServerPlayer player, PlayerGesture gesture, int ticks) {
        send(player, new PlayerGestureS2CPayload(player.getId(), gesture.id(), Math.max(1, ticks)));
    }

    /** Lets a running gesture go early (an interrupted ritual). */
    public static void stop(ServerPlayer player, PlayerGesture gesture) {
        send(player, new PlayerGestureS2CPayload(player.getId(), gesture.id(), 0));
    }

    private static void send(ServerPlayer player, PlayerGestureS2CPayload payload) {
        PacketDistributor.sendToPlayersTrackingEntity(player, payload);
        PacketDistributor.sendToPlayer(player, payload);
    }
}
