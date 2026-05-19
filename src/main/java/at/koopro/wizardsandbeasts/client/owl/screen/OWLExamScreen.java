package at.koopro.wizardsandbeasts.client.owl.screen;

import at.koopro.wizardsandbeasts.client.owl.ClientOWLCache;
import at.koopro.wizardsandbeasts.network.owl.RequestOWLExamPacket;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.Map;

/**
 * Two-phase OWL examination screen.
 * Phase 1: confirmation dialog.
 * Phase 2: results display (populated after server sends OWLDataSyncPayload).
 */
public class OWLExamScreen extends Screen {

    private static final int BG_WIDTH = 256;
    private static final int BG_HEIGHT = 220;

    private boolean examConfirmed = false;

    public OWLExamScreen() {
        super(Component.translatable("owls.screen.title"));
    }

    @Override
    protected void init() {
        super.init();
        if (!examConfirmed) {
            initConfirmPhase();
        } else {
            initResultsPhase();
        }
    }

    private void initConfirmPhase() {
        int cx = width / 2;
        int cy = height / 2;
        addRenderableWidget(Button.builder(
                Component.translatable("owls.button.ready"),
                btn -> sendExamRequest())
                .pos(cx - 100, cy + 20)
                .size(96, 20)
                .build());
        addRenderableWidget(Button.builder(
                Component.translatable("owls.button.notyet"),
                btn -> onClose())
                .pos(cx + 4, cy + 20)
                .size(96, 20)
                .build());
    }

    private void initResultsPhase() {
        int cx = width / 2;
        addRenderableWidget(Button.builder(
                Component.translatable("owls.button.choose_path"),
                btn -> {
                    assert minecraft != null;
                    minecraft.setScreen(new ProfessionSelectionScreen(ClientOWLCache.getGrades()));
                })
                .pos(cx - 75, height / 2 + 70)
                .size(150, 20)
                .build());
    }

    private void sendExamRequest() {
        ClientPacketDistributor.sendToServer(new RequestOWLExamPacket());
        examConfirmed = true;
        clearWidgets();
        initResultsPhase();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int cx = width / 2;
        int cy = height / 2;

        graphics.fill(cx - BG_WIDTH / 2, cy - BG_HEIGHT / 2, cx + BG_WIDTH / 2, cy + BG_HEIGHT / 2, 0xEEF5E6C8);

        if (!examConfirmed) {
            renderConfirmPhase(graphics, cx, cy);
        } else {
            renderResultsPhase(graphics, cx, cy);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderConfirmPhase(GuiGraphics graphics, int cx, int cy) {
        graphics.drawCenteredString(font,
                Component.translatable("owls.screen.title").withColor(0x5C3317),
                cx, cy - BG_HEIGHT / 2 + 12, 0x5C3317);
        int lineY = cy - 30;
        String[] lines = font.getSplitter().splitLines(
                Component.translatable("owls.screen.confirm_text"),
                BG_WIDTH - 24, Style.EMPTY)
                .stream()
                .map(line -> line.getString())
                .toArray(String[]::new);
        for (String line : lines) {
            graphics.drawCenteredString(font, line, cx, lineY, 0x3A2010);
            lineY += font.lineHeight + 2;
        }
    }

    private void renderResultsPhase(GuiGraphics graphics, int cx, int cy) {
        Map<OWLSubject, OWLGrade> grades = ClientOWLCache.getGrades();
        graphics.drawCenteredString(font,
                Component.translatable("owls.screen.results_title").withColor(0x5C3317),
                cx, cy - BG_HEIGHT / 2 + 12, 0x5C3317);

        int rowY = cy - BG_HEIGHT / 2 + 30;
        for (OWLSubject subject : OWLSubject.values()) {
            OWLGrade grade = grades.getOrDefault(subject, OWLGrade.T);
            int color = grade.passing ? 0xCC8800 : 0x666666;
            String text = Component.translatable(subject.translationKey()).getString()
                    + " — " + Component.translatable(grade.translationKey()).getString()
                    + " (" + grade.name() + ")";
            graphics.drawString(font, text, cx - BG_WIDTH / 2 + 12, rowY, color, false);
            rowY += font.lineHeight + 3;
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
