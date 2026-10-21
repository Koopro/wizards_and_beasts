package at.koopro.wizardsandbeasts.client.gui.config;

import java.util.ArrayList;
import java.util.List;
import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.ui.UiSurface;
import at.koopro.wizardsandbeasts.client.gui.ui.UiText;
import at.koopro.wizardsandbeasts.client.gui.ui.UiTooltips;
import at.koopro.wizardsandbeasts.client.gui.widget.ScrollList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Per-category config screen — grimoire page aesthetic: the same tiled page as the memo board, ink binding
 * rule on the left edge, chapter header, and one labelled row per setting revealed
 * top-to-bottom with a staggered ink animation.
 *
 * <p>The rows scroll. The Heritage chapter alone has twenty-odd settings at 26px each, and the page used
 * to lay them out straight down the screen: at GUI scale 3 on 1080p (360px tall) everything after the
 * twelfth row was below the window with no way to reach it. Rows outside the page are clipped rather than
 * hidden, so Tab still walks every setting and the page scrolls to whichever one takes focus.
 */
public class WizardsConfigCategoryScreen extends Screen {
    private static final int ROW_STAGGER_MS = 60;
    private static final int ROW_HEIGHT = 26;
    private static final int ROWS_TOP = 48;
    private static final int CONTENT_LEFT = 24;
    private static final int CONTENT_RIGHT = 16;
    private static final int BOTTOM_MARGIN = WizardsMetrics.SPACE_L;
    private static final int WIDGET_WIDTH = 120;
    private static final int WIDGET_HEIGHT = 18;
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII"};

    /** One setting: its control, its label, and what it does. */
    private record Row(AbstractWidget widget, Component label, @Nullable Component tooltip) {}

    private final Screen parent;
    private final WizardsConfigScreen.Category category;
    private final int ordinal;
    private final InkRevealRenderer inkReveal = new InkRevealRenderer();
    private final List<Row> rows = new ArrayList<>();
    private final ScrollList list = new ScrollList(ROW_HEIGHT);
    private @Nullable FlatTextButton back;
    private int widgetX;

    public WizardsConfigCategoryScreen(Screen parent, WizardsConfigScreen.Category category, int ordinal) {
        super(Component.translatable(ConfigWidgets.KEY + "category_title", category.displayName()));
        this.parent = parent;
        this.category = category;
        this.ordinal = ordinal;
    }

    @Override
    protected void init() {
        super.init();
        inkReveal.reset();
        rows.clear();

        back = new FlatTextButton(8, 6, Component.translatable(ConfigWidgets.KEY + "back"), this::onClose);
        addRenderableWidget(back);

        // The page holds whole rows only, so the last one is never cut through its control.
        int viewH = Math.max(ROW_HEIGHT, (height - ROWS_TOP - BOTTOM_MARGIN) / ROW_HEIGHT * ROW_HEIGHT);
        list.setBounds(CONTENT_LEFT, ROWS_TOP, width - CONTENT_LEFT - CONTENT_RIGHT, viewH);

        int index = 0;
        for (WizardsConfigScreen.ConfigEntry entry : category.entries()) {
            AbstractWidget widget = ConfigWidgets.create(entry.value(), entry.valueSpec(), 0, 0, WIDGET_WIDTH, WIDGET_HEIGHT);
            if (widget == null) {
                continue;
            }
            Component tooltip = ConfigWidgets.tooltip(entry.key(), entry.valueSpec(), entry.value().get());
            if (tooltip != null) {
                widget.setTooltip(Tooltip.create(tooltip));
            }
            addRenderableWidget(widget);
            inkReveal.register(widget, index, ROW_STAGGER_MS);
            rows.add(new Row(widget, ConfigWidgets.label(entry.key()), tooltip));
            index++;
        }
        list.setItemCount(rows.size());
        relayout();
    }

    /** Places every control against the current scroll; rows outside the page sit off it and are clipped. */
    private void relayout() {
        widgetX = list.rowLeft() + list.rowWidth() - WIDGET_WIDTH - (list.scrollable() ? WizardsMetrics.SPACE_S : 0);
        for (int i = 0; i < rows.size(); i++) {
            AbstractWidget widget = rows.get(i).widget();
            widget.setX(widgetX);
            widget.setY(list.rowTop(i) + (ROW_HEIGHT - WIDGET_HEIGHT) / 2 - 1);
        }
    }

    private int viewTop() {
        return ROWS_TOP;
    }

    private int viewBottom() {
        return ROWS_TOP + list.visibleRows() * ROW_HEIGHT;
    }

    @Override
    public void render(@NonNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        inkReveal.update();

        String roman = ordinal >= 1 && ordinal <= ROMAN.length ? ROMAN[ordinal - 1] : String.valueOf(ordinal);
        ConfigWidgets.drawCenteredNoShadow(graphics, font,
                Component.translatable(ConfigWidgets.KEY + "chapter", roman, category.displayName()).getString(),
                width / 2, 18, ConfigWidgets.INK);
        graphics.hLine(CONTENT_LEFT, width - CONTENT_RIGHT, 32, ConfigWidgets.INK);

        int ruleRight = list.rowLeft() + list.rowWidth();
        int labelW = widgetX - CONTENT_LEFT - WizardsMetrics.SPACE_M;
        graphics.enableScissor(0, viewTop(), width, viewBottom());
        for (int i = list.firstVisible(); i < list.lastVisibleExclusive(); i++) {
            if (!inkReveal.isRevealed(i, ROW_STAGGER_MS)) {
                continue;
            }
            Row row = rows.get(i);
            int rowY = list.rowTop(i);
            boolean cut = UiText.drawFit(graphics, font, UiSurface.PAGE, row.label(), CONTENT_LEFT,
                    rowY + (ROW_HEIGHT - 8) / 2, labelW, ConfigWidgets.INK);
            graphics.hLine(CONTENT_LEFT, ruleRight, rowY + ROW_HEIGHT - 1, ConfigWidgets.INK_FADED);
            row.widget().render(graphics, mouseX, mouseY, partialTick);
            // The label explains the setting as well as the control does: the full name (when it was
            // shortened) and the description, on the same card the control shows.
            boolean overLabel = mouseX >= CONTENT_LEFT && mouseX < CONTENT_LEFT + labelW
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT && mouseY < viewBottom();
            if (overLabel && (cut || row.tooltip() != null)) {
                List<Component> lines = new ArrayList<>();
                lines.add(row.label());
                if (row.tooltip() != null) {
                    lines.add(row.tooltip());
                }
                UiTooltips.show(graphics, font, lines.getFirst(), lines.subList(1, lines.size()), mouseX, mouseY);
            }
        }
        graphics.disableScissor();
        if (list.scrollable()) {
            list.renderScrollbar(graphics, UiSurface.PAGE);
        }

        if (back != null) {
            back.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void renderBackground(@NonNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, ConfigWidgets.PARCHMENT);
        McStylePanel.drawTiled(graphics, WizardsConfigScreen.PARCHMENT_TEX, 0, 0, width, height,
                WizardsConfigScreen.PARCHMENT_TILE);
        // Grimoire page binding: 4px ink rule down the left edge.
        graphics.fill(0, 0, 4, height, ConfigWidgets.INK);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean isDoubleClick) {
        // A control scrolled off the page is still where its row would be; a click there is on the header
        // or the margin, not on it.
        boolean onPage = event.y() >= viewTop() && event.y() < viewBottom();
        if (!onPage && (back == null || !back.isMouseOver(event.x(), event.y()))) {
            return false;
        }
        return super.mouseClicked(event, isDoubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (list.mouseScrolled(scrollY)) {
            relayout();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** Tab and the arrow keys reach every setting; the page follows the focus. */
    @Override
    public void setFocused(@Nullable GuiEventListener focused) {
        super.setFocused(focused);
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).widget() == focused) {
                list.scrollTo(i);
                relayout();
                return;
            }
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    /** Borderless ink-text button for the « Return link. */
    private final class FlatTextButton extends AbstractButton {
        private final Runnable action;

        FlatTextButton(int x, int y, Component label, Runnable action) {
            super(x, y, Minecraft.getInstance().font.width(label) + 4, 12, label);
            this.action = action;
        }

        @Override
        public void onPress(@NonNull InputWithModifiers input) {
            action.run();
        }

        @Override
        protected void renderContents(@NonNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.drawString(font, getMessage().getString(), getX() + 2, getY() + 2, ConfigWidgets.INK, false);
            if (isHoveredOrFocused()) {
                graphics.hLine(getX() + 2, getX() + getWidth() - 3, getY() + getHeight() - 1, ConfigWidgets.INK);
            }
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
