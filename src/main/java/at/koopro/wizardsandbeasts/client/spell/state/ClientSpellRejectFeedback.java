package at.koopro.wizardsandbeasts.client.spell.state;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.client.spell.SpellVfxClient;
import at.koopro.wizardsandbeasts.client.ui.UiStateProjection;
import at.koopro.wizardsandbeasts.spell.cast.SpellRejectCodes;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * What the client does with a refused cast.
 *
 * <p>The server sends a reject <em>code</em> and nothing else
 * ({@link at.koopro.wizardsandbeasts.network.spell.SpellDeniedS2CPayload}); everything a player
 * hears or reads about the refusal is decided here. That split is deliberate: the server stays the
 * only authority on <em>whether</em> a cast is legal, and the client owns <em>how</em> that answer is
 * presented — which is what makes the text translatable and lets the channel follow the player's HUD
 * settings instead of the server guessing at them.
 *
 * <h2>One channel, never two</h2>
 * A refusal is one sentence and must appear once. When the spell HUD is up the line is drawn beside
 * it, where the player is already looking; when it is not, the same line goes to the action bar. Codes
 * whose text is composed at the reject site — an unmet requirement describing itself, a Gamp violation's
 * lore line — resolve to {@code null} in {@link SpellRejectCodes#castRejectMessageKey} and are shown
 * here as sound only, so the site's richer sentence stands alone.
 *
 * <h2>Allocation</h2>
 * The {@link Component} is built once on receipt, not per frame. {@link #hudMessage()} and
 * {@link #hudAlpha()} are called from the HUD render path and allocate nothing.
 */
@NullMarked
public final class ClientSpellRejectFeedback {

    /** How long the HUD line stays up. Long enough to read mid-fight, short enough not to linger. */
    public static final long DISPLAY_MILLIS = 3_000L;
    /** Tail of {@link #DISPLAY_MILLIS} spent fading out. */
    public static final long FADE_MILLIS = 700L;

    @Nullable
    private static Component hudMessage;
    private static long shownAtMillis;

    private ClientSpellRejectFeedback() {}

    /** Entry point for {@code SpellDeniedS2CPayload}. Always audible; text only when there is text. */
    public static void onDenied(String reason) {
        SpellVfxClient.playDeniedFeedback();

        String key = SpellRejectCodes.castRejectMessageKey(reason);
        if (key == null) {
            // Site-owned or diagnostic: the sound is the whole of the client's contribution.
            return;
        }
        Component message = Component.translatable(key);

        Minecraft mc = Minecraft.getInstance();
        if (spellHudIsUp(mc)) {
            hudMessage = message;
            shownAtMillis = Util.getMillis();
        } else if (mc.player != null) {
            hudMessage = null;
            mc.player.displayClientMessage(message, true);
        }
    }

    /** The line the spell HUD should draw, or {@code null} when there is none or it has expired. */
    @Nullable
    public static Component hudMessage() {
        if (hudMessage == null) {
            return null;
        }
        if (elapsed() >= DISPLAY_MILLIS) {
            hudMessage = null;
            return null;
        }
        return hudMessage;
    }

    /** Opacity for {@link #hudMessage()}, 1 until the fade tail then falling to 0. */
    public static float hudAlpha() {
        long remaining = DISPLAY_MILLIS - elapsed();
        if (remaining >= FADE_MILLIS) {
            return 1.0f;
        }
        return remaining <= 0L ? 0.0f : remaining / (float) FADE_MILLIS;
    }

    /** Dropped on disconnect and on a full spell-data resync, so a stale refusal cannot survive one. */
    public static void clear() {
        hudMessage = null;
        shownAtMillis = 0L;
    }

    private static long elapsed() {
        return Util.getMillis() - shownAtMillis;
    }

    /**
     * Whether the spell HUD will actually draw this frame — the config toggle and the same visibility
     * policy {@code SpellDiamondOverlay} consults. Asked once, at receipt: a player who opens a screen
     * a tick later still gets to read the line they earned.
     */
    private static boolean spellHudIsUp(Minecraft mc) {
        if (!Config.showSpellHudOverlay || mc.player == null || mc.level == null) {
            return false;
        }
        return UiStateProjection.spellHud(mc).canRenderSpellHud();
    }
}
