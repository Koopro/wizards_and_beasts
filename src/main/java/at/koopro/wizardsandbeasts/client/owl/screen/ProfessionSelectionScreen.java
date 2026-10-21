package at.koopro.wizardsandbeasts.client.owl.screen;

import at.koopro.wizardsandbeasts.client.owl.ClientOWLCache;
import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.ui.ConfirmDialog;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui;
import at.koopro.wizardsandbeasts.client.gui.ui.UiSurface;
import at.koopro.wizardsandbeasts.client.gui.ui.UiText;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedButton;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.network.owl.ChooseProfessionPacket;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import at.koopro.wizardsandbeasts.owl.Profession;
import at.koopro.wizardsandbeasts.owl.ProfessionEligibility;
import at.koopro.wizardsandbeasts.owl.career.CareerProfile;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NonNull;

import java.util.Map;

/**
 * Choosing a career after the O.W.L.s.
 *
 * <p>Each career is a two-line row: its name, then the work it pays for and the day's wage. Careers a
 * player's grades open get a Choose button in a column of its own; the rest carry a "Not qualified"
 * chip there. The work line used to share one line with the name and the button, so the button sat on
 * top of the wage ("1G 2…"); and the confirm step was vanilla's grey stone screen in the middle of a
 * parchment flow. Scrolls with the wheel; the scrollbar shows where you are.
 */
public class ProfessionSelectionScreen extends ScaledParchmentScreen {

    private static final int BG_WIDTH = 300;
    private static final int BG_HEIGHT = 220;
    private static final int PAD = 14;
    private static final int ROW_HEIGHT = 24;
    private static final int BUTTON_W = 52;
    private static final int BUTTON_H = 14;
    private static final int LIST_TOP = PAD + WizardsMetrics.LINE_TITLE + 4;

    private final Map<OWLSubject, OWLGrade> grades;
    private final ConfirmDialog confirm = new ConfirmDialog();
    private int scrollOffset = 0;

    public ProfessionSelectionScreen(@NonNull Map<OWLSubject, OWLGrade> grades) {
        super(Component.translatable("owls.screen.profession_title"));
        this.grades = grades;
    }

    @Override
    protected void init() {
        super.init();
        layout(BG_WIDTH, BG_HEIGHT);
        rebuildButtons();
    }

    private int listTop() {
        return height / 2 - BG_HEIGHT / 2 + LIST_TOP;
    }

    private int listBottom() {
        return height / 2 + BG_HEIGHT / 2 - PAD;
    }

    private int maxScroll() {
        return Math.max(0, Profession.values().length * ROW_HEIGHT - (listBottom() - listTop()));
    }

    private void rebuildButtons() {
        clearWidgets();
        int right = width / 2 + BG_WIDTH / 2 - PAD - WizardsMetrics.SCROLLBAR_W - WizardsMetrics.SPACE_S;
        for (Profession prof : Profession.values()) {
            int rowY = listTop() + prof.ordinal() * ROW_HEIGHT - scrollOffset;
            if (rowY < listTop() || rowY + ROW_HEIGHT > listBottom() + 1 || !isClientEligible(prof)) {
                continue;
            }
            addRenderableWidget(new ThemedButton(sx(right - BUTTON_W), sy(rowY + (ROW_HEIGHT - BUTTON_H) / 2 - 1),
                    sw(BUTTON_W), sw(BUTTON_H), Component.translatable("owls.button.choose"), () -> confirmChoose(prof))
                    .tone(McStylePanel.ButtonTone.CONFIRM));
        }
    }

    private boolean isClientEligible(Profession prof) {
        // The same rule the server applies (ProfessionEligibility), with the N.E.W.T. sheet and the goblin
        // allowance the server synced, so what is greyed here is exactly what the server would refuse.
        return ProfessionEligibility.isEligible(prof, grades, ClientOWLCache.getNewtGrades(), ClientOWLCache.isGoblin());
    }

    /** "Duelling · 1 Galleon a working day", or why the career cannot be chosen. */
    private static Component workLine(Profession prof) {
        if (!prof.offered()) {
            return Component.translatable("owls.career.not_offered");
        }
        CareerProfile profile = CareerProfile.of(prof);
        return Component.translatable("owls.career.work_line",
                Component.translatable(profile.workKey()),
                CurrencyHelper.formatFromKnuts(profile.dailyWage()));
    }

    private void confirmChoose(Profession profession) {
        confirm.open(Component.translatable("owls.confirm.profession_title"),
                Component.translatable("owls.confirm.profession_body", Component.translatable(profession.translationKey())),
                Component.translatable("owls.button.choose"), Component.translatable("gui.cancel"),
                McStylePanel.ButtonTone.CONFIRM, () -> {
                    ClientPacketDistributor.sendToServer(new ChooseProfessionPacket(profession));
                    onClose();
                });
    }

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
        Ui.title(graphics, font, page, Component.translatable("owls.screen.profession_title"),
                left, cy - BG_HEIGHT / 2 + PAD, innerW, null);

        int textW = innerW - BUTTON_W - WizardsMetrics.SCROLLBAR_W - 2 * WizardsMetrics.SPACE_M;
        Component notQualified = Component.translatable("owls.career.not_qualified");
        graphics.enableScissor(left, listTop(), left + innerW, listBottom());
        for (Profession prof : Profession.values()) {
            int rowY = listTop() + prof.ordinal() * ROW_HEIGHT - scrollOffset;
            if (rowY + ROW_HEIGHT < listTop() || rowY > listBottom()) {
                continue;
            }
            boolean eligible = isClientEligible(prof);
            UiText.drawFit(graphics, font, page, Component.translatable(prof.translationKey()), left, rowY + 2, textW,
                    eligible ? page.ink() : page.ink3());
            UiText.drawFit(graphics, font, page, workLine(prof), left + 4, rowY + 2 + WizardsMetrics.LINE_TIGHT, textW - 4,
                    page.ink3());
            if (!eligible) {
                int chipW = Ui.chipWidth(font, notQualified);
                Ui.chip(graphics, font, page, left + innerW - WizardsMetrics.SCROLLBAR_W - WizardsMetrics.SPACE_S - chipW,
                        rowY + (ROW_HEIGHT - Ui.CHIP_H) / 2 - 1, notQualified, Ui.Tone.MUTED);
            }
            Ui.hairline(graphics, page, left, rowY + ROW_HEIGHT - 1, innerW - WizardsMetrics.SCROLLBAR_W - WizardsMetrics.SPACE_S);
        }
        graphics.disableScissor();
        int max = maxScroll();
        if (max > 0) {
            int trackH = listBottom() - listTop();
            int thumbH = Math.max(12, trackH * trackH / (trackH + max));
            McStylePanel.drawScrollbar(graphics, left + innerW - WizardsMetrics.SCROLLBAR_W, listTop(), trackH,
                    listTop() + (trackH - thumbH) * scrollOffset / max, thumbH);
        }

        pose.popMatrix();

        super.render(graphics, mouseX, mouseY, partialTick);
        confirm.render(graphics, font, width, height, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (confirm.isOpen()) {
            return true;
        }
        int next = Math.max(0, Math.min(maxScroll(), scrollOffset - (int) Math.signum(scrollY) * ROW_HEIGHT));
        if (next != scrollOffset) {
            scrollOffset = next;
            rebuildButtons();
        }
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return confirm.mouseClicked(event.x(), event.y()) || super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return confirm.keyPressed(event.key()) || super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
