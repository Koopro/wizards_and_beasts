package at.koopro.wizardsandbeasts.feedback;

import at.koopro.wizardsandbeasts.network.feedback.NotifyS2CPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The one way this mod talks to a player, and the one place the choice of channel is made.
 *
 * <p>There were 347 message calls before this existed and effectively one channel: chat. That was
 * noisy during play, and outright broken for anything triggered from inside a screen — chat draws
 * <em>under</em> an open GUI, so clicking a locked skill node produced feedback nobody could see.
 *
 * <p>Three channels, and the distinction is about how long the player needs the message, not about
 * how important it is:
 *
 * <ul>
 *   <li>{@link #toast} — a discrete event worth a few seconds: an unlock, a discovery, a refusal with
 *       a reason. Survives an open screen. Repeats of the same kind and title replace rather than
 *       stack, so an event that fires in a loop cannot build a wall.</li>
 *   <li>{@link #actionBar} — an in-the-moment transient during play: a cast rejection, a cooldown.
 *       One slot only; whatever is written last wins, so this suits things that are worth saying
 *       right now and worthless a second later.</li>
 *   <li>{@link #chat} — command output. A command response is a transcript the player asked for and
 *       may want to scroll back to, which is the one thing chat is genuinely good at.</li>
 * </ul>
 *
 * <p>Gameplay code should not call {@code displayClientMessage} directly any more.
 */
@NullMarked
public final class PlayerFeedback {

    private PlayerFeedback() {}

    public static void toast(Player player, NoticeKind kind, Component title) {
        toast(player, kind, title, null);
    }

    public static void toast(Player player, NoticeKind kind, Component title, @Nullable Component body) {
        if (player instanceof ServerPlayer serverPlayer) {
            NotifyS2CPayload.send(serverPlayer, kind, title, body);
        }
    }

    /** Convenience for the commonest pair: an action refused, plus why. */
    public static void refuse(Player player, Component title, @Nullable Component reason) {
        toast(player, NoticeKind.FAIL, title, reason);
    }

    public static void unlocked(Player player, Component title, @Nullable Component detail) {
        toast(player, NoticeKind.UNLOCK, title, detail);
    }

    public static void actionBar(Player player, Component message) {
        player.displayClientMessage(message, true);
    }
}
