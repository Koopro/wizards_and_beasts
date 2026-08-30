package at.koopro.wizardsandbeasts.client.hud;

import at.koopro.wizardsandbeasts.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The gate every full-screen overlay in this mod opens with.
 *
 * <p>Three of them repeated it verbatim: bail out if the server operator turned screen effects off,
 * then bail out again if there is no player or no level to draw over. Getting either half wrong is
 * a crash on the title screen or an accessibility setting that silently does nothing, so it is one
 * call now.
 */
@NullMarked
public final class HudOverlays {

    private HudOverlays() {
    }

    /**
     * The player an overlay should draw for, or {@code null} when it must not draw at all —
     * {@code reduceScreenEffects} is on, or there is no player in a level yet.
     */
    public static @Nullable LocalPlayer overlayTarget() {
        if (Config.reduceScreenEffects) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        return (player == null || mc.level == null) ? null : player;
    }
}
