package at.koopro.wizardsandbeasts.client.spell.gui;

import at.koopro.wizardsandbeasts.client.spell.state.ClientSpellTeacherState;
import at.koopro.wizardsandbeasts.network.spell.teacher.SpellTeacherLearnC2SPayload;
import at.koopro.wizardsandbeasts.spell.learning.SpellLearningService;
import at.koopro.wizardsandbeasts.client.gui.ScreenLayoutScaler;
import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class SpellTeacherScreen extends Screen {

    private static final int PANEL_W = 280;
    private static final int PANEL_H = 220;

    private final List<SpellLearningService.SpellOffer> offers = new ArrayList<>();
    private int scrollOffset = 0;
    private ScreenLayoutScaler layout;

    public SpellTeacherScreen() {
        super(Component.literal("Spell Teacher"));
    }

    @Override
    protected void init() {
        super.init();
        layout = ScreenLayoutScaler.forScreen(width, height, PANEL_W, PANEL_H);
        offers.clear();
        offers.addAll(ClientSpellTeacherState.offers());
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearWidgets();
        int panelX = layout.panelX();
        int panelY = layout.panelY();
        int maxVisible = Math.max(4, (layout.panelH() - layout.s(54)) / layout.s(22));
        int maxScroll = Math.max(0, offers.size() - maxVisible);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(panelX + layout.panelW() - layout.s(54), panelY + layout.s(6), layout.s(48), layout.s(16))
                .build());

        int start = scrollOffset;
        int end = Math.min(offers.size(), start + maxVisible);
        int y = panelY + layout.s(28);
        for (int i = start; i < end; i++) {
            SpellLearningService.SpellOffer offer = offers.get(i);
            String state = offer.learnable() ? "\u00A7aLearn" : "\u00A77Locked";
            String lockHint = !offer.learnable() && !offer.requirementText().isBlank()
                    ? " - " + trimHint(offer.requirementText(), 32)
                    : "";
            String label = offer.displayName() + " [" + offer.category().toLowerCase() + "] " + state + lockHint;
            addRenderableWidget(Button.builder(Component.literal(label), b ->
                            ClientPacketDistributor.sendToServer(new SpellTeacherLearnC2SPayload(offer.spellId())))
                    .bounds(panelX + layout.s(8), y, layout.panelW() - layout.s(16), layout.s(20))
                    .build()).active = offer.learnable();
            y += layout.s(22);
        }

        if (maxScroll > 0) {
            addRenderableWidget(Button.builder(Component.literal("\u25B2"), b -> {
                        scrollOffset = Math.max(0, scrollOffset - 1);
                        rebuildButtons();
                }).bounds(panelX + layout.panelW() - layout.s(22), panelY + layout.s(28), layout.s(14), layout.s(12))
                    .build()).active = scrollOffset > 0;

            addRenderableWidget(Button.builder(Component.literal("\u25BC"), b -> {
                        scrollOffset = Math.min(maxScroll, scrollOffset + 1);
                        rebuildButtons();
                }).bounds(panelX + layout.panelW() - layout.s(22), panelY + layout.panelH() - layout.s(22), layout.s(14), layout.s(12))
                    .build()).active = scrollOffset < maxScroll;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxVisible = Math.max(4, (layout.panelH() - layout.s(54)) / layout.s(22));
        int maxScroll = Math.max(0, offers.size() - maxVisible);
        if (maxScroll <= 0) {
            return false;
        }
        scrollOffset = Math.max(0, Math.min(maxScroll, (int) (scrollOffset - scrollY)));
        rebuildButtons();
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderMenuBackground(graphics);
        int panelX = layout.panelX();
        int panelY = layout.panelY();
        int panelW = layout.panelW();
        int panelH = layout.panelH();
        McStylePanel.drawTexturedPanel(graphics, panelX, panelY, panelW, panelH);
        graphics.drawString(font, "Spell Teacher", panelX + layout.s(8), panelY + layout.s(8), 0xFFFFD700, false);

        int hoveredIndex = -1;
        int start = scrollOffset;
        int maxVisible = Math.max(4, (layout.panelH() - layout.s(54)) / layout.s(22));
        int end = Math.min(offers.size(), start + maxVisible);
        int y = panelY + layout.s(28);
        for (int i = start; i < end; i++) {
            if (mouseX >= panelX + layout.s(8) && mouseX <= panelX + panelW - layout.s(8) && mouseY >= y && mouseY <= y + layout.s(20)) {
                hoveredIndex = i;
                break;
            }
            y += layout.s(22);
        }

        if (hoveredIndex >= 0) {
            SpellLearningService.SpellOffer offer = offers.get(hoveredIndex);
            String req = offer.requirementText().isBlank() ? "No requirement" : offer.requirementText();
            String cost = offer.costKnuts() > 0 ? "Cost: " + offer.costKnuts() + " knuts" : "Cost: Free";
            graphics.drawString(font, req, panelX + layout.s(8), panelY + panelH - layout.s(24), 0xFFCCCCCC, false);
            graphics.drawString(font, cost, panelX + layout.s(8), panelY + panelH - layout.s(14), 0xFFAAAAAA, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String trimHint(String text, int max) {
        if (text == null || text.length() <= max) {
            return text == null ? "" : text;
        }
        return text.substring(0, max - 3) + "...";
    }
}
