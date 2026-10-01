package at.koopro.wizardsandbeasts.client.admin.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * The confirmation step for a dangerous change: what is about to happen, who it affects, and two buttons.
 *
 * <p>Shown only when something in the batch is flagged dangerous (or for a whole-section reset) — a harmless
 * toggle is applied without ceremony, because a dialog on every click teaches people to click through
 * dialogs. Its {@link AdminWarning.Severity} sets the title glyph, the body's notices and the confirm button's tone:
 * DANGER for what reaches every player or cannot be taken back, WARNING otherwise.
 *
 * <p>Owns its layout and drawing; the screen owns input, adding {@link #buttons()} as its only widgets while
 * the dialog is up so nothing behind the scrim can be clicked.
 */
@NullMarked
public final class AdminConfirmDialog {

    private static final int WIDTH = 280;
    private static final int BUTTON_W = 80;
    private static final int BUTTON_H = 18;

    private final Component title;
    private final List<Component> body;
    private final AdminWarning.Severity severity;
    private final AdminButton cancel;
    private final AdminButton confirm;
    private int x;
    private int y;
    private int w;
    private int h;

    public AdminConfirmDialog(Component title, List<Component> body, Component confirmLabel, boolean destructive,
                              Runnable onConfirm, Runnable onCancel) {
        this(title, body, confirmLabel, destructive ? AdminWarning.Severity.DANGER : AdminWarning.Severity.WARNING,
                onConfirm, onCancel);
    }

    public AdminConfirmDialog(Component title, List<Component> body, Component confirmLabel,
                              AdminWarning.Severity severity, Runnable onConfirm, Runnable onCancel) {
        this.title = title;
        this.body = List.copyOf(body);
        this.severity = severity;
        boolean destructive = severity == AdminWarning.Severity.DANGER;
        this.cancel = new AdminButton(0, 0, BUTTON_W, BUTTON_H,
                Component.translatable("admin.wizards_and_beasts.button.cancel"), AdminButton.Tone.NEUTRAL, onCancel);
        this.confirm = new AdminButton(0, 0, BUTTON_W, BUTTON_H, confirmLabel,
                destructive ? AdminButton.Tone.DANGER : AdminButton.Tone.PRIMARY, onConfirm);
    }

    public List<AdminButton> buttons() {
        return List.of(cancel, confirm);
    }

    public AdminButton cancelButton() {
        return cancel;
    }

    public void layout(Font font, int screenW, int screenH) {
        w = Math.min(WIDTH, screenW - 2 * AdminTheme.PAD);
        int textW = w - 2 * AdminTheme.PAD;
        int bodyH = 0;
        for (Component paragraph : body) {
            bodyH += AdminWarning.measure(font, textW, paragraph) + AdminTheme.GAP;
        }
        h = Math.min(screenH - 2 * AdminTheme.PAD, 20 + bodyH + BUTTON_H + 2 * AdminTheme.PAD + 4);
        x = (screenW - w) / 2;
        y = (screenH - h) / 2;
        int buttonsY = y + h - AdminTheme.PAD - BUTTON_H;
        confirm.setPosition(x + w - AdminTheme.PAD - BUTTON_W, buttonsY);
        cancel.setPosition(confirm.getX() - AdminTheme.GAP - BUTTON_W, buttonsY);
    }

    /** Scrim and panel. The buttons are drawn afterwards by the screen, on top. */
    public void render(GuiGraphics g, Font font, int screenW, int screenH) {
        g.fill(0, 0, screenW, screenH, AdminTheme.SCRIM);
        AdminTheme.raised(g, x - 2, y - 2, w + 4, h + 4, AdminTheme.FRAME_RAISED, AdminTheme.GOLD_DARK, AdminTheme.FRAME_HOVER);
        g.fill(x, y, x + w, y + h, AdminTheme.PAPER);
        g.fill(x, y, x + w, y + 16, AdminTheme.FRAME);
        String glyph = severity.glyph() + " ";
        // Gilt on the purple title bar for every severity: wax red there is too dark to read; the glyph tells them apart.
        g.drawString(font, glyph, x + AdminTheme.PAD, y + 4, AdminTheme.GOLD_LIGHT, false);
        g.drawString(font, title, x + AdminTheme.PAD + font.width(glyph), y + 4, AdminTheme.FRAME_TEXT, false);
        int lineY = y + 20;
        int textW = w - 2 * AdminTheme.PAD;
        int limit = y + h - AdminTheme.PAD - BUTTON_H - AdminTheme.GAP;
        for (Component paragraph : body) {
            if (lineY >= limit) {
                break;
            }
            lineY += AdminWarning.render(g, font, x + AdminTheme.PAD, lineY, textW, paragraph, severity) + AdminTheme.GAP;
        }
    }
}
