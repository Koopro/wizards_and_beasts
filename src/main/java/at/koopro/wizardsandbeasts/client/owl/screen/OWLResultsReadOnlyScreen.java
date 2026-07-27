package at.koopro.wizardsandbeasts.client.owl.screen;

import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
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
    /** The Done button bottom sits 14px below the parchment panel by design. */
    private static final int CONTENT_OVERHANG = 14;

    private final Map<OWLSubject, OWLGrade> grades;
    @Nullable
    private final Profession profession;

    private float guiScale = 1.0f;
    private int originX;
    private int originY;
    private int designLeft;
    private int designTop;

    public OWLResultsReadOnlyScreen(
            @NonNull Map<OWLSubject, OWLGrade> grades,
            @Nullable Profession profession) {
        super(Component.translatable("owls.screen.results_title"));
        this.grades = grades;
        this.profession = profession;
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
        guiScale = GuiScaleHelper.computeScale(BG_WIDTH, BG_HEIGHT + CONTENT_OVERHANG,
                width, height, GuiScaleHelper.DEFAULT_MARGIN);
        designLeft = width / 2 - BG_WIDTH / 2;
        designTop = height / 2 - BG_HEIGHT / 2;
        originX = GuiScaleHelper.clampedLeft(Math.round(BG_WIDTH * guiScale), width, GuiScaleHelper.DEFAULT_MARGIN);
        originY = GuiScaleHelper.clampedTop(Math.round((BG_HEIGHT + CONTENT_OVERHANG) * guiScale),
                height, GuiScaleHelper.DEFAULT_MARGIN);
        int cx = width / 2;
        int cy = height / 2;

        if (profession == null) {
            addRenderableWidget(Button.builder(
                    Component.translatable("owls.button.choose_path"),
                    btn -> {
                        assert minecraft != null;
                        minecraft.setScreen(new ProfessionSelectionScreen(grades));
                    })
                    .pos(sx(cx - 75), sy(cy + BG_HEIGHT / 2 - 28))
                    .size(sw(150), sw(20))
                    .build());
        }

        addRenderableWidget(Button.builder(
                Component.translatable("gui.done"),
                btn -> onClose())
                .pos(sx(cx - 50), sy(cy + BG_HEIGHT / 2 - 6))
                .size(sw(100), sw(20))
                .build());
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

        pose.popMatrix();

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
