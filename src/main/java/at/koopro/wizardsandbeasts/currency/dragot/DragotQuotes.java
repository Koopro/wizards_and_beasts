package at.koopro.wizardsandbeasts.currency.dragot;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * The rate a teller is standing behind, right now, for one particular wizard.
 *
 * <p><b>Why a pinned quote rather than a roll at the till.</b> The brief asks for a rate that moves
 * so the exchange "feels alive". Rolling the variance at the moment the button is pressed would do
 * that — and would also mean the screen showed 0.82 and the transaction paid 0.78, which does not
 * read as a living market. It reads as the bank lying to you.
 *
 * <p>So the roll happens once, when the counter opens, and stands for
 * {@link DragotRates#QUOTE_LIFETIME_TICKS}. Inside that window the number on the screen is the number
 * you get. Come back in a minute and it has moved. That is a market; the other one is a bug report.
 *
 * <p>Server-side and authoritative. The client is told the quote so it can draw it, and is never
 * asked what the quote was.
 */
@NullMarked
public final class DragotQuotes {

    private static final PlayerScopedState<Pinned> PINNED = PlayerScopedState.create("dragot_quote");

    private DragotQuotes() {}

    private record Pinned(float rate, long expiresAtTick) {}

    /**
     * The rate this wizard is currently quoted, rolling a fresh one if none stands.
     *
     * <p>Idempotent inside the window, which is what lets display code and transaction code call it
     * without coordinating.
     */
    public static float rateFor(ServerPlayer player) {
        long now = player.level().getGameTime();
        Pinned held = PINNED.get(player);
        if (held != null && now < held.expiresAtTick()) {
            return held.rate();
        }
        float rolled = DragotRates.quotedRate(Config.dragotGalleonRate, player.getRandom().nextFloat());
        PINNED.put(player, new Pinned(rolled, now + DragotRates.QUOTE_LIFETIME_TICKS));
        return rolled;
    }

    /**
     * Forces the next {@link #rateFor} to roll again.
     *
     * <p>Called when the counter is opened, so walking away and coming back is what moves the rate —
     * rather than a player being able to stand at the till re-reading a screen until the spread
     * happens to favour them.
     */
    public static void refresh(ServerPlayer player) {
        PINNED.remove(player);
    }

    /** How far the standing quote has drifted from the configured base, as a percentage. */
    public static float driftPercent(ServerPlayer player) {
        return DragotRates.driftPercent(Config.dragotGalleonRate, rateFor(player));
    }
}
