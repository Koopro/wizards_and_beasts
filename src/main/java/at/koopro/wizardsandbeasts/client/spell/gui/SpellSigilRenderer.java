package at.koopro.wizardsandbeasts.client.spell.gui;

import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NullMarked;

/**
 * The wand diamond's engraving: channels from a hub to the four sockets, the accent ring of a filled
 * socket, the proficiency pips and the drag thread. The socket bezels themselves are sprites
 * ({@code NightChrome.socket}).
 *
 * <h2>Why a diamond is worth keeping</h2>
 *
 * <p>It mirrors {@code SpellDiamondOverlay} on the HUD, so the shape you edit is the shape you cast
 * from. It also makes the wiring cheap: with the sockets at up/right/down/left every channel from the
 * hub is <b>axis-aligned</b>, so the whole engraving is filled rectangles rather than a rasterised
 * diagonal.
 *
 * <h2>Colour</h2>
 *
 * <p>Cut into the night sheet: a void groove, unlit channels a step above the cloth, gilt where the
 * metal is. The one foreign hue is a filled socket's channel and ring, which take the spell's category
 * colour (contrast-clamped by the caller) -- the loadout is what this screen edits, so it carries the
 * accent.
 */
@NullMarked
public final class SpellSigilRenderer {

    /** The groove channels and the hub are cut into: the night sheet's void. */
    private static final int GROOVE = 0xFF07060A;
    /** An unlit channel, an empty pip: one step above the cloth, visible without competing with gilt. */
    static final int UNLIT = 0xFF2F2838;

    /** How far the idle shimmer moves the gilt, as a fraction. Small on purpose -- it is engraving. */
    private static final float PULSE_DEPTH = 0.18f;
    private static final float PULSE_PERIOD_TICKS = 70.0f;

    private SpellSigilRenderer() {}

    /**
     * A stepped ring. Minecraft's grid has no circles, and a rasterised one at this radius reads as a
     * lumpy blob; an octagon cut from filled rectangles reads as a deliberate engraved ring.
     */
    public static void ring(GuiGraphics g, int x, int y, int size, int thickness, int colour) {
        int inset = Math.max(1, size / 5);
        g.fill(x + inset, y, x + size - inset, y + thickness, colour);
        g.fill(x + inset, y + size - thickness, x + size - inset, y + size, colour);
        g.fill(x, y + inset, x + thickness, y + size - inset, colour);
        g.fill(x + size - thickness, y + inset, x + size, y + size - inset, colour);
        int step = Math.max(1, inset / 2);
        g.fill(x + step, y + step, x + inset, y + inset, colour);
        g.fill(x + size - inset, y + step, x + size - step, y + inset, colour);
        g.fill(x + step, y + size - inset, x + inset, y + size - step, colour);
        g.fill(x + size - inset, y + size - inset, x + size - step, y + size - step, colour);
    }

    /**
     * One channel from the hub to a socket.
     *
     * @param lit true once the socket it feeds holds a spell -- an empty socket's channel stays dark,
     *            so the diamond reads at a glance as "two of four wired"
     */
    public static void leyLine(GuiGraphics g, int hubX, int hubY, int socketCx, int socketCy,
                               boolean lit, int accent, float ageInTicks) {
        int live = lit ? pulse(accent, ageInTicks) : UNLIT;
        if (hubY == socketCy) {
            int x0 = Math.min(hubX, socketCx);
            int x1 = Math.max(hubX, socketCx);
            g.fill(x0, hubY - 2, x1, hubY + 2, GROOVE);
            g.fill(x0, hubY - 1, x1, hubY + 1, live);
        } else {
            int y0 = Math.min(hubY, socketCy);
            int y1 = Math.max(hubY, socketCy);
            g.fill(hubX - 2, y0, hubX + 2, y1, GROOVE);
            g.fill(hubX - 1, y0, hubX + 1, y1, live);
        }
    }

    /** The hub the channels run to. Drawn after them so the lines tuck under it. */
    public static void hub(GuiGraphics g, int cx, int cy, int radius, float ageInTicks) {
        g.fill(cx - radius, cy - radius, cx + radius, cy + radius, GROOVE);
        ring(g, cx - radius, cy - radius, radius * 2, 1, WizardsPalette.GILT_DARK);
        int inner = Math.max(1, radius / 2);
        g.fill(cx - inner, cy - inner, cx + inner, cy + inner, pulse(WizardsPalette.GILT, ageInTicks));
    }

    /** The accent ring a filled socket wears inside its bezel, in its spell's category colour. */
    public static void seat(GuiGraphics g, int x, int y, int size, int accent, float ageInTicks) {
        ring(g, x + 5, y + 5, size - 10, 1, pulse(accent, ageInTicks));
    }

    /** A slow brightness wander, so the gilt and the lit channels are never quite static. */
    private static int pulse(int argb, float ageInTicks) {
        float t = (Mth.sin(ageInTicks / PULSE_PERIOD_TICKS * Mth.TWO_PI) + 1.0f) * 0.5f;
        float k = 1.0f - PULSE_DEPTH + PULSE_DEPTH * t;
        int a = (argb >>> 24) & 0xFF;
        int r = Mth.clamp((int) (((argb >> 16) & 0xFF) * k), 0, 255);
        int gg = Mth.clamp((int) (((argb >> 8) & 0xFF) * k), 0, 255);
        int b = Mth.clamp((int) ((argb & 0xFF) * k), 0, 255);
        return (a << 24) | (r << 16) | (gg << 8) | b;
    }

    /** The thread that follows a dragged spell, drawn from where it was picked up to the cursor. */
    public static void dragThread(GuiGraphics g, int fromX, int fromY, int toX, int toY, int accent) {
        int steps = 12;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int px = Math.round(Mth.lerp(t, fromX, toX));
            int py = Math.round(Mth.lerp(t, fromY, toY));
            // Motes rather than a solid line: a straight rule between two moving points looks like a
            // UI error, a dotted trail looks like something being drawn out of the book.
            if (i % 2 == 0) {
                g.fill(px, py, px + 1, py + 1, accent);
            }
        }
    }
}
