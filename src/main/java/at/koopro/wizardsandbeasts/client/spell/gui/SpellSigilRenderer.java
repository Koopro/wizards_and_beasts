package at.koopro.wizardsandbeasts.client.spell.gui;

import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NullMarked;

/**
 * The rune-socket plate: four sockets on a diamond, wired to a hub by engraved ley-lines.
 *
 * <p>Replaces four flat squares in a row of empty panel. The loadout is the thing this screen exists
 * to edit, and it was the least interesting object on it.
 *
 * <h2>Why a diamond is worth keeping</h2>
 *
 * <p>It mirrors {@code SpellDiamondOverlay} on the HUD, so the shape you edit is the shape you cast
 * from. It also happens to make the wiring cheap: with the sockets at up/right/down/left, every
 * ley-line from the hub is <b>axis-aligned</b>, so the whole engraving is filled rectangles rather
 * than a rasterised diagonal.
 *
 * <h2>Colour comes from the palette</h2>
 *
 * <p>Ink and gilt on the page, per {@code WizardsPalette} — engraved into the sheet rather than set
 * into a leather plate. The one place a foreign hue is allowed in is the socket ring of a filled
 * slot, which takes its spell's category colour (contrast-clamped by the caller); that is the
 * screen's whole job, so it is what should carry the accent.
 */
@NullMarked
public final class SpellSigilRenderer {

    /** The engraved groove every channel, socket and the hub is cut into. */
    private static final int GROOVE = WizardsPalette.PAGE_SHADE;
    /** An unlit channel, an empty socket's ring and the hub's ring: ink laid thin. */
    private static final int ENGRAVING = WizardsPalette.PAGE_INK_2;

    /** How far the idle shimmer moves the gilt, as a fraction. Small on purpose — it is engraving. */
    private static final float PULSE_DEPTH = 0.18f;
    private static final float PULSE_PERIOD_TICKS = 70.0f;

    private SpellSigilRenderer() {}

    /**
     * A stepped ring. Minecraft's grid has no circles, and a rasterised one at this radius reads as a
     * lumpy blob; an octagon cut from filled rectangles reads as a deliberate engraved ring.
     */
    public static void ring(GuiGraphics g, int x, int y, int size, int thickness, int colour) {
        int inset = Math.max(1, size / 5);
        // Top and bottom bars, held off the corners.
        g.fill(x + inset, y, x + size - inset, y + thickness, colour);
        g.fill(x + inset, y + size - thickness, x + size - inset, y + size, colour);
        // Left and right bars.
        g.fill(x, y + inset, x + thickness, y + size - inset, colour);
        g.fill(x + size - thickness, y + inset, x + size, y + size - inset, colour);
        // The four chamfers, one step each, which is what turns a square into an octagon.
        int step = Math.max(1, inset / 2);
        g.fill(x + step, y + step, x + inset, y + inset, colour);
        g.fill(x + size - inset, y + step, x + size - step, y + inset, colour);
        g.fill(x + step, y + size - inset, x + inset, y + size - step, colour);
        g.fill(x + size - inset, y + size - inset, x + size - step, y + size - step, colour);
    }

    /**
     * One engraved channel from the hub to a socket.
     *
     * @param lit true once the socket it feeds holds a spell — an empty socket's channel stays thin
     *            ink, so the plate reads at a glance as "two of four wired"
     */
    public static void leyLine(GuiGraphics g, int hubX, int hubY, int socketCx, int socketCy,
                               boolean lit, int accent, float ageInTicks) {
        int seat = GROOVE;
        int live = lit ? pulse(accent, ageInTicks) : WizardsPalette.PAGE_DEEP;
        int half = 1;

        if (hubY == socketCy) {
            int x0 = Math.min(hubX, socketCx);
            int x1 = Math.max(hubX, socketCx);
            g.fill(x0, hubY - half - 1, x1, hubY + half + 1, seat);
            g.fill(x0, hubY - half, x1, hubY + half, live);
        } else {
            int y0 = Math.min(hubY, socketCy);
            int y1 = Math.max(hubY, socketCy);
            g.fill(hubX - half - 1, y0, hubX + half + 1, y1, seat);
            g.fill(hubX - half, y0, hubX + half, y1, live);
        }
    }

    /**
     * The hub the channels run to. Drawn last of the engraving so the lines tuck under it.
     */
    public static void hub(GuiGraphics g, int cx, int cy, int radius, float ageInTicks) {
        g.fill(cx - radius, cy - radius, cx + radius, cy + radius, GROOVE);
        ring(g, cx - radius, cy - radius, radius * 2, 1, ENGRAVING);
        int inner = Math.max(1, radius / 2);
        g.fill(cx - inner, cy - inner, cx + inner, cy + inner,
                pulse(WizardsPalette.GILT, ageInTicks));
    }

    /**
     * Proficiency as three gilt pips around the socket's foot, replacing the ○/◉/★ glyph run.
     *
     * <p>A shape you can count beats a symbol you have to learn, and it survives the pixel grid at
     * this size where a star does not.
     */
    public static void proficiencyPips(GuiGraphics g, int x, int y, int size, int filled) {
        int pip = Math.max(2, size / 10);
        int gap = pip + 1;
        int total = 3 * pip + 2 * (gap - pip);
        int px = x + (size - total) / 2;
        int py = y + size + 1;
        for (int i = 0; i < 3; i++) {
            g.fill(px + i * gap, py, px + i * gap + pip, py + pip,
                    i < filled ? WizardsPalette.GILT_DARK : WizardsPalette.PAGE_DEEP);
        }
    }

    /**
     * The socket itself: a groove in the page, an ink ring, and — when filled — that ring re-struck
     * in the spell's category colour so the plate can be read by hue alone.
     */
    public static void socket(GuiGraphics g, int x, int y, int size,
                              boolean filled, boolean hovered, boolean dropTarget,
                              int accent, float ageInTicks) {
        g.fill(x + 2, y + 2, x + size - 2, y + size - 2, GROOVE);

        int ringColour = filled ? accent : ENGRAVING;
        if (dropTarget) {
            ringColour = WizardsPalette.GILT;
        } else if (hovered) {
            ringColour = WizardsPalette.GILT_DARK;
        }
        ring(g, x, y, size, 2, ringColour);

        if (filled) {
            // A second, dimmer ring one step in reads as the socket being seated rather than empty.
            ring(g, x + 3, y + 3, size - 6, 1, pulse(accent, ageInTicks));
        } else {
            // An empty socket needs to read as *available*, not as a hole cut in the page. A single
            // gilt stud at the centre is enough to say "something seats here" — without it the four
            // wells look like damage.
            int stud = Math.max(1, size / 12);
            g.fill(x + size / 2 - stud, y + size / 2 - stud,
                    x + size / 2 + stud, y + size / 2 + stud, WizardsPalette.GILT_DARK);
        }
        if (dropTarget) {
            // Flare outward, so the target is obvious while something is over it.
            ring(g, x - 2, y - 2, size + 4, 1, WizardsPalette.GILT_DARK);
        }
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
