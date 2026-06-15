package at.koopro.wizardsandbeasts.client.item;

import at.koopro.wizardsandbeasts.item.trinket.HermionesBagMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for Hermione's Bag. Reuses the vanilla {@code generic_54} double-chest texture so no
 * bespoke art asset is required.
 */
public class HermionesBagScreen extends AbstractContainerScreen<HermionesBagMenu> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("minecraft", "textures/gui/container/generic_54.png");

    public HermionesBagScreen(HermionesBagMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 222;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        graphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight, 256, 256);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }
}
