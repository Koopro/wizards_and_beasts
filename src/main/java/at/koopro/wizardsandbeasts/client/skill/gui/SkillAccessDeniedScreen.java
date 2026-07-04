package at.koopro.wizardsandbeasts.client.skill.gui;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class SkillAccessDeniedScreen extends Screen {

    private final Component detail;

    public SkillAccessDeniedScreen(Component detail) {
        super(Component.translatable("screen.wizards_and_beasts.skill_access_denied.title"));
        this.detail = detail;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderMenuBackground(graphics);
        int panelW = 260;
        int panelH = 90;
        GuiScaleHelper.Layout layout = GuiScaleHelper.Layout.fit(width, height, panelW, panelH);
        layout.applyScale(graphics);
        int px = layout.panelX();
        int py = layout.panelY();
        int cx = px + panelW / 2;
        McStylePanel.drawTexturedPanel(graphics, px, py, panelW, panelH);
        graphics.drawCenteredString(this.font, this.title, cx, py + 12, 0xFFB02A2A);
        graphics.drawCenteredString(this.font, detail, cx, py + 30, 0xFF404040);
        graphics.drawCenteredString(this.font,
                Component.translatable("screen.wizards_and_beasts.skill_access_denied.hint"),
                cx, py + 50, 0xFF606060);
        graphics.pose().popMatrix();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
