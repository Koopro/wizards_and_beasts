package at.koopro.wizardsandbeasts.client.skill.gui;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette.GuiSkin;
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
        // The skill web's own vellum, so a refusal reads as the same book as the chart it guards.
        // Shadowless ink: the title in red rubrication, the reason in ink, the hint thinner.
        McStylePanel.drawSkinPanel(graphics, GuiSkin.STAR_CHART, px, py, panelW, panelH);
        drawCentred(graphics, this.title, cx, py + 14, WizardsPalette.PAGE_RUBRIC);
        drawCentred(graphics, detail, cx, py + 32, GuiSkin.STAR_CHART.ink());
        drawCentred(graphics, Component.translatable("screen.wizards_and_beasts.skill_access_denied.hint"),
                cx, py + 52, GuiSkin.STAR_CHART.muted());
        graphics.pose().popMatrix();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** {@code drawCenteredString} without its drop shadow, which smudges on paper. */
    private void drawCentred(GuiGraphics graphics, Component text, int cx, int y, int colour) {
        graphics.drawString(this.font, text, cx - this.font.width(text) / 2, y, colour, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
