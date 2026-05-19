package at.koopro.wizardsandbeasts.client.owl.screen;

import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import at.koopro.wizardsandbeasts.owl.Profession;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Read-only screen shown when a player right-clicks the Examination Desk after
 * already taking the exam. Shows grades and current profession (if chosen).
 */
public class OWLResultsReadOnlyScreen extends Screen {

    private static final int BG_WIDTH = 280;
    private static final int BG_HEIGHT = 240;

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
        int cx = width / 2;
        int cy = height / 2;

        if (profession == null) {
            addRenderableWidget(Button.builder(
                    Component.translatable("owls.button.choose_path"),
                    btn -> {
                        assert minecraft != null;
                        minecraft.setScreen(new ProfessionSelectionScreen(grades));
                    })
                    .pos(cx - 75, cy + BG_HEIGHT / 2 - 28)
                    .size(150, 20)
                    .build());
        }

        addRenderableWidget(Button.builder(
                Component.translatable("gui.done"),
                btn -> onClose())
                .pos(cx - 50, cy + BG_HEIGHT / 2 - 6)
                .size(100, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int cx = width / 2;
        int cy = height / 2;

        graphics.fill(cx - BG_WIDTH / 2, cy - BG_HEIGHT / 2, cx + BG_WIDTH / 2, cy + BG_HEIGHT / 2, 0xEEF5E6C8);
        graphics.drawCenteredString(font,
                Component.translatable("owls.screen.results_title").withColor(0x5C3317),
                cx, cy - BG_HEIGHT / 2 + 8, 0x5C3317);

        int rowY = cy - BG_HEIGHT / 2 + 24;
        for (OWLSubject subject : OWLSubject.values()) {
            OWLGrade grade = grades.getOrDefault(subject, OWLGrade.T);
            int color = grade.passing ? 0xCC8800 : 0x666666;
            String text = Component.translatable(subject.translationKey()).getString()
                    + " — " + grade.name()
                    + " (" + Component.translatable(grade.translationKey()).getString() + ")";
            graphics.drawString(font, text, cx - BG_WIDTH / 2 + 10, rowY, color, false);
            rowY += font.lineHeight + 3;
        }

        if (profession != null) {
            rowY += 6;
            graphics.drawCenteredString(font,
                    Component.translatable("owls.screen.profession_chosen",
                            Component.translatable(profession.translationKey())).withStyle(ChatFormatting.GOLD),
                    cx, rowY, 0xCC8800);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
