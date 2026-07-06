package at.koopro.wizardsandbeasts.client.trinket.gui;

import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import at.koopro.wizardsandbeasts.network.trinket.DiaryWriteC2SPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Write-into-the-diary dialogue. Your lines and Riddle's replies scroll in a transcript. */
public class DiaryWriteScreen extends Screen {

    @Nullable
    private static DiaryWriteScreen active;

    private static final int PANEL_W = 300;
    private static final int PANEL_H = 200;
    private static final int MAX_LINES = 80;

    private final List<String> transcript = new ArrayList<>();
    private EditBox input;
    private GuiScaleHelper.Layout layout;
    private int panelX;
    private int panelY;

    public DiaryWriteScreen() {
        super(Component.literal("Riddle's Diary"));
    }

    /** Appends one of Riddle's replies to the active transcript. */
    public static void appendReply(String line, int tier) {
        if (active != null) {
            String colour = switch (tier) {
                case 0 -> "§7";
                case 1 -> "§8";
                case 2 -> "§5";
                default -> "§4";
            };
            active.add(colour + "§oT. M. Riddle: §r" + colour + line);
        }
    }

    private void add(String line) {
        transcript.add(line);
        while (transcript.size() > MAX_LINES) {
            transcript.remove(0);
        }
    }

    @Override
    protected void init() {
        super.init();
        active = this;
        layout = GuiScaleHelper.Layout.fit(width, height, PANEL_W, PANEL_H);
        panelX = layout.panelX();
        panelY = layout.panelY();
        // Widgets live in screen space, so their bounds are scaled to line up
        // with the pose-scaled panel art drawn in render().
        input = new EditBox(font, layout.x(10), layout.y(PANEL_H - 28),
                layout.s(PANEL_W - 90), layout.s(18), Component.literal("write"));
        input.setMaxLength(80);
        input.setHint(Component.literal("write a line…"));
        addRenderableWidget(input);
        setInitialFocus(input);
        addRenderableWidget(Button.builder(Component.literal("Write"), b -> submit())
                .bounds(layout.x(PANEL_W - 74), layout.y(PANEL_H - 28), layout.s(64), layout.s(18)).build());
    }

    private void submit() {
        String text = input.getValue().trim();
        if (text.isEmpty()) {
            return;
        }
        add("§f" + text);
        ClientPacketDistributor.sendToServer(new DiaryWriteC2SPayload(text));
        input.setValue("");
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER && input.isFocused()) {
            submit();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (active == this) {
            active = null;
        }
        super.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderMenuBackground(graphics);
        layout.applyScale(graphics);
        graphics.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xE6120D08);
        graphics.renderOutline(panelX, panelY, PANEL_W, PANEL_H, 0xFF4A3A22);
        graphics.drawString(font, "§eA half-blank diary, dated 1943", panelX + 10, panelY + 8, 0xFFFFFF, false);

        int top = panelY + 24;
        int bottom = panelY + PANEL_H - 34;
        int lineH = 10;
        int maxRows = (bottom - top) / lineH;
        int start = Math.max(0, transcript.size() - maxRows);
        int y = top;
        for (int i = start; i < transcript.size(); i++) {
            graphics.drawString(font, transcript.get(i), panelX + 10, y, 0xFFFFFF, false);
            y += lineH;
        }

        graphics.pose().popMatrix();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
