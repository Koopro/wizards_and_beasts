package at.koopro.wizardsandbeasts.client.admin.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.lwjgl.glfw.GLFW;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.Consumer;

/**
 * A numeric slider over a server-defined range, snapping to a step.
 *
 * <p>Shows the current value in the middle and the range's ends at either side, so an administrator can see
 * both where a value sits and how far it could go. Keyboard: arrows step, Shift+arrows step ten, Home and
 * End jump to the ends.
 *
 * <p>Every value it can produce is inside {@code [min, max]} and on the step grid, so it never <em>offers</em>
 * the server an out-of-range number. The server still checks: a slider's range is what the server said
 * when the panel opened, not a promise.
 */
@NullMarked
public final class AdminSlider extends AbstractSliderButton implements AdminControl {

    private static final int HANDLE_W = 8;
    private static final int END_PAD = 3;

    private final double min;
    private final double max;
    private final double step;
    private final boolean integer;
    private final int decimals;
    private final Consumer<String> onEdit;
    private boolean silent;
    private String lastText;

    public AdminSlider(int x, int y, int w, int h, double min, double max, double step, boolean integer,
                       String initial, Consumer<String> onEdit) {
        super(x, y, w, h, Component.empty(), 0.0);
        this.min = min;
        this.max = max;
        this.step = step > 0.0 && Double.isFinite(step) ? step : (integer ? 1.0 : (max - min) / 100.0);
        this.integer = integer;
        this.decimals = integer ? 0 : Math.max(0, (int) -Math.floor(Math.log10(this.step)));
        this.onEdit = onEdit;
        this.lastText = initial;
        display(initial);
    }

    @Override
    public AbstractWidget widget() {
        return this;
    }

    @Override
    public void display(String text) {
        double parsed;
        try {
            parsed = Double.parseDouble(text);
        } catch (NumberFormatException notANumber) {
            parsed = min;
        }
        silent = true;
        value = max > min ? Mth.clamp((parsed - min) / (max - min), 0.0, 1.0) : 0.0;
        lastText = text;
        updateMessage();
        silent = false;
    }

    /** The slider's position as a value on the step grid, inside the range. */
    private double snapped() {
        double raw = min + value * (max - min);
        double onGrid = min + Math.round((raw - min) / step) * step;
        return Mth.clamp(onGrid, min, max);
    }

    private String format(double number) {
        if (integer) {
            return Long.toString(Math.round(number));
        }
        BigDecimal rounded = BigDecimal.valueOf(number).setScale(decimals, RoundingMode.HALF_UP);
        // Rounding to the step's precision can nudge past an end the step does not divide evenly.
        if (rounded.doubleValue() > max) {
            rounded = BigDecimal.valueOf(max);
        } else if (rounded.doubleValue() < min) {
            rounded = BigDecimal.valueOf(min);
        }
        return rounded.stripTrailingZeros().toPlainString();
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.literal(silent ? lastText : format(snapped())));
    }

    @Override
    protected void applyValue() {
        if (silent) {
            return;
        }
        String text = format(snapped());
        if (!text.equals(lastText)) {
            lastText = text;
            onEdit.accept(text);
        }
    }

    private void stepBy(double delta) {
        double target = Mth.clamp(snapped() + delta, min, max);
        setValue(max > min ? (target - min) / (max - min) : 0.0);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (!active || !isFocused()) {
            return super.keyPressed(event);
        }
        double multiple = event.hasShiftDown() ? 10.0 : 1.0;
        if (event.isLeft() || event.isDown()) {
            stepBy(-step * multiple);
            return true;
        }
        if (event.isRight() || event.isUp()) {
            stepBy(step * multiple);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_HOME) {
            setValue(0.0);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_END) {
            setValue(1.0);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void renderWidget(@NonNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        AdminTheme.inset(g, x, y, w, h);
        int handleX = x + (int) (value * (w - HANDLE_W));
        if (active && handleX > x + 1) {
            g.fill(x + 1, y + 2, handleX, y + h - 1, AdminTheme.PAPER_SELECT);
        }
        boolean lit = active && isHoveredOrFocused();
        if (lit) {
            AdminTheme.outline(g, x, y, w, h, AdminTheme.GOLD_DARK);
        }
        AdminTheme.raised(g, handleX, y, HANDLE_W, h,
                !active ? AdminTheme.PAPER_SHADE : lit ? AdminTheme.GOLD_LIGHT : AdminTheme.GOLD,
                active ? AdminTheme.GOLD_DARK : AdminTheme.INK_3, AdminTheme.GOLD_LIGHT);

        Font font = Minecraft.getInstance().font;
        int textY = y + (h - 8) / 2 + 1;
        String low = format(min);
        String high = format(max);
        String now = getMessage().getString();
        int nowW = font.width(now);
        // The ends step aside when the value would collide with them, rather than overprinting it.
        int centreLeft = x + (w - nowW) / 2;
        if (x + END_PAD + font.width(low) + 4 < centreLeft) {
            g.drawString(font, low, x + END_PAD, textY, AdminTheme.INK_3, false);
        }
        if (centreLeft + nowW + 4 < x + w - END_PAD - font.width(high)) {
            g.drawString(font, high, x + w - END_PAD - font.width(high), textY, AdminTheme.INK_3, false);
        }
        g.drawString(font, now, centreLeft, textY, active ? AdminTheme.INK : AdminTheme.INK_3, false);
        AdminTheme.focusRing(g, this);
    }
}
