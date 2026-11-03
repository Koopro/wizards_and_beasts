package at.koopro.wizardsandbeasts.client.item;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui;
import at.koopro.wizardsandbeasts.client.gui.ui.UiSurface;
import at.koopro.wizardsandbeasts.client.gui.ui.UiTooltips;
import at.koopro.wizardsandbeasts.item.trinket.HermionesBagMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.List;

import static at.koopro.wizardsandbeasts.item.trinket.HermionesBagMenu.GUI_W;
import static at.koopro.wizardsandbeasts.item.trinket.HermionesBagMenu.INV_X;
import static at.koopro.wizardsandbeasts.item.trinket.HermionesBagMenu.INV_Y;

/**
 * Hermione's beaded bag: you reach <em>into</em> it.
 *
 * <p>The bag is pink beads on rose silk with gilt piping, closed by a kiss-lock clasp across the top, and
 * its 54 slots sink into a deep rose satin lining -- the Undetectable Extension Charm's inside, not a chest.
 * The player's inventory is a parchment strip below it, so what is in the bag and what is on you are two
 * different things at a glance. It used to be vanilla's {@code generic_54} recoloured onto paper with a
 * row of beads in the title band: one of the mod's signature artefacts, opening as a double chest.
 *
 * <p>Art from {@code tools/gui_chrome.py} ({@code container/hermiones_bag/}); every frame is placed from
 * {@link HermionesBagMenu}'s own geometry, which its slots also use.
 */
public class HermionesBagScreen extends AbstractContainerScreen<HermionesBagMenu> {

    private static final Identifier FABRIC = art("fabric");
    private static final Identifier LINING = art("lining");
    private static final Identifier CLASP_BAR = art("clasp_bar");
    private static final Identifier CLASP_BALL = art("clasp_ball");

    /** The bag's body: from under the clasp to just over the parchment strip. */
    private static final int BAG_TOP = 6;
    private static final int BAG_BOTTOM = 144;
    /** The parchment strip the player's inventory sits on; the bag overlaps its top edge. */
    private static final int STRIP_TOP = 136;
    /** Clasp: a gilt bar across the middle of the top edge and two balls meeting at its centre. */
    private static final int CLASP_Y = 3;
    private static final int CLASP_INSET = 30;
    private static final int CLASP_BAR_W = 16;
    private static final int CLASP_BAR_H = 6;
    private static final int BALL = 7;
    /** The satin tag the bag's name is stitched on. */
    private static final int TAG_X = 10;
    private static final int TAG_Y = 10;
    private static final int TAG_H = 11;

    // The lining's colours, mirrored from gui_chrome.py's ROSE: the slot wells are drawn here, not in art.
    private static final int WELL_FACE = 0xFF2A0B23;
    private static final int WELL_SHADOW = 0xFF16040F;
    private static final int WELL_LIP = 0xFF6E2A58;
    private static final int TAG_FIELD = 0xFF2E0C26;
    /** A bead's pink: the ring on the slot holding the bag that is open. */
    private static final int BEAD = 0xFFE886BE;

    public HermionesBagScreen(HermionesBagMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = GUI_W;
        this.imageHeight = HermionesBagMenu.GUI_H;
        this.titleLabelX = TAG_X + 4;
        this.titleLabelY = TAG_Y + 2;
        this.inventoryLabelX = INV_X;
        this.inventoryLabelY = INV_Y - 11;
    }

    private static Identifier art(String name) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID,
                "textures/gui/container/hermiones_bag/" + name + ".png");
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        // The parchment strip first: the bag hangs over its top edge.
        Ui.panel(g, UiSurface.PAGE, x, y + STRIP_TOP, imageWidth, imageHeight - STRIP_TOP);

        McStylePanel.drawNineSliceTiled(g, FABRIC, x, y + BAG_TOP, imageWidth, BAG_BOTTOM - BAG_TOP,
                WizardsMetrics.PANEL_SPRITE_SIZE, WizardsMetrics.PANEL_SPRITE_BORDER);

        // The lining: two pixels round the bag's slot frames.
        int bagX = HermionesBagMenu.BAG_X - 3;
        int bagY = HermionesBagMenu.BAG_Y - 3;
        int bagW = HermionesBagMenu.COLS * 18 + 4;
        int bagH = HermionesBagMenu.ROWS * 18 + 4;
        McStylePanel.drawNineSliceTiled(g, LINING, x + bagX, y + bagY, bagW, bagH,
                WizardsMetrics.PANEL_SPRITE_SIZE, WizardsMetrics.PANEL_SPRITE_BORDER);

        renderClasp(g, x, y);

        int tagW = font.width(title) + 8;
        g.fill(x + TAG_X, y + TAG_Y, x + TAG_X + tagW, y + TAG_Y + TAG_H, TAG_FIELD);
        outline(g, x + TAG_X, y + TAG_Y, tagW, TAG_H, WizardsPalette.GILT_DARK);

        List<int[]> origins = HermionesBagMenu.slotOrigins();
        for (int i = 0; i < origins.size(); i++) {
            int[] o = origins.get(i);
            if (i < HermionesBagMenu.SIZE) {
                Ui.slot(g, x + o[0], y + o[1], WELL_FACE, WELL_SHADOW, WELL_LIP);
            } else {
                Ui.slot(g, x + o[0], y + o[1]);
            }
        }
        int pinned = menu.lockedSlotIndex();
        if (pinned >= 0) {
            Slot slot = menu.slots.get(pinned);
            Ui.slotTarget(g, x + slot.x, y + slot.y, 0, BEAD);
        }
    }

    /** The kiss-lock: a gilt bar tiled across the top edge, the two balls meeting at the centre. */
    private void renderClasp(GuiGraphics g, int x, int y) {
        int from = x + CLASP_INSET;
        int to = x + imageWidth - CLASP_INSET;
        for (int bx = from; bx < to; bx += CLASP_BAR_W) {
            int w = Math.min(CLASP_BAR_W, to - bx);
            g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, CLASP_BAR,
                    bx, y + CLASP_Y, 0.0F, 0.0F, w, CLASP_BAR_H, CLASP_BAR_W, CLASP_BAR_H);
        }
        int cx = x + imageWidth / 2;
        McStylePanel.drawTexture(g, CLASP_BALL, cx - BALL, y, BALL, BALL, BALL, BALL);
        McStylePanel.drawTexture(g, CLASP_BALL, cx, y, BALL, BALL, BALL, BALL);
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int colour) {
        g.fill(x, y, x + w, y + 1, colour);
        g.fill(x, y + h - 1, x + w, y + h, colour);
        g.fill(x, y + 1, x + 1, y + h - 1, colour);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, colour);
    }

    /**
     * The bag's name in cream on its satin tag (with the shadow light text needs on a dark ground); the
     * inventory label in ink on the parchment.
     */
    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, this.title, this.titleLabelX, this.titleLabelY, WizardsPalette.TEXT, true);
        g.drawString(font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
                WizardsPalette.PAGE_INK_2, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No renderBackground() here: the screen framework already ran it for this frame.
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
        renderPinnedHint(g, mouseX, mouseY);
    }

    /** Why the ringed hotbar slot will not move: it is the bag you are holding open. */
    private void renderPinnedHint(GuiGraphics g, int mouseX, int mouseY) {
        int pinned = menu.lockedSlotIndex();
        if (pinned < 0 || hoveredSlot != menu.slots.get(pinned)) {
            return;
        }
        UiTooltips.show(g, font, Component.translatable("gui.wizards_and_beasts.hermiones_bag.pinned"), mouseX, mouseY);
    }
}
