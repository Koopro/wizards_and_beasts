package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.config.SettingKind;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminState;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminSectionHeader;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminText;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTextField;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTheme;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTooltip;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminValueRow;
import at.koopro.wizardsandbeasts.network.admin.AdminSettingDescriptor;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellFact;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A searchable list on the left and the selected entry's scrolling page on the right — the shape of the Wands and
 * Travel browsers. A subclass names its entries and builds a page out of headers, wrapped text, facts and ordinary
 * setting rows; the list, scrolling, row refresh and tooltips are here. Every editable value on a page is a setting
 * descriptor edited as a draft and sent through Apply, exactly as in the other sections.
 */
@NullMarked
abstract class AdminBrowserPanel<T> implements AdminPanel {

    protected static final int ROW_H = 13;
    protected static final int SCROLLBAR_W = 4;
    protected static final int SUB_H = 14;
    protected static final int LINE = 10;
    private static final int LIST_HEADER_H = 18;

    protected @Nullable AdminPanelHost host;
    protected int x;
    protected int y;
    protected int w;
    protected int h;
    protected int listW;
    protected int detailX;
    protected int detailW;
    private double listScroll;
    private double detailScroll;
    private List<T> visible = List.of();
    private @Nullable T shown;
    private final List<PlacedRow> rows = new ArrayList<>();
    private final List<Placed> placed = new ArrayList<>();
    private final List<Text> texts = new ArrayList<>();
    private final List<Header> headers = new ArrayList<>();
    private int docHeight;

    private record Placed(AbstractWidget widget, int docX, int docY) {}

    private record PlacedRow(AdminValueRow row, int docY) {}

    private record Text(FormattedCharSequence text, int docX, int docY, int color) {}

    private record Header(Component title, int docY) {}

    // ── what a subclass supplies ──

    protected abstract List<T> entries();

    protected abstract String idOf(T entry);

    protected abstract Component nameOf(T entry);

    protected abstract boolean enabledOf(T entry);

    /** A colour swatch before the name, or 0 for none. */
    protected int swatchOf(T entry) {
        return 0;
    }

    /** Whether rows leave room for a swatch, so names line up whether or not an entry has one. */
    protected boolean hasSwatches() {
        return false;
    }

    /** Short marks at the row's right edge ("✕" withdrawn, "•" overridden). */
    protected abstract String badgesOf(T entry);

    protected abstract @Nullable String selected();

    protected abstract void setSelected(String id);

    /** Whether the section state is stale; if so {@link #request} asks the server again. */
    protected abstract boolean stale();

    protected abstract void request();

    /** Lays the page out below the page title, starting at {@code doc}; returns the page height. */
    protected abstract int buildPage(AdminPanelHost host, Font font, T entry, int doc, int rowW);

    /** Draws what is not text or a row (a preview), with the page's top at {@code top}. */
    protected void renderPage(GuiGraphics g, Font font, T entry, int top, int width, int mouseX, int mouseY) {}

    protected abstract Component pickPrompt();

    /** A one-line summary under the page title. */
    protected abstract String subtitleOf(T entry);

    protected List<Component> tooltipOf(T entry) {
        return List.of(nameOf(entry), Component.literal(subtitleOf(entry)));
    }

    /**
     * Whether the server has answered, so an empty {@link #entries()} means "there are none" rather than "still
     * waiting". The default trusts a non-empty list only; a subclass whose state knows better says so.
     */
    protected boolean loaded() {
        return !entries().isEmpty();
    }

    /** What an answered-but-empty list says ("No brooms registered."). */
    protected Component emptyMessage() {
        return Component.translatable("admin.wizards_and_beasts.empty.list");
    }

    @Override
    public @Nullable Component crumb() {
        return shown == null ? null : nameOf(shown);
    }

    // ── layout ──

    private String search = "";

    @Override
    public void init(AdminPanelHost host, int x, int y, int w, int h) {
        this.host = host;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.listW = Mth.clamp(w * 32 / 100, 110, 160);
        this.detailX = x + listW + AdminTheme.PAD;
        this.detailW = w - listW - AdminTheme.PAD;
        Font font = host.font();
        host.addPanelWidget(AdminTextField.search(font, x, y, listW, search, text -> {
            search = text;
            listScroll = 0;
            refilter();
        }));
        if (stale()) {
            request();
        }
        refilter();
        revealSelected();
        buildDetail(host, font);
    }

    private void refilter() {
        String needle = search.trim().toLowerCase(Locale.ROOT);
        List<T> out = new ArrayList<>();
        for (T entry : entries()) {
            if (needle.isEmpty() || idOf(entry).contains(needle)
                    || nameOf(entry).getString().toLowerCase(Locale.ROOT).contains(needle)) {
                out.add(entry);
            }
        }
        visible = out;
        listScroll = Mth.clamp(listScroll, 0, maxListScroll());
    }

    private void revealSelected() {
        String selected = selected();
        if (selected == null) {
            return;
        }
        for (int i = 0; i < visible.size(); i++) {
            if (idOf(visible.get(i)).equals(selected)) {
                double rowTop = i * ROW_H;
                if (rowTop < listScroll || rowTop + ROW_H > listScroll + (y + h - listTop())) {
                    listScroll = Mth.clamp(rowTop - (y + h - listTop()) / 2.0, 0, maxListScroll());
                }
                return;
            }
        }
    }

    private int listTop() {
        return y + LIST_HEADER_H;
    }

    private double maxListScroll() {
        return Math.max(0, visible.size() * ROW_H - (y + h - listTop()));
    }

    protected @Nullable T find(@Nullable String id) {
        if (id == null) {
            return null;
        }
        for (T entry : entries()) {
            if (idOf(entry).equals(id)) {
                return entry;
            }
        }
        return null;
    }

    private void buildDetail(AdminPanelHost host, Font font) {
        rows.clear();
        placed.clear();
        texts.clear();
        headers.clear();
        T entry = find(selected());
        shown = entry;
        if (entry == null) {
            docHeight = 0;
            return;
        }
        docHeight = buildPage(host, font, entry, 26, detailW - SCROLLBAR_W - 4) + 6;
        layoutDetail();
    }

    // ── page building blocks ──

    protected int header(int doc, Component title) {
        headers.add(new Header(title, doc));
        return doc + SUB_H;
    }

    protected int row(AdminPanelHost host, Font font, Identifier id, int doc, int rowW) {
        AdminSettingDescriptor setting = ClientAdminState.get(id);
        if (setting == null) {
            return doc;
        }
        rows.add(new PlacedRow(AdminRowFactory.build(host, font, setting, rowW, changed -> refreshRows()), doc));
        return doc + AdminTheme.ROW_H;
    }

    protected int wrap(Font font, int doc, int width, Component text, int color) {
        for (FormattedCharSequence line : font.split(text, width)) {
            texts.add(new Text(line, 0, doc, color));
            doc += LINE;
        }
        return doc;
    }

    protected int facts(Font font, int doc, int rowW, List<AdminSpellFact> facts) {
        for (AdminSpellFact fact : facts) {
            Component value = fact.valueTranslatable() ? Component.translatable(fact.value()) : Component.literal(fact.value());
            doc = wrap(font, doc, rowW, Component.translatable(fact.labelKey()).append(": ").append(value), AdminTheme.INK_2);
        }
        return doc;
    }

    protected void place(AbstractWidget widget, int docX, int docY) {
        if (host != null) {
            host.addPanelWidget(widget);
        }
        placed.add(new Placed(widget, docX, docY));
    }

    private double maxDetailScroll() {
        return Math.max(0, docHeight - h);
    }

    private void layoutDetail() {
        detailScroll = Mth.clamp(detailScroll, 0, maxDetailScroll());
        int rowW = detailW - SCROLLBAR_W - 4;
        for (PlacedRow placedRow : rows) {
            int rowY = y + placedRow.docY() - (int) detailScroll;
            placedRow.row().place(detailX, rowY, rowW);
            placedRow.row().setVisible(rowY >= y && rowY + AdminTheme.ROW_H <= y + h);
        }
        for (Placed p : placed) {
            int widgetY = y + p.docY() - (int) detailScroll;
            p.widget().setPosition(detailX + p.docX(), widgetY);
            p.widget().visible = widgetY >= y && widgetY + p.widget().getHeight() <= y + h;
        }
    }

    @Override
    public boolean reveal(Identifier settingId) {
        for (PlacedRow placedRow : rows) {
            if (placedRow.row().setting().id().equals(settingId)) {
                detailScroll = Mth.clamp(placedRow.docY() - h / 3.0, 0, maxDetailScroll());
                layoutDetail();
                placedRow.row().highlight();
                return true;
            }
        }
        return false;
    }

    protected void refreshRows() {
        if (host == null) {
            return;
        }
        for (PlacedRow placedRow : rows) {
            AdminRowFactory.refresh(host, placedRow.row());
        }
    }

    // ── state and input ──

    @Override
    public void onServerState() {
        if (host == null) {
            return;
        }
        if (stale()) {
            request();
        }
        refilter();
        T entry = find(selected());
        if (entry != shown) {
            // A fresh reply (or the entry went away): its page shows server values, so build it again.
            host.requestRebuild();
            return;
        }
        refreshRows();
    }

    private void select(String id) {
        if (host == null || id.equals(selected())) {
            return;
        }
        setSelected(id);
        detailScroll = 0;
        host.requestRebuild();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX < x || mouseX >= x + listW - SCROLLBAR_W || mouseY < listTop() || mouseY >= y + h) {
            return false;
        }
        int index = (int) ((mouseY - listTop() + listScroll) / ROW_H);
        if (index >= 0 && index < visible.size()) {
            select(idOf(visible.get(index)));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaY) {
        if (mouseY < y || mouseY >= y + h) {
            return false;
        }
        if (mouseX >= x && mouseX < x + listW && mouseY >= listTop()) {
            listScroll = Mth.clamp(listScroll - deltaY * ROW_H * 3, 0, maxListScroll());
            return true;
        }
        if (mouseX >= detailX && mouseX < detailX + detailW && shown != null) {
            detailScroll = Mth.clamp(detailScroll - deltaY * AdminTheme.ROW_H, 0, maxDetailScroll());
            layoutDetail();
            return true;
        }
        return false;
    }

    // ── drawing ──

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (host == null) {
            return;
        }
        Font font = host.font();
        renderList(g, font, mouseX, mouseY);
        g.fill(detailX - AdminTheme.PAD / 2 - 1, y, detailX - AdminTheme.PAD / 2, y + h, AdminTheme.PAPER_RULE);
        g.enableScissor(detailX, y, detailX + detailW, y + h);
        renderDetail(g, font, mouseX, mouseY);
        g.disableScissor();
    }

    private void renderList(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int top = listTop();
        int bottom = y + h;
        if (visible.isEmpty()) {
            Component empty = AdminText.emptyList(!entries().isEmpty(), loaded(), emptyMessage());
            int lineY = top + 2;
            for (FormattedCharSequence line : font.split(empty, listW - 4)) {
                g.drawString(font, line, x + 2, lineY, AdminTheme.INK_3, false);
                lineY += LINE;
            }
            return;
        }
        g.enableScissor(x, top, x + listW, bottom);
        int rowW = listW - SCROLLBAR_W - 2;
        String selected = selected();
        for (int i = 0; i < visible.size(); i++) {
            int rowY = top + i * ROW_H - (int) listScroll;
            if (rowY + ROW_H < top || rowY > bottom) {
                continue;
            }
            T entry = visible.get(i);
            if (idOf(entry).equals(selected)) {
                g.fill(x, rowY, x + rowW, rowY + ROW_H, AdminTheme.PAPER_SELECT);
                g.fill(x, rowY, x + 2, rowY + ROW_H, AdminTheme.GOLD);
            } else if (mouseX >= x && mouseX < x + rowW && mouseY >= rowY && mouseY < rowY + ROW_H) {
                g.fill(x, rowY, x + rowW, rowY + ROW_H, AdminTheme.ROW_HOVER);
            }
            int nameX = hasSwatches() ? x + 17 : x + 5;
            int swatch = swatchOf(entry);
            if (swatch != 0) {
                g.fill(x + 4, rowY + 2, x + 13, rowY + 11, AdminTheme.INK_3);
                g.fill(x + 5, rowY + 3, x + 12, rowY + 10, swatch | 0xFF000000);
            }
            String badges = badgesOf(entry);
            int badgeW = font.width(badges);
            boolean enabled = enabledOf(entry);
            g.drawString(font, AdminText.clip(font, nameOf(entry).getString(), x + rowW - nameX - badgeW - 4), nameX, rowY + 3,
                    enabled ? AdminTheme.INK : AdminTheme.INK_3, false);
            if (!badges.isEmpty()) {
                g.drawString(font, badges, x + rowW - badgeW - 2, rowY + 3, enabled ? AdminTheme.GOLD_DARK : AdminTheme.BAD, false);
            }
        }
        g.disableScissor();
        double max = maxListScroll();
        if (max > 0) {
            int trackX = x + listW - SCROLLBAR_W;
            int trackH = bottom - top;
            g.fill(trackX, top, trackX + SCROLLBAR_W, bottom, AdminTheme.PAPER_SHADE);
            int thumbH = Math.max(12, trackH * trackH / (visible.size() * ROW_H));
            int thumbY = top + (int) ((trackH - thumbH) * (listScroll / max));
            g.fill(trackX, thumbY, trackX + SCROLLBAR_W, thumbY + thumbH, AdminTheme.GOLD);
        }
    }

    private void renderDetail(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int width = detailW - SCROLLBAR_W - 4;
        T entry = shown;
        if (entry == null) {
            g.drawString(font, selected() == null ? pickPrompt() : Component.translatable("admin.wizards_and_beasts.status.loading"),
                    detailX, y + 4, AdminTheme.INK_3, false);
            return;
        }
        int top = y - (int) detailScroll;
        g.drawString(font, AdminText.clip(font, nameOf(entry).getString(), width), detailX, top + 2, AdminTheme.RUBRIC, false);
        g.drawString(font, AdminText.clip(font, subtitleOf(entry), width), detailX, top + 13,
                enabledOf(entry) ? AdminTheme.INK_2 : AdminTheme.BAD, false);
        for (Header header : headers) {
            AdminSectionHeader.renderSub(g, font, detailX, top + header.docY(), width, header.title());
        }
        for (Text text : texts) {
            int ty = top + text.docY();
            if (ty + LINE >= y && ty <= y + h) {
                g.drawString(font, text.text(), detailX + text.docX(), ty, text.color(), false);
            }
        }
        renderPage(g, font, entry, top, width, mouseX, mouseY);
        for (PlacedRow placedRow : rows) {
            placedRow.row().render(g, font, mouseX, mouseY, host != null && host.edits().isEdited(placedRow.row().setting().id()));
        }
        double max = maxDetailScroll();
        if (max > 0) {
            int trackX = detailX + detailW - SCROLLBAR_W;
            g.fill(trackX, y, trackX + SCROLLBAR_W, y + h, AdminTheme.PAPER_SHADE);
            int thumbH = Math.max(12, h * h / Math.max(1, docHeight));
            int thumbY = y + (int) ((h - thumbH) * (detailScroll / max));
            g.fill(trackX, thumbY, trackX + SCROLLBAR_W, thumbY + thumbH, AdminTheme.GOLD);
        }
    }

    @Override
    public void renderOverlay(GuiGraphics g, int mouseX, int mouseY) {
        if (host == null) {
            return;
        }
        for (PlacedRow placedRow : rows) {
            if (placedRow.row().labelHovered(mouseX, mouseY) && mouseY >= y && mouseY < y + h) {
                g.setTooltipForNextFrame(host.font(), AdminTooltip.forSetting(host.font(), placedRow.row().setting()), mouseX, mouseY);
                return;
            }
        }
        if (mouseX >= x && mouseX < x + listW - SCROLLBAR_W && mouseY >= listTop() && mouseY < y + h) {
            int index = (int) ((mouseY - listTop() + listScroll) / ROW_H);
            if (index >= 0 && index < visible.size()) {
                g.setComponentTooltipForNextFrame(host.font(), tooltipOf(visible.get(index)), mouseX, mouseY);
            }
        }
    }
}
