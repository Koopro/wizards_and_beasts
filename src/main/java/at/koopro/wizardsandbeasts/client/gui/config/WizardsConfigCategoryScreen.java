package at.koopro.wizardsandbeasts.client.gui.config;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.NonNull;

/**
 * Per-category config screen — grimoire page aesthetic: parchment fill, dark binding
 * rule on the left edge, chapter header, and one labelled row per setting revealed
 * top-to-bottom with a staggered ink animation.
 */
public class WizardsConfigCategoryScreen extends Screen {
    private static final int ROW_STAGGER_MS = 60;
    private static final int ROW_HEIGHT = 26;
    private static final int CONTENT_LEFT = 24;
    private static final int CONTENT_RIGHT = 16;
    private static final int WIDGET_WIDTH = 120;
    private static final int WIDGET_HEIGHT = 18;
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII"};

    private final Screen parent;
    private final WizardsConfigScreen.Category category;
    private final int ordinal;
    private final InkRevealRenderer inkReveal = new InkRevealRenderer();
    private final List<String> rowLabels = new ArrayList<>();
    private int rowsTop;

    public WizardsConfigCategoryScreen(Screen parent, WizardsConfigScreen.Category category, int ordinal) {
        super(Component.literal(category.name() + " Configuration"));
        this.parent = parent;
        this.category = category;
        this.ordinal = ordinal;
    }

    @Override
    protected void init() {
        super.init();
        inkReveal.reset();
        rowLabels.clear();

        FlatTextButton back = new FlatTextButton(8, 6, "« Return", this::onClose);
        addRenderableWidget(back);

        rowsTop = 48;
        int widgetX = width - CONTENT_RIGHT - WIDGET_WIDTH;
        int row = 0;
        for (WizardsConfigScreen.ConfigEntry entry : category.entries()) {
            int rowY = rowsTop + row * ROW_HEIGHT;
            AbstractWidget widget = ConfigWidgets.create(entry.value(), entry.valueSpec(),
                    widgetX, rowY + (ROW_HEIGHT - WIDGET_HEIGHT) / 2 - 1, WIDGET_WIDTH, WIDGET_HEIGHT);
            if (widget == null) {
                continue;
            }
            addRenderableWidget(widget);
            inkReveal.register(widget, row, ROW_STAGGER_MS);
            rowLabels.add(ConfigWidgets.humanise(entry.key()));
            row++;
        }
    }

    @Override
    public void render(@NonNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        inkReveal.update();

        String roman = ordinal >= 1 && ordinal <= ROMAN.length ? ROMAN[ordinal - 1] : String.valueOf(ordinal);
        ConfigWidgets.drawCenteredNoShadow(graphics, font, "Chapter " + roman + " — " + category.name(),
                width / 2, 18, ConfigWidgets.INK);
        graphics.hLine(CONTENT_LEFT, width - CONTENT_RIGHT, 32, ConfigWidgets.INK);

        for (int i = 0; i < rowLabels.size(); i++) {
            if (!inkReveal.isRevealed(i, ROW_STAGGER_MS)) {
                continue;
            }
            int rowY = rowsTop + i * ROW_HEIGHT;
            graphics.drawString(font, rowLabels.get(i), CONTENT_LEFT, rowY + (ROW_HEIGHT - 8) / 2, ConfigWidgets.INK, false);
            graphics.hLine(CONTENT_LEFT, width - CONTENT_RIGHT, rowY + ROW_HEIGHT - 1, ConfigWidgets.INK_FADED);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(@NonNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, ConfigWidgets.PARCHMENT);
        // Grimoire page binding: 4px ink rule down the left edge.
        graphics.fill(0, 0, 4, height, ConfigWidgets.INK);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    /** Borderless ink-text button for the « Return link. */
    private final class FlatTextButton extends AbstractButton {
        private final Runnable action;

        FlatTextButton(int x, int y, String label, Runnable action) {
            super(x, y, Minecraft.getInstance().font.width(label) + 4, 12, Component.literal(label));
            this.action = action;
        }

        @Override
        public void onPress(@NonNull InputWithModifiers input) {
            action.run();
        }

        @Override
        protected void renderContents(@NonNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.drawString(font, getMessage().getString(), getX() + 2, getY() + 2, ConfigWidgets.INK, false);
            if (isHovered()) {
                graphics.hLine(getX() + 2, getX() + getWidth() - 3, getY() + getHeight() - 1, ConfigWidgets.INK);
            }
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
