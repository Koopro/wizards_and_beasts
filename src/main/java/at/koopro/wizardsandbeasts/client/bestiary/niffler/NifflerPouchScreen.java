package at.koopro.wizardsandbeasts.client.bestiary.niffler;

import at.koopro.wizardsandbeasts.entity.niffler.NifflerPouchMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class NifflerPouchScreen extends AbstractContainerScreen<NifflerPouchMenu> {

    private static final Identifier ADULT_TEXTURE = Identifier.fromNamespaceAndPath(
            "wizards_and_beasts", "textures/gui/niffler_pouch.png");
    private static final Identifier BABY_TEXTURE = Identifier.fromNamespaceAndPath(
            "wizards_and_beasts", "textures/gui/baby_niffler_pouch.png");

    public NifflerPouchScreen(NifflerPouchMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        if (menu.isBabyPouch()) {
            this.imageWidth = 176;
            this.imageHeight = 133;
        } else {
            this.imageWidth = 176;
            this.imageHeight = 166;
        }
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        Identifier texture = menu.isBabyPouch() ? BABY_TEXTURE : ADULT_TEXTURE;
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        graphics.blit(texture, x, y, 0, 0, this.imageWidth, this.imageHeight, 256, 256);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No renderBackground() here: the screen framework already ran it for this frame.
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }
}
