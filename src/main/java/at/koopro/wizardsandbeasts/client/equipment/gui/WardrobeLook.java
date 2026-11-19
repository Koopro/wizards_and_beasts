package at.koopro.wizardsandbeasts.client.equipment.gui;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.ui.FamilyIcons;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui;
import at.koopro.wizardsandbeasts.client.gui.ui.UiSurface;
import at.koopro.wizardsandbeasts.equipment.WardrobeLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/**
 * The wardrobe's look (UI renaissance P6, 2026-11-18): an open walnut armoire with the player in a gilt mirror, slot
 * pairs down both sides (outer = worn, inner = look), and the player's inventory on a parchment strip below, as under
 * Hermione's bag. Positions come from {@link WardrobeLayout}, which the server menu uses too.
 */
@NullMarked
public final class WardrobeLook {

    public static final int W = WardrobeLayout.W;
    public static final int H = WardrobeLayout.H;

    private static final int FRAME_BORDER = 8;
    private static final Identifier FRAME = art("frame");
    private static final Identifier BACK = art("back");
    private static final Identifier RAIL = art("rail");

    /** Walnut wells: the slot sunk into the armoire's wood. */
    private static final int WELL_FACE = 0xFF2A1A10;
    private static final int WELL_SHADOW = 0xFF120904;
    private static final int WELL_LIP = 0xFF6A4A2E;
    private static final int BRASS = 0xFFD4A84A;
    private static final int BRASS_DARK = 0xFF7A5418;
    private static final int BRASS_INK = 0xFF3A2808;
    private static final int GLASS_TOP = 0xFFB8C4C8;
    private static final int GLASS_BOTTOM = 0xFF6E7C84;

    private static final int MIRROR_X = 60;
    private static final int MIRROR_Y = 18;
    private static final int MIRROR_W = W - 2 * MIRROR_X;
    private static final int MIRROR_H = 106;
    /** The eye toggle's box, relative to the look slot's item origin. */
    public static final int EYE_DX = 12;
    public static final int EYE_DY = -2;
    public static final int EYE_W = 6;
    public static final int EYE_H = 5;

    private WardrobeLook() {}

    private static Identifier art(String name) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/gui/container/wardrobe/" + name + ".png");
    }

    /**
     * Everything but the items and the player.
     *
     * @param wornFilled per place: is something worn there (no empty hint then)
     * @param shown      per place: Curios' render toggle (open or shut eye), null to draw open eyes
     * @param sealed     per place: no Curios slot behind it (drawn dimmed, no eye)
     */
    public static void drawBackground(GuiGraphics g, Font font, int x, int y, Component title,
                                      boolean[] wornFilled, boolean @org.jspecify.annotations.Nullable [] shown,
                                      boolean[] sealed) {
        Ui.panel(g, UiSurface.PAGE, x, y + WardrobeLayout.STRIP_TOP, W, H - WardrobeLayout.STRIP_TOP);
        McStylePanel.drawNineSliceTiled(g, FRAME, x, y, W, WardrobeLayout.ARMOIRE_H, 32, FRAME_BORDER);
        McStylePanel.drawNineSliceTiled(g, BACK, x + FRAME_BORDER, y + FRAME_BORDER,
                W - 2 * FRAME_BORDER, WardrobeLayout.ARMOIRE_H - 2 * FRAME_BORDER, 32, 0);
        for (int rx = x + FRAME_BORDER; rx < x + W - FRAME_BORDER; rx += 16) {
            int w = Math.min(16, x + W - FRAME_BORDER - rx);
            g.blit(RenderPipelines.GUI_TEXTURED, RAIL, rx, y + FRAME_BORDER + 1, 0f, 0f, w, 8, 16, 8);
        }

        int plateW = font.width(title) + 10;
        int px = x + (W - plateW) / 2;
        g.fill(px, y + 1, px + plateW, y + 11, BRASS_DARK);
        g.fill(px + 1, y + 2, px + plateW - 1, y + 10, BRASS);
        g.drawString(font, title, px + 5, y + 2, BRASS_INK, false);

        for (int i = 0; i < WardrobeLayout.PLACES.size(); i++) {
            WardrobeLayout.Place place = WardrobeLayout.PLACES.get(i);
            int sy = y + place.y();
            boolean dead = i < sealed.length && sealed[i];
            for (int sx : new int[]{x + place.wornX(), x + place.lookX()}) {
                Ui.slot(g, sx, sy, WELL_FACE, WELL_SHADOW, WELL_LIP);
            }
            if (dead) {
                g.fill(x + place.wornX() - 1, sy - 1, x + place.wornX() + 17, sy + 17, 0x80000000);
                g.fill(x + place.lookX() - 1, sy - 1, x + place.lookX() + 17, sy + 17, 0x80000000);
                continue;
            }
            if (i >= wornFilled.length || !wornFilled[i]) {
                FamilyIcons.equipmentSlot(g, place.iconCell(), x + place.wornX(), sy, 0.45f);
            }
            eye(g, x + place.lookX() + EYE_DX, sy + EYE_DY, shown == null || i >= shown.length || shown[i]);
        }

        drawMirrorFrame(g, x + MIRROR_X, y + MIRROR_Y, MIRROR_W, MIRROR_H);

        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                Ui.slot(g, x + WardrobeLayout.INV_X + c * 18, y + WardrobeLayout.INV_Y + r * 18);
            }
        }
        for (int c = 0; c < 9; c++) {
            Ui.slot(g, x + WardrobeLayout.INV_X + c * 18, y + WardrobeLayout.HOTBAR_Y);
        }
    }

    /** A small brass eye: open (the piece is drawn on you) or shut (hidden). */
    private static void eye(GuiGraphics g, int ex, int ey, boolean open) {
        g.fill(ex - 1, ey - 1, ex + EYE_W + 1, ey + EYE_H + 1, BRASS_DARK);
        g.fill(ex, ey, ex + EYE_W, ey + EYE_H, 0xFF2A1A10);
        if (open) {
            g.fill(ex + 1, ey + 2, ex + EYE_W - 1, ey + 3, BRASS);
            g.fill(ex + 2, ey + 1, ex + EYE_W - 2, ey + 4, BRASS);
            g.fill(ex + 2, ey + 2, ex + 4, ey + 3, BRASS_INK);
        } else {
            g.fill(ex + 1, ey + 3, ex + EYE_W - 1, ey + 4, BRASS);
        }
    }

    /** A gilt frame with rounded shoulders around silvered glass that darkens downwards. */
    private static void drawMirrorFrame(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 3, y + 2, x + w + 3, y + h + 3, BRASS_DARK);
        g.fill(x - 2, y, x + w + 2, y + h + 2, BRASS_DARK);
        g.fill(x - 2, y + 1, x + w + 2, y + h + 1, BRASS);
        g.fill(x + 2, y - 2, x + w - 2, y, BRASS_DARK);
        g.fill(x + 2, y - 1, x + w - 2, y + 1, BRASS);
        for (int i = 0; i < h; i++) {
            float t = i / (float) Math.max(1, h - 1);
            g.fill(x + 1, y + 1 + i, x + w - 1, y + 2 + i, lerp(GLASS_TOP, GLASS_BOTTOM, t));
        }
        for (int i = 0; i < 10; i++) {
            g.fill(x + 6 + i, y + 16 - i, x + 7 + i, y + 17 - i, 0x60FFFFFF);
        }
    }

    /** The player standing in the mirror, following the pointer. */
    public static void drawReflection(GuiGraphics g, int x, int y, LocalPlayer player, float mouseX, float mouseY) {
        int mx = x + MIRROR_X;
        int my = y + MIRROR_Y;
        int scale = Math.round((MIRROR_H - 8) * 0.9f / 1.8f);
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, mx, my + 2, mx + MIRROR_W, my + MIRROR_H - 2,
                scale, 0.0625f, mouseX, mouseY, player);
    }

    /** Index of the place whose eye is under the pointer, or -1. */
    public static int eyeAt(int x, int y, double mouseX, double mouseY) {
        for (int i = 0; i < WardrobeLayout.PLACES.size(); i++) {
            WardrobeLayout.Place place = WardrobeLayout.PLACES.get(i);
            int ex = x + place.lookX() + EYE_DX;
            int ey = y + place.y() + EYE_DY;
            if (mouseX >= ex - 1 && mouseX < ex + EYE_W + 1 && mouseY >= ey - 1 && mouseY < ey + EYE_H + 1) {
                return i;
            }
        }
        return -1;
    }

    /** The name of the worn or look slot for {@code place}. */
    public static Component slotName(WardrobeLayout.Place place, boolean look) {
        return Component.translatable("gui.wizards_and_beasts.wardrobe.slot." + (look ? "look" : "worn"),
                Component.translatable("gui.wizards_and_beasts.wardrobe.part." + place.key()));
    }

    private static int lerp(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | (Math.round(ar + (br - ar) * t) << 16) | (Math.round(ag + (bg - ag) * t) << 8)
                | Math.round(ab + (bb - ab) * t);
    }
}
