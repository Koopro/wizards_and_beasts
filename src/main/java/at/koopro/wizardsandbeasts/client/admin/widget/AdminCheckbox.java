package at.koopro.wizardsandbeasts.client.admin.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;

import java.util.function.Consumer;

/**
 * A labelled tick box for view options on the panel itself (filters, "show advanced"). Not used for
 * settings — a setting's boolean is an {@link AdminToggle}, so a filter can never be mistaken for a rule.
 */
@NullMarked
public final class AdminCheckbox extends AbstractButton {

    private static final int BOX = 9;

    private final Consumer<Boolean> onChange;
    private boolean checked;

    public AdminCheckbox(int x, int y, Component label, boolean checked, Consumer<Boolean> onChange) {
        super(x, y, BOX + 4 + Minecraft.getInstance().font.width(label), 12, label);
        this.checked = checked;
        this.onChange = onChange;
    }

    public boolean checked() {
        return checked;
    }

    @Override
    public void onPress(@NonNull InputWithModifiers input) {
        checked = !checked;
        onChange.accept(checked);
    }

    @Override
    protected void renderContents(@NonNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int boxY = getY() + (getHeight() - BOX) / 2;
        AdminTheme.inset(g, getX(), boxY, BOX, BOX);
        if (isHoveredOrFocused()) {
            AdminTheme.outline(g, getX(), boxY, BOX, BOX, AdminTheme.GOLD_DARK);
        }
        if (checked) {
            g.fill(getX() + 2, boxY + 2, getX() + BOX - 2, boxY + BOX - 2, AdminTheme.INK);
        }
        Font font = Minecraft.getInstance().font;
        g.drawString(font, getMessage(), getX() + BOX + 4, getY() + (getHeight() - 8) / 2,
                active ? AdminTheme.INK_2 : AdminTheme.INK_3, false);
        AdminTheme.focusRing(g, this);
    }

    @Override
    protected void renderDefaultLabel(@NonNull ActiveTextCollector collector) {
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
