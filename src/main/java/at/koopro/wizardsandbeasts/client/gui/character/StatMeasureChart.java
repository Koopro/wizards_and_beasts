package at.koopro.wizardsandbeasts.client.gui.character;

import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import net.minecraft.client.gui.GuiGraphics;
import org.jspecify.annotations.NullMarked;

/**
 * "The Measure": the five stats as an inked pentagon on the Attributes page.
 *
 * <p>One spoke per {@code PlayerStat}, Power at the top and the rest clockwise in declaration order. Guide rings at a
 * quarter, half, three quarters and full; the character's own shape is filled with a faint gilt wash and ruled in ink,
 * and each tip carries a dot in that stat's {@link StatStyle#ink}. Everything is drawn in whole GUI pixels with
 * {@code fill}, so it scales like the rest of the sheet's pixel art and needs no texture.
 *
 * <p>The geometry ({@link #point}) is pure so it can be tested without a client.
 */
@NullMarked
public final class StatMeasureChart {

    /** Guide ring fractions. */
    private static final float[] RINGS = {0.25f, 0.5f, 0.75f, 1f};
    private static final int GUIDE = (0x70 << 24) | (WizardsPalette.PAGE_DEEP & 0xFFFFFF);
    private static final int SPOKE = (0x50 << 24) | (WizardsPalette.PAGE_DEEP & 0xFFFFFF);
    private static final int SHAPE_FILL = (0x40 << 24) | (WizardsPalette.GILT & 0xFFFFFF);
    private static final int SHAPE_LINE = WizardsPalette.PAGE_INK_2;

    private StatMeasureChart() {}

    /** The point at {@code fraction} (0..1) along spoke {@code i} of {@code n}, spoke 0 straight up. Returns {x, y}. */
    public static int[] point(int cx, int cy, int radius, int i, int n, float fraction) {
        double angle = -Math.PI / 2 + 2 * Math.PI * i / n;
        float f = Math.max(0f, Math.min(1f, fraction));
        return new int[]{
                cx + (int) Math.round(Math.cos(angle) * radius * f),
                cy + (int) Math.round(Math.sin(angle) * radius * f)};
    }

    /**
     * Draws the guides and the character's shape. {@code fractions[i]} is stat {@code i} over its maximum;
     * {@code inks[i]} its ink.
     */
    public static void draw(GuiGraphics g, int cx, int cy, int radius, float[] fractions, int[] inks) {
        int n = fractions.length;
        // Guides: rings and spokes.
        for (float ring : RINGS) {
            for (int i = 0; i < n; i++) {
                int[] a = point(cx, cy, radius, i, n, ring);
                int[] b = point(cx, cy, radius, (i + 1) % n, n, ring);
                line(g, a[0], a[1], b[0], b[1], GUIDE);
            }
        }
        for (int i = 0; i < n; i++) {
            int[] tip = point(cx, cy, radius, i, n, 1f);
            line(g, cx, cy, tip[0], tip[1], SPOKE);
        }
        // The character's shape. A zero stat still gets a sliver so the shape never collapses to a line.
        int[][] shape = new int[n][];
        for (int i = 0; i < n; i++) {
            shape[i] = point(cx, cy, radius, i, n, Math.max(0.04f, fractions[i]));
        }
        fillPolygon(g, shape, SHAPE_FILL);
        for (int i = 0; i < n; i++) {
            int[] a = shape[i];
            int[] b = shape[(i + 1) % n];
            line(g, a[0], a[1], b[0], b[1], SHAPE_LINE);
        }
        for (int i = 0; i < n; i++) {
            int[] p = shape[i];
            g.fill(p[0] - 1, p[1] - 1, p[0] + 2, p[1] + 2, inks[i]);
        }
    }

    /** A one-pixel ink line (Bresenham), in GUI pixels. */
    public static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        while (true) {
            g.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) {
                return;
            }
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    /** Scanline fill of a simple polygon, one {@code fill} per covered row span. */
    static void fillPolygon(GuiGraphics g, int[][] pts, int color) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int[] p : pts) {
            minY = Math.min(minY, p[1]);
            maxY = Math.max(maxY, p[1]);
        }
        int n = pts.length;
        double[] xs = new double[n];
        for (int y = minY; y <= maxY; y++) {
            double sy = y + 0.5;
            int count = 0;
            for (int i = 0; i < n; i++) {
                int[] a = pts[i];
                int[] b = pts[(i + 1) % n];
                if ((a[1] <= sy && b[1] > sy) || (b[1] <= sy && a[1] > sy)) {
                    xs[count++] = a[0] + (sy - a[1]) * (b[0] - a[0]) / (double) (b[1] - a[1]);
                }
            }
            java.util.Arrays.sort(xs, 0, count);
            for (int k = 0; k + 1 < count; k += 2) {
                int from = (int) Math.round(xs[k]);
                int to = (int) Math.round(xs[k + 1]);
                if (to > from) {
                    g.fill(from, y, to, y + 1, color);
                }
            }
        }
    }
}
