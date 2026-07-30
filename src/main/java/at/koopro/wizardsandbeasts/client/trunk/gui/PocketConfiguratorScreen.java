package at.koopro.wizardsandbeasts.client.trunk.gui;

import at.koopro.wizardsandbeasts.network.trunk.PocketConfigC2SPayload;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.trunk.PocketBiomes;
import at.koopro.wizardsandbeasts.trunk.gui.PocketConfiguratorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public class PocketConfiguratorScreen extends AbstractContainerScreen<PocketConfiguratorMenu> {

    private int localRadius;
    private int localBiomeIndex;

    public PocketConfiguratorScreen(PocketConfiguratorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 130;
    }

    @Override
    protected void init() {
        super.init();
        localRadius = menu.getRadius();
        localBiomeIndex = menu.getBiomeIndex();

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        addRenderableWidget(Button.builder(Component.literal("-"),
                        b -> localRadius = Math.max(8, localRadius - 1))
                .bounds(x + 36, y + 32, 18, 14).build());
        addRenderableWidget(Button.builder(Component.literal("+"),
                        b -> localRadius = Math.min(32, localRadius + 1))
                .bounds(x + 88, y + 32, 18, 14).build());

        addRenderableWidget(Button.builder(Component.literal("◄"),
                        b -> localBiomeIndex = (localBiomeIndex - 1 + PocketBiomes.SELECTABLE.size()) % PocketBiomes.SELECTABLE.size())
                .bounds(x + 10, y + 58, 18, 14).build());
        addRenderableWidget(Button.builder(Component.literal("►"),
                        b -> localBiomeIndex = (localBiomeIndex + 1) % PocketBiomes.SELECTABLE.size())
                .bounds(x + 148, y + 58, 18, 14).build());

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.wizards_and_beasts.pocket_configurator.apply"),
                        b -> applyAndClose())
                .bounds(x + 56, y + 96, 64, 18).build());
    }

    private void applyAndClose() {
        String biomeKey = PocketBiomes.SELECTABLE.get(localBiomeIndex);
        ClientPacketDistributor.sendToServer(
                new PocketConfigC2SPayload(menu.getConfigurator().getBlockPos(), localRadius, biomeKey));
        onClose();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xC0000000 | (WizardsPalette.INK & 0x00FFFFFF));
        g.fill(x + 4, y + 4, x + imageWidth - 4, y + imageHeight - 4, WizardsPalette.PLATE);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        g.drawCenteredString(font,
                Component.translatable("gui.wizards_and_beasts.pocket_configurator.title"),
                x + imageWidth / 2, y + 10, WizardsPalette.BRASS_HI);

        g.drawString(font,
                Component.translatable("gui.wizards_and_beasts.pocket_configurator.size"),
                x + 12, y + 22, WizardsPalette.TEXT_DIM, false);
        g.drawCenteredString(font, String.valueOf(localRadius), x + 64, y + 34, WizardsPalette.TEXT);

        g.drawString(font,
                Component.translatable("gui.wizards_and_beasts.pocket_configurator.biome"),
                x + 12, y + 48, WizardsPalette.TEXT_DIM, false);
        String biomeName = PocketBiomes.displayName(PocketBiomes.SELECTABLE.get(localBiomeIndex));
        g.drawCenteredString(font, biomeName, x + imageWidth / 2, y + 62, WizardsPalette.BRASS);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {}
}
