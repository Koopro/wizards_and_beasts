package at.koopro.wizardsandbeasts.client.floo;

import at.koopro.wizardsandbeasts.client.hud.HudOverlays;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * The green wash over the world while you are standing in a lit Floo hearth.
 *
 * <h2>Why it draws nothing at all when you are not</h2>
 * <p>This is the only feedback that a wizard is <em>in</em> the fire rather than beside it, and that
 * distinction now decides real things: it is what the departure window is measured against, and
 * stepping out of it cancels a hop. Without a visible edge to the fire, "you stepped out" is a
 * message about a boundary the player was never shown.
 *
 * <h2>No packet</h2>
 * <p>Read straight off the client's own blockstate. The flames are an ordinary block and the client
 * already knows where they are, so a server-driven "you are in fire" flag would be a packet per tick
 * per traveller to tell the client something it is holding in its hand. It also means the overlay
 * cannot desync: it is true exactly as often as the block is there.
 *
 * <p>Both the feet and the eyes are checked. A player standing in the flame block and a player whose
 * head is in it from the hearth's own opening are equally in the fire, and testing one position would
 * flicker the wash off every time they crouched.
 */
public final class FlooHearthOverlay {

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "floo_hearth");

    /** How fast the wash comes up and goes down, in units per frame at 60fps. */
    private static final float FADE_STEP = 0.12f;

    /** Deep hearth green for the full-screen wash: {@code FlooCues.EMERALD} at a third value. */
    private static final int WASH_RGB = 0x0E5A22;

    /** The Floo green itself, as bare RGB — a GUI fill packs its own alpha in the top byte. */
    private static final int EDGE_RGB = at.koopro.wizardsandbeasts.floo.FlooCues.EMERALD & 0x00FFFFFF;

    private static float intensity = 0.0f;

    private FlooHearthOverlay() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        LocalPlayer player = HudOverlays.overlayTarget();
        if (player == null) {
            intensity = 0.0f;
            return;
        }
        Minecraft mc = Minecraft.getInstance();

        // Eased rather than switched, so walking into a grate is a swell of green and not a single
        // frame where the screen changes colour. The target is binary; only the approach to it is not.
        float target = inFlooFire(mc, player) ? 1.0f : 0.0f;
        intensity = Mth.approach(intensity, target, FADE_STEP);
        if (intensity <= 0.01f) {
            return;
        }

        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

        // A wash rather than a full-strength fill: the player has to keep being able to read the room
        // they are about to leave, and the destination screen draws on top of this.
        int washAlpha = (int) (intensity * 70);
        graphics.fill(0, 0, w, h, (washAlpha << 24) | WASH_RGB);

        // Vignette, built from four edge bands rather than a texture. The bands are what make it read
        // as being inside something looking out, which a flat tint alone never does.
        int band = Math.max(8, Math.min(w, h) / 8);
        int edgeAlpha = (int) (intensity * 120);
        int edge = (edgeAlpha << 24) | EDGE_RGB;
        graphics.fill(0, 0, w, band, edge);
        graphics.fill(0, h - band, w, h, edge);
        graphics.fill(0, 0, band, h, edge);
        graphics.fill(w - band, 0, w, h, edge);
    }

    private static boolean inFlooFire(Minecraft mc, LocalPlayer player) {
        BlockPos feet = player.blockPosition();
        if (isFlames(mc, feet)) {
            return true;
        }
        return isFlames(mc, BlockPos.containing(player.getX(), player.getEyeY(), player.getZ()));
    }

    private static boolean isFlames(Minecraft mc, BlockPos pos) {
        return mc.level != null && mc.level.getBlockState(pos).is(ModBlocks.FLOO_FLAMES.get());
    }
}
