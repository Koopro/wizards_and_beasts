package at.koopro.wizardsandbeasts.client.admin.widget;

import at.koopro.wizardsandbeasts.admin.AdminResult;
import at.koopro.wizardsandbeasts.admin.config.SettingScope;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminState;
import at.koopro.wizardsandbeasts.network.admin.AdminSettingDescriptor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;
import org.jspecify.annotations.NullMarked;

/**
 * One setting's line on a panel: name and badges on the left, a status line under it, the control and a
 * reset-to-default button on the right.
 *
 * <p>The status line is where the row stays honest. In priority order it says: saving (a request is in
 * flight), refused and why (the server said no — the control already shows the real value again), edited
 * (a draft not yet applied), saved (briefly, after the server confirmed), changed by someone else, or simply
 * the default. A row never claims a value the server has not confirmed without saying so.
 */
@NullMarked
public final class AdminValueRow {

    private static final int RESET_W = 14;
    private static final long SAVED_FLASH_MS = 4_000L;
    private static final long REMOTE_FLASH_MS = 8_000L;
    private static final long HIGHLIGHT_MS = 2_500L;

    private AdminSettingDescriptor setting;
    private final AdminControl control;
    private final AdminButton reset;
    private int x;
    private int y;
    private int w;
    private boolean visible = true;
    /** Until when the row is outlined because search led here. */
    private long highlightUntil;

    public AdminValueRow(AdminSettingDescriptor setting, AdminControl control, AdminButton reset) {
        this.setting = setting;
        this.control = control;
        this.reset = reset;
    }

    public AdminSettingDescriptor setting() {
        return setting;
    }

    public AdminControl control() {
        return control;
    }

    public AdminButton resetButton() {
        return reset;
    }

    /** The control's width for a row of width {@code rowW}: generous, but never more than about half. */
    public static int controlWidth(int rowW) {
        return Math.max(90, Math.min(150, rowW * 45 / 100));
    }

    public static int resetWidth() {
        return RESET_W;
    }

    public void place(int rowX, int rowY, int rowW) {
        this.x = rowX;
        this.y = rowY;
        this.w = rowW;
        int controlW = controlWidth(rowW);
        int controlY = rowY + (AdminTheme.ROW_H - AdminTheme.CONTROL_H) / 2;
        control.widget().setPosition(rowX + rowW - RESET_W - AdminTheme.GAP - controlW, controlY);
        control.widget().setWidth(controlW);
        reset.setPosition(rowX + rowW - RESET_W, controlY);
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        control.widget().visible = visible;
        reset.visible = visible;
    }

    public boolean visible() {
        return visible;
    }

    /** Outlines the row for a moment: the administrator arrived here from search and should see which row it was. */
    public void highlight() {
        highlightUntil = Util.getMillis() + HIGHLIGHT_MS;
    }

    /** Replaces the descriptor (a new server value) and redraws the control with {@code shown}. */
    public void update(AdminSettingDescriptor fresh, String shown, boolean edited) {
        this.setting = fresh;
        control.display(shown);
        control.setEnabled(fresh.editable() && !ClientAdminState.isPending(fresh.id()));
        reset.active = fresh.editable() && (edited ? !shown.equals(fresh.defaultValue()) : !fresh.isDefault());
    }

    /** Whether the pointer is over the descriptive part of the row (the tooltip target). */
    public boolean labelHovered(int mouseX, int mouseY) {
        return visible && mouseX >= x && mouseX < control.widget().getX() - 2 && mouseY >= y && mouseY < y + AdminTheme.ROW_H;
    }

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY, boolean edited) {
        if (!visible) {
            return;
        }
        if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + AdminTheme.ROW_H) {
            g.fill(x - 2, y, x + w + 2, y + AdminTheme.ROW_H, AdminTheme.ROW_HOVER);
        }
        g.fill(x, y + AdminTheme.ROW_H - 1, x + w, y + AdminTheme.ROW_H, AdminTheme.PAPER_SHADE);
        if (Util.getMillis() < highlightUntil) {
            g.fill(x - 2, y, x + w + 2, y + AdminTheme.ROW_H, AdminTheme.ROW_HOVER);
            AdminTheme.outline(g, x - 2, y, w + 4, AdminTheme.ROW_H, AdminTheme.GOLD);
        }

        int labelRoom = control.widget().getX() - x - 6;
        // A gilt pip marks "not at default", the same cue whether the change is a draft or stored.
        boolean nonDefault = edited || !setting.isDefault();
        if (nonDefault) {
            g.fill(x, y + 5, x + 2, y + 12, AdminTheme.GOLD);
        }
        int textX = x + 5;
        MutableComponent label = Component.translatable(setting.nameKey()).copy();
        String badges = (setting.dangerous() ? " !" : "") + setting.applyMode().badge();
        String labelText = AdminText.clip(font, label.getString(), labelRoom - 5 - font.width(badges));
        int ink = setting.scope() == SettingScope.CLIENT ? AdminTheme.INK_2 : AdminTheme.INK;
        g.drawString(font, labelText, textX, y + 4, ink, false);
        if (!badges.isEmpty()) {
            g.drawString(font, badges, textX + font.width(labelText), y + 4, AdminTheme.RUBRIC, false);
        }

        Status status = status(edited);
        g.drawString(font, AdminText.clip(font, status.text().getString(), labelRoom - 5), textX, y + 15,
                status.color(), false);
    }

    private record Status(Component text, int color) {}

    private Status status(boolean edited) {
        if (ClientAdminState.isPending(setting.id())) {
            return new Status(Component.translatable("admin.wizards_and_beasts.status.saving"), AdminTheme.GOLD_DARK);
        }
        ClientAdminState.Feedback feedback = ClientAdminState.feedback(setting.id());
        long age = feedback == null ? Long.MAX_VALUE : Util.getMillis() - feedback.atMillis();
        if (feedback != null && feedback.kind() == ClientAdminState.Feedback.Kind.REJECTED && !edited) {
            return new Status(refusal(feedback.result()), AdminTheme.BAD);
        }
        if (edited) {
            return new Status(Component.translatable("admin.wizards_and_beasts.status.edited"), AdminTheme.GOLD_DARK);
        }
        if (feedback != null && feedback.kind() == ClientAdminState.Feedback.Kind.APPLIED && age < SAVED_FLASH_MS) {
            if (feedback.result().restartRequired()) {
                return new Status(Component.translatable("admin.wizards_and_beasts.status.saved_restart"), AdminTheme.GOOD);
            }
            if (setting.applyMode() != at.koopro.wizardsandbeasts.admin.config.ApplyMode.RUNTIME) {
                return new Status(Component.translatable("admin.wizards_and_beasts.status.saved_mode",
                        Component.translatable(setting.applyMode().labelKey())), AdminTheme.GOOD);
            }
            return new Status(Component.translatable("admin.wizards_and_beasts.status.saved"), AdminTheme.GOOD);
        }
        if (feedback != null && feedback.kind() == ClientAdminState.Feedback.Kind.REMOTE && age < REMOTE_FLASH_MS) {
            return new Status(Component.translatable("admin.wizards_and_beasts.status.remote"), AdminTheme.INK_2);
        }
        if (setting.scope() == SettingScope.CLIENT) {
            return new Status(Component.translatable("admin.wizards_and_beasts.status.client"), AdminTheme.INK_3);
        }
        if (!setting.editable()) {
            return new Status(Component.translatable("admin.wizards_and_beasts.status.locked"), AdminTheme.INK_3);
        }
        if (setting.applyMode() != at.koopro.wizardsandbeasts.admin.config.ApplyMode.RUNTIME) {
            // Said on the row itself, not only in the tooltip: an administrator must not discover after the fact
            // that a switch only reaches new chunks or the next restart.
            return new Status(Component.translatable("admin.wizards_and_beasts.status.default_mode",
                    Component.translatable(setting.applyMode().labelKey()), shortValue(setting, setting.defaultValue())),
                    AdminTheme.RUBRIC);
        }
        return new Status(Component.translatable("admin.wizards_and_beasts.status.default",
                shortValue(setting, setting.defaultValue())), setting.isDefault() ? AdminTheme.INK_3 : AdminTheme.INK_2);
    }

    private static Component refusal(AdminResult result) {
        Component reason = result.rejection() != null
                ? Component.translatable(result.rejection().translationKey())
                : Component.translatable(result.detailKey() == null
                        ? "admin.wizards_and_beasts.status.timeout" : result.detailKey());
        MutableComponent text = Component.literal("✖ ").append(reason);
        if (result.rejection() != null && result.detailKey() != null) {
            text.append(" ").append(Component.translatable(result.detailKey()));
        }
        return text;
    }

    private static Component shortValue(AdminSettingDescriptor setting, String value) {
        return AdminTooltip.display(setting, value);
    }
}
