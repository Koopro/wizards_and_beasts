package at.koopro.wizardsandbeasts.client.gui.widget;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui;
import at.koopro.wizardsandbeasts.client.gui.ui.UiSurface;
import at.koopro.wizardsandbeasts.client.gui.ui.UiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A closed button showing the current choice and a chevron; clicked, a list of every choice opens under
 * it (above it when there is no room), drawn above the rest of the screen.
 *
 * <p>For a choice among more than about five options, where cycling with {@link CyclerWidget}'s arrows
 * would mean clicking through options a player cannot see. For two to five options the cycler is
 * better: its value is always visible and it needs no popup.
 *
 * <p>Register it <em>before</em> the widgets its list may cover, so it is offered clicks first.
 */
@NullMarked
public final class ThemedDropdown<T> extends AbstractWidget {

    private static final int ROW = WizardsMetrics.ROW_H - 4;
    private static final int MAX_ROWS = 8;

    private final List<T> options;
    private final Function<T, Component> label;
    private final Consumer<T> onSelect;
    private final UiSurface surface;
    private T selected;
    private boolean open;
    private int scroll;

    public ThemedDropdown(int x, int y, int w, int h, List<T> options, T selected,
                          Function<T, Component> label, UiSurface surface, Consumer<T> onSelect) {
        super(x, y, w, h, label.apply(selected));
        this.options = List.copyOf(options);
        this.selected = selected;
        this.label = label;
        this.surface = surface;
        this.onSelect = onSelect;
    }

    public T selected() {
        return selected;
    }

    private int rows() {
        return Math.min(MAX_ROWS, options.size());
    }

    private Ui.Rect listRect() {
        int h = rows() * ROW + 2;
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int below = getY() + getHeight();
        int y = below + h <= screenH ? below : getY() - h;
        return new Ui.Rect(getX(), y, getWidth(), h);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        McStylePanel.drawThemedButton(g, getX(), getY(), getWidth(), getHeight(),
                open ? McStylePanel.ButtonTone.SELECT : McStylePanel.ButtonTone.NEUTRAL,
                McStylePanel.ControlState.of(active, isHovered() || isFocused()));
        int ink = active ? UiSurface.PAGE.ink() : UiSurface.PAGE.disabled();
        int textY = getY() + (getHeight() - font.lineHeight) / 2 + 1;
        g.drawString(font, UiText.fit(font, label.apply(selected).getString(), getWidth() - 20),
                getX() + WizardsMetrics.SPACE_S + 2, textY, ink, false);
        g.drawString(font, open ? "▴" : "▾", getX() + getWidth() - 10, textY, ink, false);
        if (!open) {
            return;
        }
        g.nextStratum();
        Ui.Rect list = listRect();
        Ui.panel(g, UiSurface.NIGHT, list.x(), list.y(), list.w(), list.h());
        for (int i = 0; i < rows(); i++) {
            T option = options.get(i + scroll);
            int ry = list.y() + 1 + i * ROW;
            Ui.Rect row = new Ui.Rect(list.x() + 1, ry, list.w() - 2, ROW);
            if (option.equals(selected)) {
                g.fill(row.x(), row.y(), row.right(), row.bottom(), UiSurface.NIGHT.selectWash());
            } else if (row.contains(mouseX, mouseY)) {
                g.fill(row.x(), row.y(), row.right(), row.bottom(), UiSurface.NIGHT.hoverWash());
            }
            g.drawString(font, UiText.fit(font, label.apply(option).getString(), row.w() - 8),
                    row.x() + WizardsMetrics.SPACE_S, ry + (ROW - font.lineHeight) / 2 + 1,
                    option.equals(selected) ? UiSurface.NIGHT.heading() : UiSurface.NIGHT.ink(), true);
        }
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return super.isMouseOver(mouseX, mouseY) || (open && listRect().contains(mouseX, mouseY));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!active || !visible) {
            return false;
        }
        if (open && listRect().contains(event.x(), event.y())) {
            int i = (int) ((event.y() - listRect().y() - 1) / ROW) + scroll;
            if (i >= 0 && i < options.size()) {
                selected = options.get(i);
                setMessage(label.apply(selected));
                onSelect.accept(selected);
            }
            open = false;
            playClick();
            return true;
        }
        if (super.isMouseOver(event.x(), event.y())) {
            open = !open;
            scroll = Math.max(0, Math.min(options.indexOf(selected), options.size() - rows()));
            playClick();
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
        if (!open || !listRect().contains(mouseX, mouseY)) {
            return false;
        }
        scroll = Math.max(0, Math.min(options.size() - rows(), scroll - (int) Math.signum(scrollY)));
        return true;
    }

    private static void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, label.apply(selected));
    }
}
