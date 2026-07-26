package at.koopro.wizardsandbeasts.client.owl.screen;

import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import at.koopro.wizardsandbeasts.client.owl.ClientOWLCache;
import at.koopro.wizardsandbeasts.network.owl.RequestOWLExamPacket;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
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

    private float guiScale = 1.0f;
    private int originX;
    private int originY;
    private int designLeft;
    private int designTop;

    public OWLExamScreen() {
        super(Component.translatable("owls.screen.title"));
    }

    /** Map an unscaled design-space x (relative to current width/height) to screen space. */
    private int sx(int designX) {
        return originX + Math.round((designX - designLeft) * guiScale);
    }

    private int sy(int designY) {
        return originY + Math.round((designY - designTop) * guiScale);
    }

    private int sw(int size) {
        return Math.max(1, Math.round(size * guiScale));
    }

    @Override
    protected void init() {
        super.init();
        guiScale = GuiScaleHelper.computeScale(BG_WIDTH, BG_HEIGHT, width, height, GuiScaleHelper.DEFAULT_MARGIN);
        designLeft = width / 2 - BG_WIDTH / 2;
        designTop = height / 2 - BG_HEIGHT / 2;
        originX = GuiScaleHelper.clampedLeft(Math.round(BG_WIDTH * guiScale), width, GuiScaleHelper.DEFAULT_MARGIN);
        originY = GuiScaleHelper.clampedTop(Math.round(BG_HEIGHT * guiScale), height, GuiScaleHelper.DEFAULT_MARGIN);
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
                .pos(sx(cx - 100), sy(cy + 20))
                .size(sw(96), sw(20))
                .build());
        addRenderableWidget(Button.builder(
                Component.translatable("owls.button.notyet"),
                btn -> onClose())
                .pos(sx(cx + 4), sy(cy + 20))
                .size(sw(96), sw(20))
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
                .pos(sx(cx - 75), sy(height / 2 + 70))
                .size(sw(150), sw(20))
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
        // No renderBackground() here: the screen framework already ran it for this frame.
        int cx = width / 2;
        int cy = height / 2;

        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(originX - designLeft * guiScale, originY - designTop * guiScale);
        pose.scale(guiScale, guiScale);

        graphics.fill(cx - BG_WIDTH / 2, cy - BG_HEIGHT / 2, cx + BG_WIDTH / 2, cy + BG_HEIGHT / 2, 0xEEF5E6C8);

        if (!examConfirmed) {
            renderConfirmPhase(graphics, cx, cy);
        } else {
            renderResultsPhase(graphics, cx, cy);
        }

        pose.popMatrix();

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
