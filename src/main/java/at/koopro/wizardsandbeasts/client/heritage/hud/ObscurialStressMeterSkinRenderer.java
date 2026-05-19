package at.koopro.wizardsandbeasts.client.heritage.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Draws a compact full front portrait from the player's skin atlas.
 */
public final class ObscurialStressMeterSkinRenderer {
    private static final int SKIN_TEX_W = 64;
    private static final int SKIN_TEX_H = 64;
    private static final float SLOT_SCALE = 0.78f;
    private static final int SLOT_OFFSET_X = 1;
    private static final int SLOT_OFFSET_Y = -3;

    private ObscurialStressMeterSkinRenderer() {}

    public static void renderFrontPortrait(
            GuiGraphics graphics, AbstractClientPlayer player, int destX, int destY, int destW, int destH) {
        Identifier texture = player.getSkin().body().texturePath();

        // Front silhouette layout in skin units: 16 wide x 32 tall.
        float unit = Math.min(destW / 16.0f, destH / 32.0f) * SLOT_SCALE;
        int fullW = Math.max(1, Math.round(16.0f * unit));
        int fullH = Math.max(1, Math.round(32.0f * unit));
        int baseX = destX + (destW - fullW) / 2 + SLOT_OFFSET_X;
        int baseY = destY + (destH - fullH) / 2 + SLOT_OFFSET_Y;
        int u = Math.max(1, Math.round(unit));

        // Head
        blitPartWithOverlay(graphics, texture, baseX + (4 * u), baseY, 8 * u, 8 * u, 8, 8, 40, 8, 8, 8);
        // Body
        blitPartWithOverlay(graphics, texture, baseX + (4 * u), baseY + (8 * u), 8 * u, 12 * u, 20, 20, 20, 36, 8, 12);
        // Arms (screen-left uses left arm set, screen-right uses right arm set)
        blitPartWithOverlay(graphics, texture, baseX, baseY + (8 * u), 4 * u, 12 * u, 36, 52, 52, 52, 4, 12);
        blitPartWithOverlay(graphics, texture, baseX + (12 * u), baseY + (8 * u), 4 * u, 12 * u, 44, 20, 44, 36, 4, 12);
        // Legs (screen-left uses left leg set, screen-right uses right leg set)
        blitPartWithOverlay(graphics, texture, baseX + (4 * u), baseY + (20 * u), 4 * u, 12 * u, 20, 52, 4, 52, 4, 12);
        blitPartWithOverlay(graphics, texture, baseX + (8 * u), baseY + (20 * u), 4 * u, 12 * u, 4, 20, 4, 36, 4, 12);
    }

    private static void blitPartWithOverlay(
            GuiGraphics graphics,
            Identifier texture,
            int x,
            int y,
            int destW,
            int destH,
            int baseU,
            int baseV,
            int overlayU,
            int overlayV,
            int regionW,
            int regionH) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, (float) baseU, (float) baseV, destW, destH, regionW, regionH, SKIN_TEX_W, SKIN_TEX_H);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, (float) overlayU, (float) overlayV, destW, destH, regionW, regionH, SKIN_TEX_W, SKIN_TEX_H);
    }
}
