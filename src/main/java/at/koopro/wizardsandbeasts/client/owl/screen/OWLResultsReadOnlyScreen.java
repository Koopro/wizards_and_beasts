package at.koopro.wizardsandbeasts.client.owl.screen;

import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui;
import at.koopro.wizardsandbeasts.client.gui.ui.UiSurface;
import at.koopro.wizardsandbeasts.client.gui.ui.UiText;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedButton;
import at.koopro.wizardsandbeasts.client.owl.ClientOWLCache;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import at.koopro.wizardsandbeasts.owl.Profession;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Read-only screen shown when a player right-clicks the Examination Desk after
 * already taking the exam. Shows grades and current profession (if chosen).
 */
public class OWLResultsReadOnlyScreen extends ScaledParchmentScreen {

    private static final int BG_WIDTH = 280;
    private static final int BG_HEIGHT = 240;
    /** Text and buttons start this far in from the sheet's edge: clear of its double ink rule. */
    private static final int PAD = 14;
    private static final int BUTTON_H = 20;

    private final Map<OWLSubject, OWLGrade> grades;
    @Nullable
    private final Profession profession;

    public OWLResultsReadOnlyScreen(
            @NonNull Map<OWLSubject, OWLGrade> grades,
            @Nullable Profession profession) {
        super(Component.translatable("owls.screen.results_title"));
        this.grades = grades;
        this.profession = profession;
    }

    @Override
    protected void init() {
        super.init();
        layout(BG_WIDTH, BG_HEIGHT);
        int cx = width / 2;
        int by = height / 2 + BG_HEIGHT / 2 - PAD - BUTTON_H;
        int bw = (BG_WIDTH - 2 * PAD - WizardsMetrics.SPACE_M) / 2;

        // Both buttons inside the sheet, on one row: Done used to hang 14px off its bottom edge.
        if (ClientOWLCache.isCareerOpen()) {
            addRenderableWidget(new ThemedButton(sx(cx - BG_WIDTH / 2 + PAD), sy(by), sw(bw), sw(BUTTON_H),
                Component.translatable("owls.button.choose_path"), () -> {
                        assert minecraft != null;
                        minecraft.setScreen(new ProfessionSelectionScreen(grades));
                    })
                .tone(McStylePanel.ButtonTone.CONFIRM));
        }
        addRenderableWidget(new ThemedButton(sx(cx + BG_WIDTH / 2 - PAD - bw), sy(by), sw(bw), sw(BUTTON_H),
                Component.translatable("gui.done"), () -> onClose()));
    }

    /**
     * The results as a table: the subject on the left, the grade's word and its letter on a chip at the
     * right. One sentence per subject ("Defence Against the Dark Arts — E (Exceeds Expectations)") ran
     * off the sheet, and a list of sentences hides the one thing a player scans for, the letter.
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No renderBackground() here: the screen framework already ran it for this frame.
        int cx = width / 2;
        int cy = height / 2;
        int left = cx - BG_WIDTH / 2 + PAD;
        int innerW = BG_WIDTH - 2 * PAD;
        UiSurface page = UiSurface.PAGE;
        var pose = graphics.pose();
        beginScaledPass(graphics);
        drawParchment(graphics, BG_WIDTH, BG_HEIGHT);
        int rowY = Ui.title(graphics, font, page, Component.translatable("owls.screen.results_title"),
                left, cy - BG_HEIGHT / 2 + PAD, innerW, null);

        int chipW = Ui.chipWidth(font, Component.literal("W"));
        for (OWLSubject subject : OWLSubject.values()) {
            Component name = Component.translatable(subject.translationKey());
            Component word;
            Component letter;
            Ui.Tone tone;
            if (!subject.examined()) {
                // Not sat: no content in the game feeds it, so no grade is printed against it.
                word = Component.translatable("owls.subject.not_examined");
                letter = Component.literal("–");
                tone = Ui.Tone.MUTED;
            } else {
                OWLGrade grade = grades.getOrDefault(subject, OWLGrade.T);
                word = Component.translatable(grade.translationKey());
                letter = Component.literal(grade.name());
                tone = grade.passing ? Ui.Tone.GOOD : Ui.Tone.BAD;
            }
            int wordW = font.width(word);
            int nameW = innerW - chipW - wordW - 2 * WizardsMetrics.SPACE_M;
            UiText.drawFit(graphics, font, page, name, left, rowY + 1, nameW,
                    subject.examined() ? page.ink() : page.ink3());
            UiText.drawRight(graphics, font, page, word, left + innerW - chipW - WizardsMetrics.SPACE_S, rowY + 1,
                    subject.examined() ? page.ink2() : page.ink3());
            Ui.chip(graphics, font, page, left + innerW - Ui.chipWidth(font, letter), rowY - 1, letter, tone);
            rowY += WizardsMetrics.LINE_BODY;
        }

        // When the next sitting is: the sheet is a checkpoint, not a verdict for life.
        long now = minecraft == null || minecraft.level == null ? 0L : minecraft.level.getGameTime();
        long waitTicks = ClientOWLCache.getNextSittingTick() - now;
        rowY += WizardsMetrics.SPACE_S;
        if (waitTicks > 0) {
            long days = (waitTicks + 23999L) / 24000L;
            drawCentred(graphics, Component.translatable("owls.screen.resit_in", days), cx, rowY, page.ink2());
            rowY += WizardsMetrics.LINE_BODY;
        }
        if (profession != null) {
            drawCentred(graphics, Component.translatable("owls.screen.profession_chosen",
                            Component.translatable(profession.translationKey())), cx, rowY, page.heading());
        }

        pose.popMatrix();

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
