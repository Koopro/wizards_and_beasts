package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.client.admin.ClientAdminState;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminButton;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminControl;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminControlFactory;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTheme;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminValueRow;
import at.koopro.wizardsandbeasts.network.admin.AdminSettingDescriptor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.function.Consumer;

/**
 * Builds one editable {@link AdminValueRow} for any setting descriptor — a section setting or a spell's value —
 * and registers its widgets with the host. Edits become drafts in the host's edit session; nothing is sent
 * until Apply.
 */
@NullMarked
final class AdminRowFactory {

    private AdminRowFactory() {}

    /**
     * @param refresh called with the setting's id after an edit, so the owning panel can redraw that row
     */
    static AdminValueRow build(AdminPanelHost host, Font font, AdminSettingDescriptor setting, int rowW,
                               Consumer<Identifier> refresh) {
        Identifier id = setting.id();
        AdminControl control = AdminControlFactory.create(setting, font, 0, 0, AdminValueRow.controlWidth(rowW),
                AdminTheme.CONTROL_H, host.edits().shown(setting), value -> {
                    AdminSettingDescriptor current = ClientAdminState.get(id);
                    if (current != null) {
                        host.edits().edit(current, value);
                        refresh.accept(id);
                    }
                });
        AdminButton reset = new AdminButton(0, 0, AdminValueRow.resetWidth(), AdminTheme.CONTROL_H,
                Component.literal("↺"), AdminButton.Tone.QUIET, () -> {
                    AdminSettingDescriptor current = ClientAdminState.get(id);
                    if (current != null && current.editable()) {
                        host.edits().edit(current, current.defaultValue());
                        refresh.accept(id);
                    }
                });
        reset.setTooltip(Tooltip.create(Component.translatable("admin.wizards_and_beasts.button.reset_row.tooltip")));
        host.addPanelWidget(control.widget());
        host.addPanelWidget(reset);
        AdminValueRow row = new AdminValueRow(setting, control, reset);
        row.update(setting, host.edits().shown(setting), host.edits().isEdited(id));
        return row;
    }

    /** Re-reads a row's setting and redraws it with the draft, pending or server value. */
    static void refresh(AdminPanelHost host, AdminValueRow row) {
        AdminSettingDescriptor fresh = ClientAdminState.get(row.setting().id());
        if (fresh != null) {
            row.update(fresh, host.edits().shown(fresh), host.edits().isEdited(fresh.id()));
        }
    }
}
