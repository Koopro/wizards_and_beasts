package at.koopro.wizardsandbeasts.client.item;

import at.koopro.wizardsandbeasts.item.trinket.HermionesBagMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for Hermione's Bag.
 *
 * <p>This used to blit vanilla's {@code generic_54} directly, so one of the mod's signature
 * artefacts opened as a plain double chest. The sheet it draws now is that same panel
 * recoloured into the mod's leather and gold by {@code tools/gui_chrome.py}, with beads
 * along the title band — same geometry, so every slot still lands where the menu puts it.
 */
public class HermionesBagScreen extends AbstractContainerScreen<HermionesBagMenu> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            "wizards_and_beasts", "textures/gui/container/hermiones_bag.png");

    /** Height of the chest half of the sheet: the rows plus the panel's own top border. */
    private static final int CHEST_H = HermionesBagMenu.ROWS * 18 + 17;
    /** The sheet's player-inventory strip starts at v=126 and is 96 tall, whatever the row count. */
    private static final int INVENTORY_V = 126;
    private static final int INVENTORY_H = 96;
    private static final int SHEET = 256;

    public HermionesBagScreen(HermionesBagMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 222;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    /**
     * Two blits, exactly as {@code ContainerScreen} does it: {@code generic_54} holds six rows and a
     * player-inventory strip that always sits at v=126, so a container of any row count is assembled from
     * the top of the sheet plus that strip — the sheet is not one contiguous panel to stretch.
     *
     * <p>This used to be a single {@code blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight, 256, 256)}.
     * That is the 1.21.1 signature, and it no longer exists: the only pipeline-less overload in 1.21.11 is
     * {@code blit(Identifier, x0, y0, x1, y1, u0, u1, v0, v1)} with float UVs, which the old call bound to
     * silently because ints widen to floats. It resolved to a quad from the panel's corner back to the
     * screen origin with reversed, out-of-range UVs — the background did not merely sit wrong, it was
     * structurally meaningless.
     */
    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                x, y, 0.0F, 0.0F, this.imageWidth, CHEST_H, SHEET, SHEET);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                x, y + CHEST_H, 0.0F, INVENTORY_V, this.imageWidth, INVENTORY_H, SHEET, SHEET);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No renderBackground() here: the screen framework already ran it for this frame.
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }
}
