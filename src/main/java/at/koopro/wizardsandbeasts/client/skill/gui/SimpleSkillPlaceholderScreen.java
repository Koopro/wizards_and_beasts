package at.koopro.wizardsandbeasts.client.skill.gui;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.ScreenLayoutScaler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class SimpleSkillPlaceholderScreen extends Screen {
    private final Component sectionText;
    private final Component placeholderText;
    private final Component hintText;
    private ScreenLayoutScaler layout;

    protected SimpleSkillPlaceholderScreen(Component title, Component sectionText, Component placeholderText, Component hintText) {
        super(title);
        this.sectionText = sectionText;
        this.placeholderText = placeholderText;
        this.hintText = hintText;
    }

    @Override
    protected void init() {
        super.init();
        layout = ScreenLayoutScaler.forScreen(width, height, 330, 200);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderMenuBackground(graphics);
        int panelW = layout.panelW();
        int panelH = layout.panelH();
        int x = layout.panelX();
        int y = layout.panelY();
        int cx = this.width / 2;
        int cy = this.height / 2;

        McStylePanel.drawTexturedPanel(graphics, x, y, panelW, panelH);

        graphics.drawCenteredString(this.font, this.title, cx, y + layout.s(6), 0xFF404040);
        graphics.drawString(this.font, sectionText, x + layout.s(10), y + layout.s(30), 0xFF404040, false);
        graphics.drawCenteredString(this.font, placeholderText, cx, cy - 6, 0xFF404040);
        graphics.drawCenteredString(this.font, hintText, cx, cy + 18, 0xFF606060);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
