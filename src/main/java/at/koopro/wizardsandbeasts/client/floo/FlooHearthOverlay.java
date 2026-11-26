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
 * What you see while you stand in a green Floo fire: the glow from below and the flames licking up the view.
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

        // Standing in the fire (remade 2026-11-26: the four green bands read as a frame of boxes): the green glow
        // rising from below, and the hearth's own flames licking up the bottom of the view, two rows out of step so
        // the edge of the fire never sits still. The middle stays clear: the room you are leaving, and the list.
        graphics.fillGradient(0, h / 3, w, h, argb(0.0f, 0x0F5A24), argb(intensity * 0.55f, 0x1A8A36));
        graphics.fillGradient(0, 0, w, h / 5, argb(intensity * 0.25f, 0x0F5A24), argb(0.0f, 0x0F5A24));
        long frame = System.currentTimeMillis() / 50L;
        int big = Math.max(16, h / 5);
        for (int row = 0; row < 2; row++) {
            int size = row == 0 ? big : big * 2 / 3;
            float alpha = intensity * (row == 0 ? 0.85f : 0.95f);
            for (int x = -size / 2 + row * size / 3, i = 0; x < w; x += size * 3 / 4, i++) {
                int f = (int) Math.floorMod(frame + i * 7L + row * 13L, FIRE_FRAMES);
                // Each tongue a little lower or higher, so the top of the fire is ragged, not a straight line.
                int lift = (int) (Math.sin(i * 1.7 + row) * size * 0.15);
                graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, FIRE[(i + row) & 1],
                        x, h - size + size / 6 - lift, 0.0f, f * 16.0f, size, size, 16, 16, 16, 16 * FIRE_FRAMES,
                        argb(alpha, 0xFFFFFF));
            }
        }
    }

    private static final Identifier[] FIRE = {
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/gui/floo/floor_fire_0.png"),
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/gui/floo/floor_fire_1.png")};
    private static final int FIRE_FRAMES = 32;

    private static int argb(float alpha, int rgb) {
        return (Mth.clamp(Math.round(alpha * 255.0f), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static boolean inFlooFire(Minecraft mc, LocalPlayer player) {
        if (mc.level == null) {
            return false;
        }
        BlockPos feet = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-2, -1, -2), feet.offset(2, 0, 2))) {
            if (at.koopro.wizardsandbeasts.floo.FlooHearths.isGreen(mc.level, pos)
                    && mc.level.getBlockEntity(pos) instanceof at.koopro.wizardsandbeasts.block.floo.FlooGrateBlockEntity grate
                    && at.koopro.wizardsandbeasts.floo.FlooHearths.inOpening(grate, player.position())) {
                return true;
            }
        }
        return false;
    }
}
