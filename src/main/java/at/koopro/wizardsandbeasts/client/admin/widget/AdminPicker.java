package at.koopro.wizardsandbeasts.client.admin.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Pick one thing out of many: a spell, an effect, a lineage, a creature, a player, a resource id.
 *
 * <p>Closed, it shows the choice and a chevron. Clicked (or Enter / Space when focused), a list opens under it with
 * a filter line at its head: typing narrows the list in place, ↑↓ move, Enter picks, Esc closes, the wheel scrolls.
 * The panel this used to replace was a filter field next to a ◀ ▶ cycler, which meant clicking through fifty
 * effects one at a time to reach the one you had already typed.
 *
 * <p>Options come from the server ({@code id} is what is sent back; {@code label} is what is shown and also
 * matched). The picker only chooses; whatever the panel does with the choice goes through the server.
 * Register it before the widgets its list may cover, so it is offered clicks first; the host screen draws the open
 * list last with {@link #renderPopup}.
 */
@NullMarked
public final class AdminPicker extends AbstractWidget {

    /** One choice: the id sent back, the label shown, and an optional quiet note (a namespace, a rarity). */
    public record Option(String id, Component label, String note) {
        public Option(String id, Component label) {
            this(id, label, "");
        }
    }

    private static final int ROW_H = 12;
    private static final int MAX_ROWS = 8;

    private List<Option> options;
    private final Consumer<String> onPick;
    private final Component placeholder;
    private String selected;
    private String filter = "";
    private boolean open;
    private int scroll;
    private int cursor;

    public AdminPicker(int x, int y, int w, int h, List<Option> options, String selected, Component placeholder,
                       Consumer<String> onPick) {
        super(x, y, w, h, Component.empty());
        this.options = List.copyOf(options);
        this.selected = selected;
        this.placeholder = placeholder;
        this.onPick = onPick;
    }

    public String selected() {
        return selected;
    }

    /** Replaces the option list (a fresh server reply); keeps the choice when it is still offered. */
    public void setOptions(List<Option> next) {
        options = List.copyOf(next);
        if (options.stream().noneMatch(o -> o.id().equals(selected))) {
            selected = "";
        }
        scroll = 0;
        cursor = 0;
    }

    public boolean isOpen() {
        return open;
    }

    private List<Option> matching() {
        String needle = filter.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return options;
        }
        List<Option> out = new ArrayList<>();
        for (Option option : options) {
            if (option.id().toLowerCase(Locale.ROOT).contains(needle)
                    || option.label().getString().toLowerCase(Locale.ROOT).contains(needle)) {
                out.add(option);
            }
        }
        return out;
    }

    private int listTop() {
        return getY() + getHeight();
    }

    private int listHeight() {
        return ROW_H * (1 + Math.max(1, Math.min(MAX_ROWS, matching().size()))) + 2;
    }

    private boolean inList(double mx, double my) {
        return open && mx >= getX() && mx < getX() + getWidth() && my >= listTop() && my < listTop() + listHeight();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        AdminTheme.raised(g, getX(), getY(), getWidth(), getHeight(), AdminTheme.PAPER,
                isHoveredOrFocused() || open ? AdminTheme.GOLD_DARK : AdminTheme.PAPER_RULE, AdminTheme.PAPER_SHADE);
        Option current = find(selected);
        Component shown = current == null ? placeholder : current.label();
        int textY = getY() + (getHeight() - 8) / 2;
        g.drawString(font, fit(font, shown.getString(), getWidth() - 16), getX() + 4, textY,
                current == null ? AdminTheme.INK_3 : active ? AdminTheme.INK : AdminTheme.INK_3, false);
        g.drawString(font, open ? "▴" : "▾", getX() + getWidth() - 9, textY, AdminTheme.INK_2, false);
        AdminTheme.focusRing(g, this);
    }

    /**
     * The open list. The screen calls this after every widget has drawn, on a stratum of its own: drawn from
     * {@link #renderWidget} it would sit under any sibling added later (the buttons below the picker).
     */
    public void renderPopup(GuiGraphics g, int mouseX, int mouseY) {
        if (open && visible) {
            renderList(g, Minecraft.getInstance().font, mouseX, mouseY);
        }
    }

    private void renderList(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int x = getX();
        int y = listTop();
        int w = getWidth();
        int h = listHeight();
        g.fill(x, y, x + w, y + h, AdminTheme.FRAME);
        AdminTheme.outline(g, x, y, w, h, AdminTheme.GOLD_DARK);
        // The filter line: what has been typed, or how to filter.
        boolean blink = (net.minecraft.util.Util.getMillis() / 500L) % 2L == 0L;
        String head = filter.isEmpty() ? "" : filter + (blink ? "_" : "");
        g.drawString(font, head.isEmpty() ? Component.translatable("admin.wizards_and_beasts.picker.type_to_filter").getString()
                : fit(font, "⌕ " + head, w - 8), x + 4, y + 3, head.isEmpty() ? AdminTheme.FRAME_TEXT_DIM : AdminTheme.FRAME_TEXT, false);
        g.fill(x + 1, y + ROW_H, x + w - 1, y + ROW_H + 1, AdminTheme.GOLD_DARK);
        List<Option> rows = matching();
        if (rows.isEmpty()) {
            g.drawString(font, Component.translatable("admin.wizards_and_beasts.picker.no_match"), x + 4, y + ROW_H + 3,
                    AdminTheme.FRAME_TEXT_DIM, false);
            return;
        }
        for (int i = 0; i < Math.min(MAX_ROWS, rows.size() - scroll); i++) {
            Option option = rows.get(scroll + i);
            int ry = y + ROW_H * (i + 1) + 1;
            boolean hot = mouseX >= x && mouseX < x + w && mouseY >= ry && mouseY < ry + ROW_H;
            if (scroll + i == cursor || hot) {
                g.fill(x + 1, ry, x + w - 1, ry + ROW_H, 0x40000000 | (AdminTheme.GOLD & 0xFFFFFF));
            }
            int noteW = option.note().isEmpty() ? 0 : font.width(option.note()) + 6;
            g.drawString(font, fit(font, option.label().getString(), w - 10 - noteW), x + 4, ry + 2,
                    option.id().equals(selected) ? AdminTheme.GOLD_LIGHT : AdminTheme.FRAME_TEXT, false);
            if (noteW > 0) {
                g.drawString(font, option.note(), x + w - 4 - font.width(option.note()), ry + 2, AdminTheme.FRAME_TEXT_DIM, false);
            }
        }
        if (rows.size() > MAX_ROWS) {
            int trackH = MAX_ROWS * ROW_H;
            int thumbH = Math.max(6, trackH * MAX_ROWS / rows.size());
            int thumbY = y + ROW_H + 1 + (trackH - thumbH) * scroll / Math.max(1, rows.size() - MAX_ROWS);
            g.fill(x + w - 3, thumbY, x + w - 1, thumbY + thumbH, AdminTheme.GOLD);
        }
    }

    private static String fit(Font font, String text, int w) {
        if (font.width(text) <= w) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, w - font.width("…"))) + "…";
    }

    private Option find(String id) {
        for (Option option : options) {
            if (option.id().equals(id)) {
                return option;
            }
        }
        return null;
    }

    private void pick(Option option) {
        selected = option.id();
        open = false;
        filter = "";
        onPick.accept(option.id());
    }

    private void toggle() {
        open = !open;
        filter = "";
        scroll = 0;
        List<Option> rows = matching();
        cursor = Math.max(0, indexOf(rows, selected));
        scroll = Math.max(0, Math.min(cursor, rows.size() - MAX_ROWS));
    }

    private static int indexOf(List<Option> rows, String id) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).id().equals(id)) {
                return i;
            }
        }
        return 0;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return super.isMouseOver(mouseX, mouseY) || inList(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!active || !visible) {
            return false;
        }
        if (inList(event.x(), event.y())) {
            int row = (int) ((event.y() - listTop() - 1) / ROW_H) - 1 + scroll;
            List<Option> rows = matching();
            if (row >= scroll && row < rows.size()) {
                pick(rows.get(row));
            }
            return true;
        }
        if (super.isMouseOver(event.x(), event.y())) {
            setFocused(true);
            toggle();
            return true;
        }
        if (open) {
            open = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!inList(mouseX, mouseY)) {
            return false;
        }
        scroll = Math.max(0, Math.min(Math.max(0, matching().size() - MAX_ROWS), scroll - (int) Math.signum(scrollY)));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!active) {
            return false;
        }
        int key = event.key();
        if (!open) {
            if (isFocused() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_DOWN)) {
                toggle();
                return true;
            }
            return false;
        }
        List<Option> rows = matching();
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> open = false;
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (!rows.isEmpty()) {
                    pick(rows.get(Math.min(cursor, rows.size() - 1)));
                }
            }
            case GLFW.GLFW_KEY_DOWN -> cursor = Math.min(rows.size() - 1, cursor + 1);
            case GLFW.GLFW_KEY_UP -> cursor = Math.max(0, cursor - 1);
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!filter.isEmpty()) {
                    filter = filter.substring(0, filter.length() - 1);
                    cursor = 0;
                    scroll = 0;
                }
            }
            default -> {
                return true; // an open list takes the keyboard; nothing leaks to the page behind it
            }
        }
        if (cursor < scroll) {
            scroll = cursor;
        } else if (cursor >= scroll + MAX_ROWS) {
            scroll = cursor - MAX_ROWS + 1;
        }
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!open || !event.isAllowedChatCharacter() || filter.length() >= 32) {
            return open;
        }
        filter += event.codepointAsString();
        cursor = 0;
        scroll = 0;
        return true;
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) {
            open = false;
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        Option current = find(selected);
        output.add(NarratedElementType.TITLE, current == null ? placeholder : current.label());
    }
}
