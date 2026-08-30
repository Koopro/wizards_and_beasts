package at.koopro.wizardsandbeasts.client.gui.character;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/**
 * The Character Sheet's own art, and what to draw when it is not there.
 *
 * <h2>The art exists</h2>
 * This class used to carry {@code TODO: create ...} on every constant. All of them have since been
 * produced and are on disk at exactly the documented sizes, so the TODOs were telling the next reader
 * that a shipped screen was unfinished. A fourth constant, {@code BACKGROUND}, is gone: the sheet
 * moved to the shared nine-sliced theme panel, which is exact at any size, and the fixed 320x240
 * parchment it used to blit had no readers left.
 *
 * <h2>Why it draws instead of exposing identifiers</h2>
 * A texture path is only as good as the pack loaded over it. Any resource pack that overrides this
 * mod's namespace and omits a file — or any partial install — turns a missing PNG into the
 * black-and-magenta checkerboard across a whole row of tabs, which reads as a crash rather than as a
 * missing asset. So the drawing decision lives here: when the bespoke art resolves it is used, and
 * when it does not the shared themed control is drawn in its place. The sheet still looks deliberate,
 * in the same palette as every other screen in the mod, and nothing shows the checkerboard.
 *
 * <p>The fallback is not a lesser path kept for emergencies — it is the same {@code McStylePanel}
 * control the rest of the GUI is built from. Losing the bespoke art costs the sheet its own flavour,
 * not its usability.
 */
@NullMarked
public final class CharacterSheetTextures {

    /** 16x16 icon shown on the inventory tab button. */
    public static final Identifier ICON_TAB = Identifier.fromNamespaceAndPath(
            WizardsAndBeastsMod.MODID, "textures/gui/character_sheet/icon_tab.png");

    /** 24x24 nine-slice (3px corners) sub-panel for tabs/effect chips — inactive state. */
    public static final Identifier PANEL = Identifier.fromNamespaceAndPath(
            WizardsAndBeastsMod.MODID, "textures/gui/character_sheet/panel.png");

    /** 24x24 nine-slice (3px corners) sub-panel — active/hover state. */
    public static final Identifier PANEL_SEL = Identifier.fromNamespaceAndPath(
            WizardsAndBeastsMod.MODID, "textures/gui/character_sheet/panel_sel.png");

    public static final int PANEL_SIZE = 24;
    public static final int PANEL_BORDER = 3;

    /**
     * Whether the bespoke tab art resolved, cached rather than asked per frame.
     *
     * <p>Refreshed by {@link #refresh()} when the screen opens, which is often enough: a resource
     * reload while the sheet is already open is rare, and reopening it re-checks. Asking the resource
     * manager three times a frame for something that changes on a pack switch would be the wrong trade.
     */
    private static boolean bespokePanelsPresent = true;
    /** Same probe for the inventory tab icon, which lives on the vanilla inventory screen. */
    private static boolean iconTabPresent = true;

    private CharacterSheetTextures() {}

    /** Re-checks whether the bespoke art is present. Call from the screen's {@code init}. */
    public static void refresh() {
        Minecraft mc = Minecraft.getInstance();
        bespokePanelsPresent = mc.getResourceManager().getResource(PANEL).isPresent()
                && mc.getResourceManager().getResource(PANEL_SEL).isPresent();
        iconTabPresent = mc.getResourceManager().getResource(ICON_TAB).isPresent();
        StatIcons.refresh();
    }

    /**
     * Draws the inventory tab button's icon, or a small themed seal when the icon is not there.
     *
     * <p>This one matters more than the panels: the button sits on the vanilla inventory screen,
     * which every player opens constantly, so a missing 16x16 here is a magenta square in the corner
     * of the most-used screen in the game.
     */
    public static void drawTabIcon(GuiGraphics g, int x, int y, int size) {
        if (iconTabPresent) {
            McStylePanel.drawTexture(g, ICON_TAB, x, y, size, size, size, size);
            return;
        }
        McStylePanel.drawThemedInset(g, x, y, size, size);
    }

    /**
     * Draws one tab/chip panel — the bespoke nine-slice when it is available, the shared themed
     * control when it is not.
     */
    public static void drawPanel(GuiGraphics g, int x, int y, int w, int h,
                                 boolean selected, boolean hovered) {
        if (bespokePanelsPresent) {
            McStylePanel.drawNineSlice(g, selected || hovered ? PANEL_SEL : PANEL,
                    x, y, w, h, PANEL_SIZE, PANEL_BORDER);
            return;
        }
        // Always "active": a tab is a live control whether or not it is the one showing, and
        // ControlState.of maps inactive to DISABLED, which would grey out the selected tab.
        McStylePanel.drawThemedButton(g, x, y, w, h,
                McStylePanel.ControlState.of(true, selected || hovered));
    }
}
