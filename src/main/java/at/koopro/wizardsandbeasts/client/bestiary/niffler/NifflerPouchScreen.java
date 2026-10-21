package at.koopro.wizardsandbeasts.client.bestiary.niffler;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui;
import at.koopro.wizardsandbeasts.client.gui.ui.UiSurface;
import at.koopro.wizardsandbeasts.entity.niffler.NifflerPouchMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * A niffler's pouch: you reach into the creature's belly pocket.
 *
 * <p>Black velvet fur with a blue-grey sheen around the opening, a few stolen coins caught in it, and the
 * slots sunk into soft brown suede -- the same object-first treatment as Hermione's bag, from the animal
 * this time. The player's inventory is a parchment strip below, so pouch and pockets are two things at a
 * glance. An adult's pouch has three rows, a baby's one; the sheet is sized to the rows it holds.
 *
 * <p>Art from {@code tools/gui_chrome.py} ({@code container/niffler_pouch/}); every frame is placed from
 * {@link NifflerPouchMenu}'s own geometry, which its slots also use. It used to be two fixed 176px sheets
 * from {@code pouch_textures.py} whose wells had to be kept in step with the menu by hand.
 */
public class NifflerPouchScreen extends AbstractContainerScreen<NifflerPouchMenu> {

    private static final Identifier FUR = art("fur");
    private static final Identifier LINING = art("lining");

    /** The fur's top edge: the name tag overlaps it. */
    private static final int FUR_TOP = 4;
    private static final int TAG_X = 10;
    private static final int TAG_Y = 8;
    private static final int TAG_H = 12;

    // The lining's colours, mirrored from gui_chrome.py's NIFFLER: the slot wells are drawn here.
    private static final int WELL_FACE = 0xFF3E2716;
    private static final int WELL_SHADOW = 0xFF24160C;
    private static final int WELL_LIP = 0xFF7A5238;
    /** The tag is a coin's gold, lettered in the suede's darkest brown. */
    private static final int TAG_FIELD = 0xFFD4AF37;
    private static final int TAG_EDGE = 0xFF8F6522;
    private static final int TAG_INK = 0xFF24160C;

    private final int rows;

    public NifflerPouchScreen(NifflerPouchMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.rows = menu.pouchRows();
        this.imageWidth = NifflerPouchMenu.GUI_W;
        this.imageHeight = NifflerPouchMenu.guiHeight(rows);
        this.titleLabelX = TAG_X + 4;
        this.titleLabelY = TAG_Y + 2;
        this.inventoryLabelX = NifflerPouchMenu.INV_X;
        this.inventoryLabelY = NifflerPouchMenu.invY(rows) - 11;
    }

    private static Identifier art(String name) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID,
                "textures/gui/container/niffler_pouch/" + name + ".png");
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        int furBottom = NifflerPouchMenu.furBottom(rows);

        // The parchment strip first: the pouch's fur hangs over its top edge.
        int stripTop = furBottom - 8;
        Ui.panel(g, UiSurface.PAGE, x, y + stripTop, imageWidth, imageHeight - stripTop);
        McStylePanel.drawNineSliceTiled(g, FUR, x, y + FUR_TOP, imageWidth, furBottom - FUR_TOP,
                WizardsMetrics.PANEL_SPRITE_SIZE, WizardsMetrics.PANEL_SPRITE_BORDER);
        McStylePanel.drawNineSliceTiled(g, LINING, x + NifflerPouchMenu.POUCH_X - 3, y + NifflerPouchMenu.POUCH_Y - 3,
                9 * 18 + 4, rows * 18 + 4, WizardsMetrics.PANEL_SPRITE_SIZE, WizardsMetrics.PANEL_SPRITE_BORDER);

        int tagW = font.width(title) + 8;
        g.fill(x + TAG_X, y + TAG_Y, x + TAG_X + tagW, y + TAG_Y + TAG_H, TAG_EDGE);
        g.fill(x + TAG_X + 1, y + TAG_Y + 1, x + TAG_X + tagW - 1, y + TAG_Y + TAG_H - 1, TAG_FIELD);

        List<int[]> origins = NifflerPouchMenu.slotOrigins(rows);
        for (int i = 0; i < origins.size(); i++) {
            int[] o = origins.get(i);
            if (i < rows * 9) {
                Ui.slot(g, x + o[0], y + o[1], WELL_FACE, WELL_SHADOW, WELL_LIP);
            } else {
                Ui.slot(g, x + o[0], y + o[1]);
            }
        }
    }

    /** The name in dark brown on its gold tag; the inventory label in ink on the parchment. */
    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, this.title, this.titleLabelX, this.titleLabelY, TAG_INK, false);
        g.drawString(font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
                WizardsPalette.PAGE_INK_2, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No renderBackground() here: the screen framework already ran it for this frame.
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
    }
}
