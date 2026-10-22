package at.koopro.wizardsandbeasts.client.trinket.gui;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

/**
 * The two-way mirror's materials: an engraved pewter frame, silvered glass (light for the call, dark
 * behind the face it shows), breath-fog, an engraved plaque, pewter buttons ({@code sprites/mirror/}).
 * Art from {@code tools/gui_chrome.py} ({@code artefact/mirror/}).
 *
 * <p>Inks are slate, chosen against the light glass (#A9B4C0): {@link #INK} 7.3 : 1, {@link #INK_2} 4.9 : 1.
 */
final class MirrorChrome {

    /** Folder of the pewter button sprites, for {@code ThemedButton.skinned}. */
    static final String BUTTONS = "mirror";
    /** Frame width up to where the glass begins: the sprite's outer seven rows. */
    static final int FRAME = 7;
    static final int INK = 0xFF1E2630;
    static final int INK_2 = 0xFF36414E;
    static final int INK_OFF = 0xFF5A6572;

    private static final Identifier FRAME_TEX = tex("frame");
    private static final Identifier GLASS = tex("glass");
    private static final Identifier GLASS_DARK = tex("glass_dark");
    private static final Identifier FOG = tex("fog");
    private static final Identifier PLAQUE = tex("plaque");
    private static final int GLASS_TILE = 64;

    private MirrorChrome() {}

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/gui/artefact/mirror/" + name + ".png");
    }

    /** The whole mirror: glass inside, then the frame over its edge. */
    static void mirror(GuiGraphics g, int x, int y, int w, int h, boolean dark) {
        McStylePanel.drawTiled(g, dark ? GLASS_DARK : GLASS, x + FRAME, y + FRAME, w - 2 * FRAME, h - 2 * FRAME, GLASS_TILE);
        frame(g, x, y, w, h);
    }

    static void frame(GuiGraphics g, int x, int y, int w, int h) {
        McStylePanel.drawNineSliceTiled(g, FRAME_TEX, x, y, w, h,
                WizardsMetrics.PANEL_SPRITE_SIZE, WizardsMetrics.PANEL_SPRITE_BORDER);
    }

    /** A patch of breath on the glass, soft at its edges. */
    static void fog(GuiGraphics g, int x, int y, int w, int h) {
        McStylePanel.drawNineSliceTiled(g, FOG, x, y, w, h,
                WizardsMetrics.PANEL_SPRITE_SIZE, WizardsMetrics.PANEL_SPRITE_BORDER);
    }

    static void plaque(GuiGraphics g, int x, int y, int w, int h) {
        McStylePanel.drawNineSliceTiled(g, PLAQUE, x, y, w, h,
                WizardsMetrics.PANEL_SPRITE_SIZE, WizardsMetrics.PANEL_SPRITE_BORDER);
    }

    /** An engraved line across the glass: a dark cut with its lit lip under it. */
    static void rule(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, 0xC03B4048);
        g.fill(x, y + 1, x + w, y + 2, 0x80E6ECF2);
    }
}
