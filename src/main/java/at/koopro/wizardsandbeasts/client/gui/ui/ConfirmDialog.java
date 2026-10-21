package at.koopro.wizardsandbeasts.client.gui.ui;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * A modal question over a screen: a title, what will happen, and two buttons.
 *
 * <p>Not a {@code Screen}: a confirmation belongs to the screen that asked, which keeps drawing behind
 * the scrim, and a separate screen would lose that screen's scroll and selection when it closed. The
 * owning screen holds one, forwards {@code render}, {@code mouseClicked} and {@code keyPressed} to it
 * first while {@link #isOpen()}, and stops there when it returns {@code true}.
 *
 * <p>Rules: the confirm label is the verb ("Forget spell"), never "Yes"/"OK". A destructive action uses
 * {@link McStylePanel.ButtonTone#DANGER} and Enter does <em>not</em> confirm it -- only a click does.
 * Escape always cancels.
 */
@NullMarked
public final class ConfirmDialog {

    private static final int BUTTON_H = 20;

    private @Nullable Component title;
    private @Nullable Component body;
    private Component confirmLabel = Component.empty();
    private Component cancelLabel = Component.empty();
    private McStylePanel.ButtonTone tone = McStylePanel.ButtonTone.CONFIRM;
    private @Nullable Runnable onConfirm;
    private Ui.Rect confirmRect = new Ui.Rect(0, 0, 0, 0);
    private Ui.Rect cancelRect = new Ui.Rect(0, 0, 0, 0);
    private boolean open;

    /** Opens the dialog. {@code tone} is CONFIRM for a commitment, DANGER for something destroyed. */
    public void open(Component title, Component body, Component confirmLabel, Component cancelLabel,
                     McStylePanel.ButtonTone tone, Runnable onConfirm) {
        this.title = title;
        this.body = body;
        this.confirmLabel = confirmLabel;
        this.cancelLabel = cancelLabel;
        this.tone = tone;
        this.onConfirm = onConfirm;
        this.open = true;
    }

    public boolean isOpen() {
        return open;
    }

    public void close() {
        open = false;
        onConfirm = null;
    }

    public void render(GuiGraphics g, Font font, int screenW, int screenH, int mouseX, int mouseY) {
        if (!open || title == null || body == null) {
            return;
        }
        g.nextStratum();
        Ui.scrim(g, screenW, screenH);
        int w = Math.min(WizardsMetrics.PANEL_MODAL_W, screenW - 2 * WizardsMetrics.SPACE_M);
        int pad = WizardsMetrics.SPACE_M + 4;
        int textW = w - 2 * pad;
        int bodyLines = Math.min(6, UiText.lineCount(font, body, textW));
        int h = pad + WizardsMetrics.LINE_TITLE + WizardsMetrics.DIVIDER_H
                + bodyLines * WizardsMetrics.LINE_BODY + WizardsMetrics.SPACE_L + BUTTON_H + pad;
        int x = (screenW - w) / 2;
        int y = (screenH - h) / 2;
        UiSurface s = UiSurface.PAGE;
        Ui.panel(g, s, x, y, w, h);
        int cy = Ui.title(g, font, s, title, x + pad, y + pad, textW, null);
        cy = UiText.drawWrapped(g, font, s, UiText.Role.BODY, body, x + pad, cy, textW, 6);
        int bw = (textW - WizardsMetrics.SPACE_M) / 2;
        int by = y + h - pad - BUTTON_H;
        cancelRect = new Ui.Rect(x + pad, by, bw, BUTTON_H);
        confirmRect = new Ui.Rect(x + pad + bw + WizardsMetrics.SPACE_M, by, bw, BUTTON_H);
        button(g, font, cancelRect, cancelLabel, McStylePanel.ButtonTone.NEUTRAL, mouseX, mouseY);
        button(g, font, confirmRect, confirmLabel, tone, mouseX, mouseY);
    }

    private static void button(GuiGraphics g, Font font, Ui.Rect r, Component label,
                               McStylePanel.ButtonTone tone, int mouseX, int mouseY) {
        McStylePanel.drawThemedButton(g, r.x(), r.y(), r.w(), r.h(), tone,
                McStylePanel.ControlState.of(true, r.contains(mouseX, mouseY)));
        String text = UiText.fit(font, label.getString(), r.w() - 2 * WizardsMetrics.SPACE_S);
        g.drawString(font, text, r.x() + (r.w() - font.width(text)) / 2, r.y() + (r.h() - font.lineHeight) / 2 + 1,
                UiSurface.PAGE.ink(), false);
    }

    /** Returns true while open: a modal swallows every click, inside it or not. */
    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!open) {
            return false;
        }
        if (confirmRect.contains(mouseX, mouseY)) {
            click();
            Runnable action = onConfirm;
            close();
            if (action != null) {
                action.run();
            }
        } else if (cancelRect.contains(mouseX, mouseY)) {
            click();
            close();
        }
        return true;
    }

    /** Escape cancels; Enter confirms unless the action is destructive. Returns true while open. */
    public boolean keyPressed(int keyCode) {
        if (!open) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
        } else if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && tone != McStylePanel.ButtonTone.DANGER) {
            Runnable action = onConfirm;
            close();
            if (action != null) {
                action.run();
            }
        }
        return true;
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }
}
