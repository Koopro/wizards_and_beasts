package at.koopro.wizardsandbeasts.client.gui.ui;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/**
 * The night sheet's sprites ({@code gui/sprites/night/}, from {@code tools/gui_parchment.py}).
 *
 * <p>The dark material of the system screens: bookcloth the colour of the night card, a gilt bezel lit
 * from the top-left, an amethyst in each corner clasp. Components reach it through
 * {@link UiSurface#NIGHT_SHEET} ({@link Ui#panel}, {@link Ui#inset}, {@link Ui#divider}, {@link Ui#tab});
 * the pieces only this material has -- wand sockets, direction chevrons, the selected button face --
 * are drawn from here directly.
 *
 * <p>Panels and wells tile on 64/8 like the parchment kit, so the cloth's grain never stretches.
 * Sockets and chevrons are drawn 1:1: a pixel-art bezel scaled by a fraction is a smear.
 */
@NullMarked
public final class NightChrome {

    public static final String FOLDER = "night";
    /** Socket sprite edge, drawn at native size. */
    public static final int SOCKET = 30;
    /** Direction chevron edge. */
    public static final int ARROW = 7;
    public static final int ORNAMENT_W = 13;
    public static final int ORNAMENT_H = 7;

    private static final Identifier PANEL = sprite("panel");
    private static final Identifier INSET = sprite("panel_inset");
    private static final Identifier DIVIDER = sprite("divider");
    private static final Identifier ORNAMENT = sprite("ornament");
    private static final Identifier BUTTON = sprite("button");
    private static final Identifier BUTTON_HOVER = sprite("button_hover");
    private static final Identifier BUTTON_OFF = sprite("button_off");
    private static final Identifier BUTTON_SELECT = sprite("button_select");
    private static final Identifier SOCKET_IDLE = sprite("socket");
    private static final Identifier SOCKET_HOVER = sprite("socket_hover");
    private static final Identifier SOCKET_TARGET = sprite("socket_target");
    /** Indexed like the wand loadout and the HUD diamond: up, right, down, left. */
    private static final Identifier[] ARROWS = {
            sprite("arrow_up"), sprite("arrow_right"), sprite("arrow_down"), sprite("arrow_left")};

    private NightChrome() {}

    private static Identifier sprite(String name) {
        return McStylePanel.skinSprite(FOLDER, name);
    }

    /** The raised sheet. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        McStylePanel.drawNineSliceTiled(g, PANEL, x, y, w, h,
                WizardsMetrics.PANEL_SPRITE_SIZE, WizardsMetrics.PANEL_SPRITE_BORDER);
    }

    /** A recess in it: lists, viewports, text fields. */
    public static void inset(GuiGraphics g, int x, int y, int w, int h) {
        McStylePanel.drawNineSliceTiled(g, INSET, x, y, w, h,
                WizardsMetrics.PANEL_SPRITE_SIZE, WizardsMetrics.PANEL_SPRITE_BORDER);
    }

    /** The gilt rule, stretched to {@code w}; rows only, so the stretch is exact. Returns the y below it. */
    public static int divider(GuiGraphics g, int x, int y, int w) {
        McStylePanel.drawTexture(g, DIVIDER, x, y, w, WizardsMetrics.DIVIDER_H,
                WizardsMetrics.BAR_SPRITE_LENGTH, WizardsMetrics.DIVIDER_H);
        return y + WizardsMetrics.DIVIDER_H;
    }

    /** The lozenge laid on a rule, centred on {@code cx}; {@code ruleY} is the y the divider was drawn at. */
    public static void ornament(GuiGraphics g, int cx, int ruleY) {
        McStylePanel.drawTexture(g, ORNAMENT, cx - ORNAMENT_W / 2, ruleY, ORNAMENT_W, ORNAMENT_H,
                ORNAMENT_W, ORNAMENT_H);
    }

    /**
     * A button face. {@code selected} is the arcane face of a chosen tab or filter and wins over hover,
     * so a picked filter does not look unpicked while the mouse is on it.
     */
    public static void button(GuiGraphics g, int x, int y, int w, int h,
                              McStylePanel.ControlState state, boolean selected) {
        Identifier face = selected ? BUTTON_SELECT : switch (state) {
            case HOVER -> BUTTON_HOVER;
            case DISABLED -> BUTTON_OFF;
            case NORMAL -> BUTTON;
        };
        McStylePanel.drawNineSlice(g, face, x, y, w, h,
                WizardsMetrics.BUTTON_SPRITE_SIZE, WizardsMetrics.BUTTON_SPRITE_BORDER);
    }

    /**
     * A wand socket at native size. The bezel shows the state; the spell's ring and icon are the
     * caller's. {@link UiSlotState#DROP_OK} adds an arcane rim inside a bright bezel, so a drop target
     * differs from a hovered socket in shape as well as brightness.
     */
    public static void socket(GuiGraphics g, int x, int y, UiSlotState state) {
        Identifier frame = switch (state) {
            case DROP_OK -> SOCKET_TARGET;
            case HOVER, SELECTED -> SOCKET_HOVER;
            default -> SOCKET_IDLE;
        };
        McStylePanel.drawTexture(g, frame, x, y, SOCKET, SOCKET, SOCKET, SOCKET);
    }

    /** The chevron for loadout direction {@code slot} (0 up, 1 right, 2 down, 3 left). */
    public static void arrow(GuiGraphics g, int slot, int x, int y) {
        McStylePanel.drawTexture(g, ARROWS[Math.floorMod(slot, 4)], x, y, ARROW, ARROW, ARROW, ARROW);
    }
}
