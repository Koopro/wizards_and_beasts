package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.AdminLangKeys;
import at.koopro.wizardsandbeasts.admin.config.SettingScope;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminState;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminCheckbox;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminSectionHeader;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminText;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTheme;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTooltip;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminValueRow;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminWarning;
import at.koopro.wizardsandbeasts.network.admin.AdminSettingDescriptor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The generic section panel: one {@link AdminValueRow} per setting the server filed under this section, in
 * the server's order, each with the control {@link AdminControlFactory} picks for it.
 *
 * <p>Nothing here names a setting. A section gains a row when the server registers a setting in it — which
 * is the whole point: new settings need a catalog entry and lang keys, never screen code.
 */
@NullMarked
final class SettingsPanel implements AdminPanel {

    private static final int HEADER_H = 32;
    private static final int SCROLLBAR_W = 4;

    private final AdminCategory section;
    private final List<AdminValueRow> rows = new ArrayList<>();
    private @Nullable AdminPanelHost host;
    private boolean onlyChanged;
    private double scroll;
    private int x;
    private int y;
    private int w;
    private int h;
    private int rowsTop;
    private int rowW;

    /** Shows only the settings this accepts; a section split into tabs gives each tab its own slice. */
    private final java.util.function.Predicate<Identifier> filter;
    private final @Nullable Component title;
    private final @Nullable Component summary;
    /** A paragraph under the heading: what the page cannot change, or a rule that shapes how its rows behave. */
    private @Nullable Component note;
    /** Look for rows in every section, not only this one (a travel page showing the Floo fee filed under Economy). */
    private boolean anySection;
    private int noteTop;

    SettingsPanel(AdminCategory section) {
        this(section, id -> true, null, null);
    }

    /** One tab's slice of a section, under its own heading. */
    SettingsPanel(AdminCategory section, java.util.function.Predicate<Identifier> filter,
                  @Nullable Component title, @Nullable Component summary) {
        this.section = section;
        this.filter = filter;
        this.title = title;
        this.summary = summary;
    }

    SettingsPanel note(Component note) {
        this.note = note;
        return this;
    }

    /** Rows may come from any section; the filter alone decides. */
    SettingsPanel anySection() {
        this.anySection = true;
        return this;
    }

    private int noteHeight(Font font) {
        return note == null ? 0 : font.split(note, w).size() * 10 + AdminTheme.GAP;
    }

    @Override
    public AdminCategory section() {
        return section;
    }

    @Override
    public void init(AdminPanelHost host, int x, int y, int w, int h) {
        this.host = host;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        rows.clear();
        Font font = host.font();

        host.addPanelWidget(new AdminCheckbox(x + w - 4 - checkboxWidth(font), y,
                Component.translatable("admin.wizards_and_beasts.filter.only_changed"), onlyChanged, checked -> {
                    onlyChanged = checked;
                    scroll = 0;
                    host.requestRebuild();
                }));

        noteTop = y + HEADER_H + clientNoticeHeight(font);
        rowsTop = noteTop + noteHeight(font);
        List<AdminSettingDescriptor> visible = visibleSettings();
        rowW = w - (contentHeight(visible.size()) > viewportHeight() ? SCROLLBAR_W + 4 : 0);
        for (AdminSettingDescriptor setting : visible) {
            rows.add(buildRow(host, font, setting));
        }
        layoutRows();
    }

    private static int checkboxWidth(Font font) {
        return 13 + font.width(Component.translatable("admin.wizards_and_beasts.filter.only_changed"));
    }

    /**
     * The section's global settings. Entity-scoped ones ({@code heritage/<id>/selectable}) belong to their
     * entity's page — a heritage's detail view — and would read as eight unlabelled "Selectable" rows here.
     */
    private List<AdminSettingDescriptor> sectionSettings() {
        List<AdminSettingDescriptor> out = new ArrayList<>();
        for (AdminSettingDescriptor setting : anySection ? ClientAdminState.all() : ClientAdminState.inCategory(section)) {
            if (!AdminLangKeys.entityScoped(setting.id().getPath()) && filter.test(setting.id())) {
                out.add(setting);
            }
        }
        return out;
    }

    private List<AdminSettingDescriptor> visibleSettings() {
        List<AdminSettingDescriptor> out = new ArrayList<>();
        for (AdminSettingDescriptor setting : sectionSettings()) {
            if (!onlyChanged || !setting.isDefault() || (host != null && host.edits().isEdited(setting.id()))) {
                out.add(setting);
            }
        }
        return out;
    }

    private AdminValueRow buildRow(AdminPanelHost host, Font font, AdminSettingDescriptor setting) {
        return AdminRowFactory.build(host, font, setting, rowW, this::refreshRow);
    }

    private void refreshRow(Identifier id) {
        if (host == null) {
            return;
        }
        for (AdminValueRow row : rows) {
            if (row.setting().id().equals(id)) {
                AdminRowFactory.refresh(host, row);
            }
        }
    }

    @Override
    public void onServerState() {
        if (host == null) {
            return;
        }
        List<AdminSettingDescriptor> now = visibleSettings();
        if (now.size() != rows.size()) {
            host.requestRebuild();
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            if (!rows.get(i).setting().id().equals(now.get(i).id())) {
                host.requestRebuild();
                return;
            }
        }
        for (AdminValueRow row : rows) {
            refreshRow(row.setting().id());
        }
    }

    @Override
    public boolean reveal(Identifier settingId) {
        for (int i = 0; i < rows.size(); i++) {
            AdminValueRow row = rows.get(i);
            if (row.setting().id().equals(settingId)) {
                scroll = Mth.clamp(i * AdminTheme.ROW_H - viewportHeight() / 3.0, 0, maxScroll());
                layoutRows();
                row.highlight();
                return true;
            }
        }
        if (onlyChanged && host != null) {
            for (AdminSettingDescriptor setting : sectionSettings()) {
                if (setting.id().equals(settingId)) {
                    // Hidden by "only changed": show everything, and the caller tries again after the rebuild.
                    onlyChanged = false;
                    host.requestRebuild();
                    return false;
                }
            }
        }
        return false;
    }

    @Override
    public boolean hasSectionReset() {
        return true;
    }

    @Override
    public List<AdminSettingDescriptor> resettable() {
        List<AdminSettingDescriptor> out = new ArrayList<>();
        for (AdminSettingDescriptor setting : sectionSettings()) {
            if (setting.editable() && !setting.isDefault()) {
                out.add(setting);
            }
        }
        return out;
    }

    // ── layout and scrolling ──

    private int clientNoticeHeight(Font font) {
        return hasClientSettings() ? AdminWarning.measure(font, w, clientNotice()) + AdminTheme.GAP : 0;
    }

    private boolean hasClientSettings() {
        for (AdminSettingDescriptor setting : sectionSettings()) {
            if (setting.scope() == SettingScope.CLIENT) {
                return true;
            }
        }
        return false;
    }

    private static Component clientNotice() {
        return Component.translatable("admin.wizards_and_beasts.notice.client_settings");
    }

    private int viewportHeight() {
        return y + h - rowsTop;
    }

    private int contentHeight(int rowCount) {
        return rowCount * AdminTheme.ROW_H;
    }

    private double maxScroll() {
        return Math.max(0, contentHeight(rows.size()) - viewportHeight());
    }

    private void layoutRows() {
        scroll = Mth.clamp(scroll, 0, maxScroll());
        int bottom = y + h;
        for (int i = 0; i < rows.size(); i++) {
            AdminValueRow row = rows.get(i);
            int rowY = rowsTop + i * AdminTheme.ROW_H - (int) scroll;
            row.place(x, rowY, rowW);
            // Only whole rows are shown: widgets have no clipping, so a half-visible slider would draw
            // over the header or the footer.
            row.setVisible(rowY >= rowsTop && rowY + AdminTheme.ROW_H <= bottom);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaY) {
        if (mouseX < x || mouseX >= x + w || mouseY < rowsTop || mouseY >= y + h || maxScroll() <= 0) {
            return false;
        }
        scroll -= deltaY * AdminTheme.ROW_H;
        layoutRows();
        return true;
    }

    // ── drawing ──

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (host == null) {
            return;
        }
        Font font = host.font();
        AdminSectionHeader.render(g, font, x, y, w - checkboxWidth(font) - 8,
                title != null ? title : Component.translatable(section.nameKey()),
                summary != null ? summary : Component.translatable(section.summaryKey()));
        if (hasClientSettings()) {
            AdminWarning.render(g, font, x, y + HEADER_H, w, clientNotice(), AdminWarning.Severity.INFO);
        }
        if (note != null) {
            int ny = noteTop;
            for (net.minecraft.util.FormattedCharSequence line : font.split(note, w)) {
                g.drawString(font, line, x, ny, AdminTheme.INK_3, false);
                ny += 10;
            }
        }
        if (rows.isEmpty()) {
            AdminText.centred(g, font, Component.translatable("admin.wizards_and_beasts.filter.empty"),
                    x + w / 2, rowsTop + 12, AdminTheme.INK_3);
            return;
        }
        for (AdminValueRow row : rows) {
            row.render(g, font, mouseX, mouseY, host.edits().isEdited(row.setting().id()));
        }
        double max = maxScroll();
        if (max > 0) {
            int trackX = x + w - SCROLLBAR_W;
            int trackH = viewportHeight();
            g.fill(trackX, rowsTop, trackX + SCROLLBAR_W, rowsTop + trackH, AdminTheme.PAPER_SHADE);
            int thumbH = Math.max(12, trackH * trackH / contentHeight(rows.size()));
            int thumbY = rowsTop + (int) ((trackH - thumbH) * (scroll / max));
            g.fill(trackX, thumbY, trackX + SCROLLBAR_W, thumbY + thumbH, AdminTheme.GOLD);
        }
    }

    @Override
    public void renderOverlay(GuiGraphics g, int mouseX, int mouseY) {
        if (host == null) {
            return;
        }
        for (AdminValueRow row : rows) {
            if (row.labelHovered(mouseX, mouseY)) {
                g.setTooltipForNextFrame(host.font(), AdminTooltip.forSetting(host.font(), row.setting()), mouseX, mouseY);
                return;
            }
        }
    }
}
