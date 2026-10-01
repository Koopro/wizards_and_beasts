package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.client.admin.ClientAdminState;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTheme;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTooltip;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminValueRow;
import at.koopro.wizardsandbeasts.network.admin.AdminSettingDescriptor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * A handful of ordinary setting rows at fixed positions, for a bespoke page that shows a few settings beside something
 * else (a preview). Same rows, drafts, tooltips and refresh as {@link SettingsPanel}, without its scrolling list.
 */
@NullMarked
final class PlacedRows {

    private final List<AdminValueRow> rows = new ArrayList<>();

    void clear() {
        rows.clear();
    }

    /** Adds the row for {@code id} at {@code (x, y)} if the client knows the setting; returns the next free y. */
    int add(AdminPanelHost host, Font font, Identifier id, int x, int y, int w) {
        AdminSettingDescriptor setting = ClientAdminState.get(id);
        if (setting == null) {
            return y;
        }
        AdminValueRow row = AdminRowFactory.build(host, font, setting, w, changed -> refresh(host));
        row.place(x, y, w);
        rows.add(row);
        return y + AdminTheme.ROW_H;
    }

    boolean isEmpty() {
        return rows.isEmpty();
    }

    void refresh(AdminPanelHost host) {
        for (AdminValueRow row : rows) {
            AdminRowFactory.refresh(host, row);
        }
    }

    void render(AdminPanelHost host, GuiGraphics g, Font font, int mouseX, int mouseY) {
        for (AdminValueRow row : rows) {
            row.render(g, font, mouseX, mouseY, host.edits().isEdited(row.setting().id()));
        }
    }

    boolean renderTooltip(GuiGraphics g, Font font, int mouseX, int mouseY) {
        for (AdminValueRow row : rows) {
            if (row.labelHovered(mouseX, mouseY)) {
                g.setTooltipForNextFrame(font, AdminTooltip.forSetting(font, row.setting()), mouseX, mouseY);
                return true;
            }
        }
        return false;
    }

    List<AdminSettingDescriptor> settings() {
        List<AdminSettingDescriptor> out = new ArrayList<>();
        rows.forEach(row -> out.add(row.setting()));
        return out;
    }
}
