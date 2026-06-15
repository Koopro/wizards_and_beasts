package at.koopro.wizardsandbeasts.client.trinket.gui;

import at.koopro.wizardsandbeasts.diary.DiaryService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Possession takeover. A locked overlay that holds the writer for the duration of the possession —
 * no movement, no dismissal — while the soul fragment speaks. Auto-closes when the hold expires.
 */
public class DiaryPossessionScreen extends Screen {

    private final String line;
    private int ticksLeft = DiaryService.POSSESSION_TICKS;

    public DiaryPossessionScreen(String line) {
        super(Component.literal("…"));
        this.line = line;
    }

    @Override
    public void tick() {
        if (--ticksLeft <= 0) {
            onClose();
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        return true; // swallow all input — the writer is not in control
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean isDoubleClick) {
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Deepening black; the diary swallows the screen.
        float t = 1f - (ticksLeft / (float) DiaryService.POSSESSION_TICKS);
        int alpha = (int) (0x88 + 0x77 * Math.min(1f, t)) << 24;
        graphics.fill(0, 0, width, height, alpha | 0x000000);

        int cy = height / 2;
        graphics.drawCenteredString(font, "§4§o\"" + line + "\"", width / 2, cy - 6, 0xFFFFFF);
        graphics.drawCenteredString(font, "§8you cannot look away", width / 2, cy + 12, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
