package at.koopro.wizardsandbeasts.client.hud;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.trinket.HeldSneakoscope;
import at.koopro.wizardsandbeasts.item.trinket.SneakoscopeItem;
import at.koopro.wizardsandbeasts.sneakoscope.SneakoscopeTuning;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * The red pulse at the edges of the screen when a held Sneakoscope hits six suspects or more.
 *
 * <p>Reserved for the top band on purpose. This is the one piece of Sneakoscope feedback the player
 * cannot look away from, so spending it on anything less than "leave the room" would spend it
 * permanently — after the third false alarm nobody reads a screen tint any more.
 *
 * <p>Edges only, never a full-screen wash: the middle of the screen is where you look for the way
 * out. Honours {@code reduceScreenEffects}, like every other tint layer in the mod.
 */
public final class SneakoscopeAlarmOverlay {

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "sneakoscope_alarm");

    /** Deep arterial red. Reads as danger at low alpha without turning the screen pink. */
    private static final int ALARM_RGB = 0x8B0F14;
    /** Widest the band gets, as a fraction of the shorter screen axis. */
    private static final float MAX_EDGE_FRACTION = 0.16f;
    /** Band width at the threshold, so the alarm arrives as a rim rather than a wall. */
    private static final float MIN_EDGE_FRACTION = 0.07f;

    private SneakoscopeAlarmOverlay() {}

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        LocalPlayer player = HudOverlays.overlayTarget();
        if (player == null) {
            return;
        }
        ItemStack stack = HeldSneakoscope.find(player);
        if (stack == null) {
            return;
        }
        int threats = SneakoscopeItem.threatCount(stack);
        float alpha = SneakoscopeTuning.alarmPulseAlpha(System.currentTimeMillis(), threats);
        if (alpha <= 0.0f) {
            return;
        }

        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        // Band width tracks the same pulse as the alpha, so the edges breathe in and out together
        // instead of a fixed frame simply changing colour.
        float swell = Mth.clamp(alpha / SneakoscopeTuning.MAX_ALARM_ALPHA, 0.0f, 1.0f);
        float fraction = MIN_EDGE_FRACTION + (MAX_EDGE_FRACTION - MIN_EDGE_FRACTION) * swell;
        int edge = Math.max(2, (int) (Math.min(w, h) * fraction));
        int colour = (Mth.clamp((int) (alpha * 255.0f), 0, 255) << 24) | ALARM_RGB;

        graphics.fill(0, 0, w, edge, colour);
        graphics.fill(0, h - edge, w, h, colour);
        graphics.fill(0, edge, edge, h - edge, colour);
        graphics.fill(w - edge, edge, w, h - edge, colour);
    }
}
