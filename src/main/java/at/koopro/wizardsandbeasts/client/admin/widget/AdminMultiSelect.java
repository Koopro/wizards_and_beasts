package at.koopro.wizardsandbeasts.client.admin.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A checklist: many things, each on or off, edited together.
 *
 * <p>Holds two sets — what the server says ({@link #setItems}) and what the administrator has ticked — and shows both:
 * an item whose tick differs from the server carries a gilt edit mark, as a draft row does in a settings list. It
 * never sends anything; {@link #changes()} is the draft, and the panel that owns it sends it through the server
 * (where every change is checked, may be held for confirmation, and comes back as the stored value).
 *
 * <p>Click a row to toggle it; Space toggles the focused row, ↑↓ move, PageUp/PageDown page, the wheel scrolls. The
 * filter ({@link #setFilter}) narrows the rows shown; "all" and "none" act on the shown rows only, so "untick every
 * Dark Arts spell" is a filter and a click.
 */
@NullMarked
public final class AdminMultiSelect extends AbstractWidget {

    /** One row: the id, its label, a quiet note at the right (a category, "custom"). */
    public record Item(String id, Component label, String note) {}

    private static final int ROW_H = 12;
    private static final int BOX = 8;

    private List<Item> items = List.of();
    private final Set<String> server = new HashSet<>();
    private final Set<String> ticked = new HashSet<>();
    private final Consumer<Integer> onChange;
    private String filter = "";
    private int scroll;
    private int cursor;

    public AdminMultiSelect(int x, int y, int w, int h, Consumer<Integer> onChange) {
        super(x, y, w, h, Component.empty());
        this.onChange = onChange;
    }

    /**
     * Replaces the rows and the server's state. Ticks the administrator changed and the server has not since
     * contradicted are kept; a change the server has now applied (or refused) is dropped, because its row now shows
     * the server's word.
     */
    public void setItems(List<Item> next, Set<String> serverOn) {
        Map<String, Boolean> drafts = new LinkedHashMap<>(changes());
        items = List.copyOf(next);
        server.clear();
        server.addAll(serverOn);
        ticked.clear();
        ticked.addAll(serverOn);
        for (Map.Entry<String, Boolean> draft : drafts.entrySet()) {
            boolean known = items.stream().anyMatch(i -> i.id().equals(draft.getKey()));
            if (known && draft.getValue() != serverOn.contains(draft.getKey())) {
                if (draft.getValue()) {
                    ticked.add(draft.getKey());
                } else {
                    ticked.remove(draft.getKey());
                }
            }
        }
        clampScroll();
    }

    public void setFilter(String text) {
        filter = text.trim().toLowerCase(Locale.ROOT);
        scroll = 0;
        cursor = 0;
    }

    /** id → new state, for every row whose tick differs from the server. */
    public Map<String, Boolean> changes() {
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (Item item : items) {
            boolean on = ticked.contains(item.id());
            if (on != server.contains(item.id())) {
                out.put(item.id(), on);
            }
        }
        return out;
    }

    public void revert() {
        ticked.clear();
        ticked.addAll(server);
        onChange.accept(0);
    }

    /** Ticks or unticks every row the filter shows. */
    public void setShown(boolean on) {
        for (Item item : shown()) {
            if (on) {
                ticked.add(item.id());
            } else {
                ticked.remove(item.id());
            }
        }
        onChange.accept(changes().size());
    }

    public List<Item> shown() {
        if (filter.isEmpty()) {
            return items;
        }
        List<Item> out = new ArrayList<>();
        for (Item item : items) {
            if (item.id().toLowerCase(Locale.ROOT).contains(filter)
                    || item.label().getString().toLowerCase(Locale.ROOT).contains(filter)
                    || item.note().toLowerCase(Locale.ROOT).contains(filter)) {
                out.add(item);
            }
        }
        return out;
    }

    public Component labelOf(String id) {
        for (Item item : items) {
            if (item.id().equals(id)) {
                return item.label();
            }
        }
        return Component.literal(id);
    }

    private int visibleRows() {
        return Math.max(1, (getHeight() - 2) / ROW_H);
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, shown().size() - visibleRows())));
    }

    private void toggle(Item item) {
        if (!ticked.remove(item.id())) {
            ticked.add(item.id());
        }
        onChange.accept(changes().size());
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        AdminTheme.inset(g, getX(), getY(), getWidth(), getHeight());
        List<Item> rows = shown();
        if (rows.isEmpty()) {
            g.drawString(font, Component.translatable("admin.wizards_and_beasts.picker.no_match"), getX() + 6, getY() + 6,
                    AdminTheme.INK_3, false);
            return;
        }
        g.enableScissor(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1);
        int listW = getWidth() - 6;
        for (int i = 0; i < visibleRows() && scroll + i < rows.size(); i++) {
            Item item = rows.get(scroll + i);
            int ry = getY() + 1 + i * ROW_H;
            boolean hot = active && mouseX >= getX() && mouseX < getX() + listW && mouseY >= ry && mouseY < ry + ROW_H;
            if (hot || (isFocused() && scroll + i == cursor)) {
                g.fill(getX() + 1, ry, getX() + listW, ry + ROW_H, AdminTheme.ROW_HOVER);
            }
            boolean on = ticked.contains(item.id());
            boolean edited = on != server.contains(item.id());
            int bx = getX() + 4;
            int by = ry + (ROW_H - BOX) / 2;
            AdminTheme.inset(g, bx, by, BOX, BOX);
            if (on) {
                g.fill(bx + 2, by + 2, bx + BOX - 2, by + BOX - 2, active ? AdminTheme.INK : AdminTheme.INK_3);
            }
            if (edited) {
                // The draft mark a settings row carries: this tick is not the server's yet.
                g.fill(getX() + 1, ry + 1, getX() + 3, ry + ROW_H - 1, AdminTheme.GOLD);
            }
            int noteW = item.note().isEmpty() ? 0 : font.width(item.note()) + 6;
            String label = item.label().getString();
            int room = listW - (bx + BOX + 4 - getX()) - noteW - 2;
            if (font.width(label) > room) {
                label = font.plainSubstrByWidth(label, Math.max(0, room - font.width("…"))) + "…";
            }
            g.drawString(font, label, bx + BOX + 4, ry + 2, edited ? AdminTheme.INK : on ? AdminTheme.INK_2 : AdminTheme.INK_3, false);
            if (noteW > 0) {
                g.drawString(font, item.note(), getX() + listW - 2 - font.width(item.note()), ry + 2, AdminTheme.INK_3, false);
            }
        }
        g.disableScissor();
        if (rows.size() > visibleRows()) {
            int trackH = getHeight() - 2;
            int thumbH = Math.max(8, trackH * visibleRows() / rows.size());
            int thumbY = getY() + 1 + (trackH - thumbH) * scroll / Math.max(1, rows.size() - visibleRows());
            g.fill(getX() + getWidth() - 4, thumbY, getX() + getWidth() - 2, thumbY + thumbH, AdminTheme.GOLD_DARK);
        }
        AdminTheme.focusRing(g, this);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!active || !visible || !isMouseOver(event.x(), event.y())) {
            return false;
        }
        setFocused(true);
        int index = (int) ((event.y() - getY() - 1) / ROW_H) + scroll;
        List<Item> rows = shown();
        if (index >= 0 && index < rows.size()) {
            cursor = index;
            toggle(rows.get(index));
            playDownSound(Minecraft.getInstance().getSoundManager());
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        scroll -= (int) Math.signum(scrollY) * 3;
        clampScroll();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!active || !isFocused()) {
            return false;
        }
        List<Item> rows = shown();
        if (rows.isEmpty()) {
            return false;
        }
        switch (event.key()) {
            case GLFW.GLFW_KEY_DOWN -> cursor = Math.min(rows.size() - 1, cursor + 1);
            case GLFW.GLFW_KEY_UP -> cursor = Math.max(0, cursor - 1);
            case GLFW.GLFW_KEY_PAGE_DOWN -> cursor = Math.min(rows.size() - 1, cursor + visibleRows());
            case GLFW.GLFW_KEY_PAGE_UP -> cursor = Math.max(0, cursor - visibleRows());
            case GLFW.GLFW_KEY_SPACE -> toggle(rows.get(Math.min(cursor, rows.size() - 1)));
            default -> {
                return false;
            }
        }
        if (cursor < scroll) {
            scroll = cursor;
        } else if (cursor >= scroll + visibleRows()) {
            scroll = cursor - visibleRows() + 1;
        }
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.literal(changes().size() + " changed of " + items.size()));
    }
}
