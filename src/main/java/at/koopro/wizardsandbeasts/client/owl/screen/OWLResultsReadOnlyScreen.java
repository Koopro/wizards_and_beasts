package at.koopro.wizardsandbeasts.client.owl.screen;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedButton;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
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
    /** The Done button bottom sits 14px below the parchment panel by design. */
    private static final int CONTENT_OVERHANG = 14;

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
        layout(BG_WIDTH, BG_HEIGHT, CONTENT_OVERHANG);
        int cx = width / 2;
        int cy = height / 2;

        if (profession == null) {
            addRenderableWidget(new ThemedButton(sx(cx - 75), sy(cy + BG_HEIGHT / 2 - 28), sw(150), sw(20),
                Component.translatable("owls.button.choose_path"), () -> {
                        assert minecraft != null;
                        minecraft.setScreen(new ProfessionSelectionScreen(grades));
                    })
                .tone(McStylePanel.ButtonTone.CONFIRM));
        }

        addRenderableWidget(new ThemedButton(sx(cx - 50), sy(cy + BG_HEIGHT / 2 - 6), sw(100), sw(20),
                Component.translatable("gui.done"), () -> onClose()));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No renderBackground() here: the screen framework already ran it for this frame.
        int cx = width / 2;
        int cy = height / 2;
        var pose = graphics.pose();
        beginScaledPass(graphics);
        drawParchment(graphics, BG_WIDTH, BG_HEIGHT);
        drawCentred(graphics, Component.translatable("owls.screen.results_title"),
                cx, cy - BG_HEIGHT / 2 + 10, WizardsPalette.PAGE_RUBRIC);

        int rowY = cy - BG_HEIGHT / 2 + 24;
        for (OWLSubject subject : OWLSubject.values()) {
            OWLGrade grade = grades.getOrDefault(subject, OWLGrade.T);
            // Opaque ink: the old 0xCC8800 / 0x666666 had no alpha byte, and a text colour with
            // alpha 0 is not drawn at all on 1.21.11.
            int color = grade.passing ? WizardsPalette.PAGE_GOOD : WizardsPalette.PAGE_INK_2;
            String text = Component.translatable(subject.translationKey()).getString()
                    + " — " + grade.name()
                    + " (" + Component.translatable(grade.translationKey()).getString() + ")";
            graphics.drawString(font, text, cx - BG_WIDTH / 2 + 12, rowY, color, false);
            rowY += font.lineHeight + 3;
        }

        if (profession != null) {
            rowY += 6;
            // Red ink rather than chat GOLD, which is under 2 : 1 on the sheet.
            drawCentred(graphics, Component.translatable("owls.screen.profession_chosen",
                            Component.translatable(profession.translationKey())),
                    cx, rowY, WizardsPalette.PAGE_RUBRIC);
        }

        pose.popMatrix();

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
