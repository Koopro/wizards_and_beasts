package at.koopro.wizardsandbeasts.client.admin.widget;

import at.koopro.wizardsandbeasts.client.gui.util.GuiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * {@code ◀ VALUE ▶} over a server-supplied option list. Click the left or right third to step, or use the
 * arrow keys when focused. Wraps at both ends.
 *
 * <p>Options are displayed through {@code admin.wizards_and_beasts.option.<value>} when that key exists, and
 * as a tidied constant name otherwise, so a new enum constant is readable before anyone writes its lang key.
 */
@NullMarked
public final class AdminEnumSelector extends AbstractWidget implements AdminControl {

    private static final int ARROW_W = 10;

    private List<String> options;
    private final Consumer<String> onEdit;
    private final Function<String, Component> labeller;
    private int index;

    public AdminEnumSelector(int x, int y, int w, int h, List<String> options, String initial,
                             Consumer<String> onEdit) {
        this(x, y, w, h, options, initial, onEdit, AdminEnumSelector::optionLabel);
    }

    /** With a custom labeller, for options that are not enum constants (a player, a filter). */
    public AdminEnumSelector(int x, int y, int w, int h, List<String> options, String initial,
                             Consumer<String> onEdit, Function<String, Component> labeller) {
        super(x, y, w, h, Component.empty());
        this.options = options.isEmpty() ? List.of(initial) : List.copyOf(options);
        this.onEdit = onEdit;
        this.labeller = labeller;
        display(initial);
    }

    /**
     * Replaces the options in place (a filter narrowed them), keeping {@code current} selected when it is still among
     * them. In place rather than by rebuilding the page, so a filter field being typed into keeps its focus.
     */
    public void setOptions(List<String> next, String current) {
        options = next.isEmpty() ? List.of(current) : List.copyOf(next);
        display(current);
    }

    public String selected() {
        return options.get(index);
    }

    @Override
    public AbstractWidget widget() {
        return this;
    }

    @Override
    public void display(String value) {
        int found = options.indexOf(value);
        index = Math.max(found, 0);
    }

    private void step(int delta) {
        index = Math.floorMod(index + delta, options.size());
        onEdit.accept(options.get(index));
    }

    @Override
    public void onClick(@NonNull MouseButtonEvent event, boolean doubleClick) {
        double relative = event.x() - getX();
        step(relative < getWidth() / 2.0 ? -1 : 1);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (!active || !isFocused()) {
            return false;
        }
        if (event.isLeft()) {
            step(-1);
            return true;
        }
        if (event.isRight() || event.isSelection()) {
            step(1);
            return true;
        }
        return false;
    }

    /** The label an option is shown under. */
    public static Component optionLabel(String value) {
        String key = "admin.wizards_and_beasts.option." + value.toLowerCase(Locale.ROOT);
        String fallback = value.charAt(0) + value.substring(1).toLowerCase(Locale.ROOT).replace('_', ' ');
        return Component.translatableWithFallback(key, fallback);
    }

    @Override
    protected void renderWidget(@NonNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        boolean lit = active && isHoveredOrFocused();
        AdminTheme.raised(g, x, y, w, h, lit ? AdminTheme.PAPER_SELECT : AdminTheme.PAPER,
                lit ? AdminTheme.GOLD_DARK : AdminTheme.PAPER_RULE, 0xFFFFFFFF);
        Font font = Minecraft.getInstance().font;
        int textY = y + (h - 8) / 2 + 1;
        int arrowInk = active ? AdminTheme.GOLD_DARK : AdminTheme.INK_3;
        boolean hoverLeft = lit && mouseX < x + w / 2;
        g.drawString(font, "◀", x + 3, textY, hoverLeft ? AdminTheme.RUBRIC : arrowInk, false);
        g.drawString(font, "▶", x + w - 3 - font.width("▶"), textY, lit && !hoverLeft ? AdminTheme.RUBRIC : arrowInk, false);
        GuiText.drawFittedCentered(g, font, labeller.apply(options.get(index)).getString(), x + ARROW_W, textY,
                w - 2 * ARROW_W, active ? AdminTheme.INK : AdminTheme.INK_3);
        AdminTheme.focusRing(g, this);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, labeller.apply(options.get(index)));
    }
}
