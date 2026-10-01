package at.koopro.wizardsandbeasts.client.admin.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;

import java.util.function.Consumer;

/**
 * A switch for a boolean setting: a gilt-lit track when on, a recessed one when off, with the word beside
 * it so the state never depends on reading a colour.
 */
@NullMarked
public final class AdminToggle extends AbstractButton implements AdminControl {

    private static final int TRACK_W = 22;
    private static final int TRACK_H = 10;
    private static final int KNOB = 8;

    private final Consumer<String> onEdit;
    private boolean on;

    public AdminToggle(int x, int y, int w, int h, boolean initial, Consumer<String> onEdit) {
        super(x, y, w, h, Component.empty());
        this.on = initial;
        this.onEdit = onEdit;
    }

    @Override
    public AbstractWidget widget() {
        return this;
    }

    @Override
    public void display(String value) {
        on = Boolean.parseBoolean(value);
    }

    @Override
    public void onPress(@NonNull InputWithModifiers input) {
        on = !on;
        onEdit.accept(Boolean.toString(on));
    }

    @Override
    protected void renderContents(@NonNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int trackX = getX() + getWidth() - TRACK_W;
        int trackY = getY() + (getHeight() - TRACK_H) / 2;
        boolean lit = active && isHoveredOrFocused();
        if (on) {
            AdminTheme.raised(g, trackX, trackY, TRACK_W, TRACK_H,
                    active ? AdminTheme.GOLD : AdminTheme.PAPER_DEEP, AdminTheme.GOLD_DARK, AdminTheme.GOLD_LIGHT);
        } else {
            AdminTheme.inset(g, trackX, trackY, TRACK_W, TRACK_H);
        }
        if (lit) {
            AdminTheme.outline(g, trackX - 1, trackY - 1, TRACK_W + 2, TRACK_H + 2, AdminTheme.GOLD_DARK);
        }
        int knobX = on ? trackX + TRACK_W - KNOB - 1 : trackX + 1;
        AdminTheme.raised(g, knobX, trackY + 1, KNOB, KNOB,
                active ? AdminTheme.PAPER : AdminTheme.PAPER_SHADE, AdminTheme.INK_2, 0xFFFFFFFF);

        Font font = Minecraft.getInstance().font;
        Component word = Component.translatable(on ? "admin.wizards_and_beasts.value.on" : "admin.wizards_and_beasts.value.off");
        int ink = !active ? AdminTheme.INK_3 : on ? AdminTheme.INK : AdminTheme.INK_2;
        g.drawString(font, word, trackX - 4 - font.width(word), getY() + (getHeight() - 8) / 2, ink, false);
        AdminTheme.focusRing(g, this);
    }

    @Override
    protected void renderDefaultLabel(@NonNull ActiveTextCollector collector) {
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.translatable(
                on ? "admin.wizards_and_beasts.value.on" : "admin.wizards_and_beasts.value.off"));
    }
}
