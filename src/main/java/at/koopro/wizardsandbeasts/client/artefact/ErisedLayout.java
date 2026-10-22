package at.koopro.wizardsandbeasts.client.artefact;

/**
 * Where the Mirror of Erised screen puts the mirror and its glass, in GUI pixels.
 *
 * <p>The art is drawn at its own size whenever it fits ({@code tools/gui_chrome.py}, {@code ERISED_*}). On a
 * screen too short for it -- a large GUI scale on a small window -- only the art is shrunk to fit; text is never
 * scaled, it wraps to the smaller glass instead.
 */
public record ErisedLayout(int x, int y, int w, int h, float scale, int glassX, int glassY, int glassW, int glassH,
                           int railX, int railY, int railW, int railH) {

    public static final int TEX_W = 192;
    public static final int TEX_H = 232;
    /** Glass and top rail in texture pixels, inclusive boxes as the generator draws them. */
    static final int GLASS_X0 = 20, GLASS_Y0 = 38, GLASS_X1 = 171, GLASS_Y1 = 199;
    static final int RAIL_Y0 = 18;
    static final int MARGIN = 6;

    public static ErisedLayout of(int screenW, int screenH) {
        float scale = Math.min(1.0f, Math.min((screenW - 2f * MARGIN) / TEX_W, (screenH - 2f * MARGIN) / TEX_H));
        scale = Math.max(scale, 0.25f);
        int w = Math.round(TEX_W * scale);
        int h = Math.round(TEX_H * scale);
        int x = (screenW - w) / 2;
        int y = (screenH - h) / 2;
        int gx = x + Math.round(GLASS_X0 * scale);
        int gy = y + Math.round(GLASS_Y0 * scale);
        int gw = Math.round((GLASS_X1 + 1 - GLASS_X0) * scale);
        int gh = Math.round((GLASS_Y1 + 1 - GLASS_Y0) * scale);
        int ry = y + Math.round(RAIL_Y0 * scale);
        return new ErisedLayout(x, y, w, h, scale, gx, gy, gw, gh, gx, ry, gw, gy - ry);
    }

    public boolean overRail(double mx, double my) {
        return mx >= railX && mx < railX + railW && my >= railY && my < railY + railH;
    }
}
