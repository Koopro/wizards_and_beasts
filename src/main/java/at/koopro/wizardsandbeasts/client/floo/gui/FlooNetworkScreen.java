package at.koopro.wizardsandbeasts.client.floo.gui;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.ScreenLayoutScaler;
import at.koopro.wizardsandbeasts.floo.FlooDestinationDto;
import at.koopro.wizardsandbeasts.network.floo.FlooTravelRequestC2SPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class FlooNetworkScreen extends Screen {

    private static final int PANEL_W = 220;
    private static final int PANEL_H = 210;
    private static final int ENTRY_H = 16;
    private static final int MAX_VISIBLE = 8;
    private static final int LIST_PADDING = 6;

    private static final int COL_TITLE = 0xFFD4AF37;
    private static final int COL_ENTRY = 0xFFE8E8E8;
    private static final int COL_SELECTED = 0xFF4CAF50;
    private static final int COL_DISABLED = 0xFF666666;
    private static final int COL_HOVER = 0xFF8BC34A;
    private static final int COL_DIVIDER = 0xFF444466;

    private final List<FlooDestinationDto> destinations;
    private int selectedIndex = -1;
    private int scrollOffset = 0;
    private Button travelButton;
    private ScreenLayoutScaler layout;

    public FlooNetworkScreen(@NonNull List<FlooDestinationDto> destinations) {
        super(Component.literal("Floo Network"));
        this.destinations = destinations;
    }

    @Override
    protected void init() {
        super.init();
        layout = ScreenLayoutScaler.forScreen(width, height, PANEL_W, PANEL_H);
        int px = layout.panelX();
        int py = layout.panelY();
        int panelW = layout.panelW();
        int panelH = layout.panelH();

        int btnY = py + panelH - layout.s(28);
        int btnW = layout.s(80);

        travelButton = addRenderableWidget(
                Button.builder(Component.literal("Travel"), b -> onTravelClicked())
                        .bounds(px + panelW / 2 - btnW - layout.s(4), btnY, btnW, layout.s(18))
                        .build());
        travelButton.active = false;

        addRenderableWidget(
                Button.builder(Component.literal("Cancel"), b -> onClose())
                        .bounds(px + panelW / 2 + layout.s(4), btnY, btnW, layout.s(18))
                        .build());
    }

    @Override
    public void render(@NonNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderMenuBackground(graphics);
        int px = layout.panelX();
        int py = layout.panelY();
        int panelW = layout.panelW();
        int panelH = layout.panelH();

        McStylePanel.drawTexturedPanel(graphics, px, py, panelW, panelH);

        graphics.drawCenteredString(font, "Floo Network", px + panelW / 2, py + layout.s(8), COL_TITLE);
        graphics.fill(px + layout.s(5), py + layout.s(22), px + panelW - layout.s(5), py + layout.s(23), COL_DIVIDER);

        if (destinations.isEmpty()) {
            graphics.drawCenteredString(font, "No connected fireplaces found.",
                    px + panelW / 2, py + layout.s(60), COL_DISABLED);
        } else {
            renderList(graphics, px, py, panelW, mouseX, mouseY);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderList(@NonNull GuiGraphics graphics, int px, int py, int panelW, int mouseX, int mouseY) {
        int listX = px + LIST_PADDING;
        int listY = py + layout.s(26);
        int listW = panelW - LIST_PADDING * 2;
        int visibleCount = Math.min(MAX_VISIBLE, destinations.size() - scrollOffset);

        graphics.enableScissor(listX, listY, listX + listW, listY + MAX_VISIBLE * ENTRY_H);

        for (int i = 0; i < visibleCount; i++) {
            int idx = i + scrollOffset;
            FlooDestinationDto dto = destinations.get(idx);
            int entryY = listY + i * ENTRY_H;
            boolean hovered = mouseX >= listX && mouseX < listX + listW
                    && mouseY >= entryY && mouseY < entryY + ENTRY_H;

            int bgColor = 0x00000000;
            if (idx == selectedIndex) {
                bgColor = 0x80204020;
            } else if (hovered && dto.isEnabled()) {
                bgColor = 0x40808080;
            }
            if (bgColor != 0) {
                graphics.fill(listX, entryY, listX + listW, entryY + ENTRY_H, bgColor);
            }

            int textColor;
            if (!dto.isEnabled()) {
                textColor = COL_DISABLED;
            } else if (idx == selectedIndex) {
                textColor = COL_SELECTED;
            } else if (hovered) {
                textColor = COL_HOVER;
            } else {
                textColor = COL_ENTRY;
            }

            String label = dto.networkAddress();
            if (!dto.isEnabled()) label += " [Sealed]";
            String dimLabel = formatDimension(dto.dimensionName());
            if (!dimLabel.isEmpty()) {
                graphics.drawString(font, label, listX + 2, entryY + 2, textColor, false);
                graphics.drawString(font, dimLabel, listX + 2, entryY + 9, COL_DISABLED, false);
            } else {
                graphics.drawString(font, label, listX + 2, entryY + 4, textColor, false);
            }
        }

        graphics.disableScissor();

        if (destinations.size() > MAX_VISIBLE) {
            int scrollbarX = px + panelW - LIST_PADDING - 2;
            int scrollbarTotalH = MAX_VISIBLE * ENTRY_H;
            int thumbH = Math.max(10, scrollbarTotalH * MAX_VISIBLE / destinations.size());
            int thumbY = listY + (scrollbarTotalH - thumbH) * scrollOffset
                    / Math.max(1, destinations.size() - MAX_VISIBLE);
            graphics.fill(scrollbarX, listY, scrollbarX + 2, listY + scrollbarTotalH, 0x40FFFFFF);
            graphics.fill(scrollbarX, thumbY, scrollbarX + 2, thumbY + thumbH, 0xAAFFFFFF);
        }
    }

    private static @NonNull String formatDimension(@NonNull String dimensionId) {
        if (dimensionId.equals("minecraft:overworld")) return "";
        int colon = dimensionId.lastIndexOf(':');
        return colon >= 0 ? "[" + dimensionId.substring(colon + 1).replace('_', ' ') + "]" : "";
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (!destinations.isEmpty()) {
            if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
                int dir = key == GLFW.GLFW_KEY_UP ? -1 : 1;
                int next = (selectedIndex < 0 ? (dir > 0 ? -1 : destinations.size()) : selectedIndex) + dir;
                while (next >= 0 && next < destinations.size() && !destinations.get(next).isEnabled()) {
                    next += dir;
                }
                if (next >= 0 && next < destinations.size()) {
                    selectedIndex = next;
                    travelButton.active = true;
                    if (selectedIndex < scrollOffset) scrollOffset = selectedIndex;
                    else if (selectedIndex >= scrollOffset + MAX_VISIBLE) scrollOffset = selectedIndex - MAX_VISIBLE + 1;
                }
                return true;
            }
            if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && travelButton.active) {
                onTravelClicked();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = Math.max(0, destinations.size() - MAX_VISIBLE);
        scrollOffset = Math.max(0, Math.min(scrollOffset - (int) Math.signum(scrollY), maxScroll));
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (event.button() == 0) {
            double mouseX = event.x();
            double mouseY = event.y();
            int px = layout.panelX();
            int py = layout.panelY();
            int listX = px + LIST_PADDING;
            int listY = py + layout.s(26);
            int listW = layout.panelW() - LIST_PADDING * 2;
            int visibleCount = Math.min(MAX_VISIBLE, destinations.size() - scrollOffset);

            if (mouseX >= listX && mouseX < listX + listW) {
                for (int i = 0; i < visibleCount; i++) {
                    int entryY = listY + i * ENTRY_H;
                    if (mouseY >= entryY && mouseY < entryY + ENTRY_H) {
                        int idx = i + scrollOffset;
                        if (idx < destinations.size() && destinations.get(idx).isEnabled()) {
                            selectedIndex = idx;
                            travelButton.active = true;
                            if (isDoubleClick) {
                                onTravelClicked();
                            }
                        }
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(event, isDoubleClick);
    }

    private void onTravelClicked() {
        if (selectedIndex < 0 || selectedIndex >= destinations.size()) return;
        FlooDestinationDto dto = destinations.get(selectedIndex);
        if (!dto.isEnabled()) return;
        ClientPacketDistributor.sendToServer(new FlooTravelRequestC2SPayload(dto.networkAddress()));
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
