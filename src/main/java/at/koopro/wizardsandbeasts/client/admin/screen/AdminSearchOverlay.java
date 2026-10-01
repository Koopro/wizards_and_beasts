package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.client.admin.search.AdminSearchIndex;
import at.koopro.wizardsandbeasts.client.admin.search.AdminSearchIndex.Entry;
import at.koopro.wizardsandbeasts.client.admin.search.AdminSearchIndex.Target;
import at.koopro.wizardsandbeasts.client.admin.search.AdminSearchSources;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminSectionHeader;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminText;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTextField;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The global search page, laid over the content area while open: a search box, then the best matches across every
 * section, each with what kind of thing it is and where it lives. ↑/↓ choose, Enter opens, Esc closes; the mouse
 * works too. The index is shared across openings and rebuilt only when what it indexes has changed.
 */
@NullMarked
final class AdminSearchOverlay {

    private static final int ROW_H = 22;
    private static final int FIELD_H = 14;
    private static final int LIMIT = 60;
    private static final String KEY = "admin.wizards_and_beasts.search.";

    private static final AdminSearchIndex INDEX = new AdminSearchIndex();
    /** The last query, kept so reopening search shows where the administrator was. */
    private static String text = "";

    private AdminSearchIndex.Result result = new AdminSearchIndex.Result(List.of(), 0);
    private int selected;
    private double scroll;
    private int x;
    private int y;
    private int w;
    private int h;

    AdminSearchOverlay() {
        AdminSearchSources.requestMissing();
        requery();
    }

    /** Builds the search box for the area {@code (x, y, w, h)}; the screen adds it as a widget and focuses it. */
    AdminTextField init(Font font, int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        AdminTextField field = new AdminTextField(font, x, y + 14, w, FIELD_H,
                at.koopro.wizardsandbeasts.admin.config.SettingKind.STRING, 48, text, typed -> {
                    text = typed;
                    selected = 0;
                    scroll = 0;
                    requery();
                });
        field.inkHint(Component.translatable(KEY + "hint.global"));
        return field;
    }

    /** New server data: rebuild the index if a source changed, and run the query again. */
    void onServerState() {
        requery();
    }

    private void requery() {
        long signature = AdminSearchSources.signature();
        if (INDEX.stale(signature)) {
            INDEX.rebuild(signature, AdminSearchSources.build());
        }
        result = INDEX.query(text, LIMIT);
        selected = Mth.clamp(selected, 0, Math.max(0, result.entries().size() - 1));
    }

    private int listTop() {
        return y + 14 + FIELD_H + 6;
    }

    private int listBottom() {
        return y + h - 12;
    }

    private int visibleRows() {
        return Math.max(1, (listBottom() - listTop()) / ROW_H);
    }

    private double maxScroll() {
        return Math.max(0, result.entries().size() * ROW_H - (listBottom() - listTop()));
    }

    /** Keys the box does not use. Returns the chosen target on Enter, else null. */
    @Nullable Target keyPressed(int key) {
        int count = result.entries().size();
        if (count == 0) {
            return null;
        }
        switch (key) {
            case GLFW.GLFW_KEY_DOWN -> select(selected + 1);
            case GLFW.GLFW_KEY_UP -> select(selected - 1);
            case GLFW.GLFW_KEY_PAGE_DOWN -> select(selected + visibleRows());
            case GLFW.GLFW_KEY_PAGE_UP -> select(selected - visibleRows());
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                return result.entries().get(selected).target();
            }
            default -> { }
        }
        return null;
    }

    /** Whether the overlay wants {@code key}; the screen passes these here before the search box sees them. */
    static boolean handles(int key) {
        return key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_PAGE_DOWN
                || key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
    }

    private void select(int index) {
        selected = Mth.clamp(index, 0, Math.max(0, result.entries().size() - 1));
        double rowTop = selected * ROW_H;
        double view = listBottom() - listTop();
        if (rowTop < scroll) {
            scroll = rowTop;
        } else if (rowTop + ROW_H > scroll + view) {
            scroll = rowTop + ROW_H - view;
        }
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < x || mouseX >= x + w || mouseY < listTop() || mouseY >= listBottom()) {
            return -1;
        }
        int index = (int) ((mouseY - listTop() + scroll) / ROW_H);
        return index >= 0 && index < result.entries().size() ? index : -1;
    }

    @Nullable Target mouseClicked(double mouseX, double mouseY) {
        int index = rowAt(mouseX, mouseY);
        return index < 0 ? null : result.entries().get(index).target();
    }

    boolean mouseScrolled(double mouseX, double mouseY, double deltaY) {
        if (mouseY < listTop() || mouseY >= listBottom()) {
            return false;
        }
        scroll = Mth.clamp(scroll - deltaY * ROW_H, 0, maxScroll());
        return true;
    }

    void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        AdminSectionHeader.renderSub(g, font, x, y, w, Component.translatable(KEY + "title"));
        int top = listTop();
        int bottom = listBottom();
        List<Entry> entries = result.entries();
        if (text.isBlank()) {
            int lineY = top + 4;
            for (FormattedCharSequence line : font.split(Component.translatable(KEY + "intro", INDEX.size()), w)) {
                g.drawString(font, line, x, lineY, AdminTheme.INK_2, false);
                lineY += font.lineHeight + 2;
            }
            g.drawString(font, AdminText.clip(font, Component.translatable(KEY + "keys").getString(), w), x, lineY + 6,
                    AdminTheme.INK_3, false);
            return;
        }
        if (entries.isEmpty()) {
            for (FormattedCharSequence line : font.split(Component.translatable(KEY + "none", text.trim()), w)) {
                g.drawString(font, line, x, top + 4, AdminTheme.INK_3, false);
                top += font.lineHeight + 2;
            }
            return;
        }
        int hovered = rowAt(mouseX, mouseY);
        g.enableScissor(x, top, x + w, bottom);
        for (int i = 0; i < entries.size(); i++) {
            int rowY = top + i * ROW_H - (int) scroll;
            if (rowY + ROW_H < top || rowY > bottom) {
                continue;
            }
            Entry entry = entries.get(i);
            if (i == selected) {
                g.fill(x, rowY, x + w, rowY + ROW_H, AdminTheme.PAPER_SELECT);
                g.fill(x, rowY, x + 2, rowY + ROW_H, AdminTheme.GOLD);
            } else if (i == hovered) {
                g.fill(x, rowY, x + w, rowY + ROW_H, AdminTheme.ROW_HOVER);
            }
            String kind = Component.translatable(entry.kind().labelKey()).getString();
            int kindW = font.width(kind);
            g.drawString(font, AdminText.clip(font, entry.title(), w - kindW - 16), x + 6, rowY + 3, AdminTheme.INK, false);
            g.drawString(font, kind, x + w - kindW - 4, rowY + 3, AdminTheme.GOLD_DARK, false);
            g.drawString(font, AdminText.clip(font, entry.detail(), w - 12), x + 6, rowY + 12, AdminTheme.INK_3, false);
            g.fill(x, rowY + ROW_H - 1, x + w, rowY + ROW_H, AdminTheme.PAPER_SHADE);
        }
        g.disableScissor();
        String count = result.total() > entries.size()
                ? Component.translatable(KEY + "count.more", entries.size(), result.total()).getString()
                : Component.translatable(KEY + "count", result.total()).getString();
        g.drawString(font, AdminText.clip(font, count + "  ·  " + Component.translatable(KEY + "keys").getString(), w),
                x, bottom + 2, AdminTheme.INK_3, false);
    }

    static void setText(String query) {
        text = query;
    }
}
