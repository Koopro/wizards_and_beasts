package at.koopro.neo.client.gui;

import at.koopro.neo.form.FormRegistry;
import at.koopro.neo.network.ClientFormDataHolder;
import at.koopro.neo.network.FormChangeRequestC2SPacket;
import at.koopro.neo.network.SizeOverrideC2SPacket;
import at.koopro.neo.util.GuiUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.gui.widget.ExtendedSlider;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Debug GUI for the form/morph system.
 * Provides form selection, scale sliders, and transition testing.
 */
public class MorphDebugScreen extends Screen {

    private static final int PANEL_W = 400;
    private static final int PANEL_H = 300;

    private static final int COL_BG = 0xDD1E1610;
    private static final int COL_BORDER = 0xFF6B5B4E;
    private static final int COL_GOLD = 0xFFFFD700;
    private static final int COL_PARCHMENT = 0xFFE8D5B3;
    private static final int COL_DIM = 0xFF998877;

    private ExtendedSlider sliderX;
    private ExtendedSlider sliderY;
    private ExtendedSlider sliderZ;

    @Nullable private String selectedFormId;
    private int scrollOffset = 0;

    public MorphDebugScreen() {
        super(Component.literal("Morph Debug"));
    }

    @Override
    protected void init() {
        super.init();
        rebuild();
    }

    private void rebuild() {
        clearWidgets();

        int px = (width - PANEL_W) / 2;
        int py = (height - PANEL_H) / 2;

        // ── Form list (left side) ──
        List<String> formIds = FormRegistry.getAllFormIds();
        int listX = px + 10;
        int listY = py + 25;
        int btnW = 150;
        int btnH = 16;
        int maxVisible = 14;

        for (int i = 0; i < Math.min(maxVisible, formIds.size() - scrollOffset); i++) {
            int idx = i + scrollOffset;
            if (idx >= formIds.size()) break;
            String formId = formIds.get(idx);
            int y = listY + i * (btnH + 2);

            addRenderableWidget(Button.builder(
                    Component.literal(formId),
                    btn -> {
                        selectedFormId = formId;
                        rebuild();
                    }
            ).bounds(listX, y, btnW, btnH).build());
        }

        // Scroll buttons
        if (scrollOffset > 0) {
            addRenderableWidget(Button.builder(Component.literal("\u25B2"), btn -> {
                scrollOffset = Math.max(0, scrollOffset - 5);
                rebuild();
            }).bounds(listX + btnW + 4, listY, 20, 16).build());
        }
        if (scrollOffset + maxVisible < formIds.size()) {
            addRenderableWidget(Button.builder(Component.literal("\u25BC"), btn -> {
                scrollOffset = Math.min(formIds.size() - maxVisible, scrollOffset + 5);
                rebuild();
            }).bounds(listX + btnW + 4, listY + (maxVisible - 1) * (btnH + 2), 20, 16).build());
        }

        // ── Scale sliders (right side) ──
        int sliderX_pos = px + 190;
        int sliderY_pos = py + 25;
        int sliderW = 190;

        sliderX = new ExtendedSlider(sliderX_pos, sliderY_pos, sliderW, 20,
                Component.literal("X: "), Component.empty(),
                0.1, 5.0, 1.0, true);
        sliderY = new ExtendedSlider(sliderX_pos, sliderY_pos + 28, sliderW, 20,
                Component.literal("Y: "), Component.empty(),
                0.1, 5.0, 1.0, true);
        sliderZ = new ExtendedSlider(sliderX_pos, sliderY_pos + 56, sliderW, 20,
                Component.literal("Z: "), Component.empty(),
                0.1, 5.0, 1.0, true);

        addRenderableWidget(sliderX);
        addRenderableWidget(sliderY);
        addRenderableWidget(sliderZ);

        // Apply Scale button
        addRenderableWidget(Button.builder(Component.literal("Apply Scale"), btn -> {
            if (minecraft != null && minecraft.player != null) {
                UUID uuid = minecraft.player.getUUID();
                ClientPacketDistributor.sendToServer(new SizeOverrideC2SPacket(
                        uuid,
                        (float) sliderX.getValue(),
                        (float) sliderY.getValue(),
                        (float) sliderZ.getValue()));
            }
        }).bounds(sliderX_pos, sliderY_pos + 90, sliderW, 20).build());

        // Apply Form button
        addRenderableWidget(Button.builder(Component.literal("Apply Form"), btn -> {
            if (selectedFormId != null && minecraft != null && minecraft.player != null) {
                ClientPacketDistributor.sendToServer(new FormChangeRequestC2SPacket(
                        minecraft.player.getUUID(), selectedFormId));
            }
        }).bounds(sliderX_pos, sliderY_pos + 118, sliderW, 20).build());

        // Transition Test button
        addRenderableWidget(Button.builder(Component.literal("Test Transition"), btn -> {
            if (selectedFormId != null && minecraft != null && minecraft.player != null) {
                String currentFormId = getCurrentFormId();
                if (currentFormId != null && !currentFormId.equals(selectedFormId)) {
                    // Send form change — server TransitionManager handles the transition
                    ClientPacketDistributor.sendToServer(new FormChangeRequestC2SPacket(
                            minecraft.player.getUUID(), selectedFormId));
                }
            }
        }).bounds(sliderX_pos, sliderY_pos + 146, sliderW, 20).build());

        // Close button
        addRenderableWidget(Button.builder(Component.literal("Close"), btn -> onClose())
                .bounds(px + PANEL_W / 2 - 40, py + PANEL_H - 30, 80, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int px = (width - PANEL_W) / 2;
        int py = (height - PANEL_H) / 2;

        // Background panel
        graphics.fill(px, py, px + PANEL_W, py + PANEL_H, COL_BG);

        // Border
        GuiUtils.drawBorder(graphics, px, py, PANEL_W, PANEL_H, COL_BORDER);

        // Title
        graphics.drawCenteredString(font, "Morph Debug", px + PANEL_W / 2, py + 6, COL_GOLD);

        // Divider between list and controls
        graphics.fill(px + 180, py + 20, px + 181, py + PANEL_H - 35, COL_BORDER);

        // Selected form info
        if (selectedFormId != null) {
            int infoY = py + PANEL_H - 80;
            graphics.drawString(font, "Selected: " + selectedFormId,
                    px + 190, infoY, COL_PARCHMENT, false);

            var form = FormRegistry.get(selectedFormId);
            if (form != null) {
                graphics.drawString(font, "Model: " + form.modelType().getDisplayName(),
                        px + 190, infoY + 12, COL_DIM, false);
                graphics.drawString(font, "Size: " + form.sizeProfileId(),
                        px + 190, infoY + 24, COL_DIM, false);
            }
        }

        // Current form info
        String currentForm = getCurrentFormId();
        if (currentForm != null) {
            graphics.drawString(font, "Current: " + currentForm,
                    px + 10, py + PANEL_H - 45, COL_PARCHMENT, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Nullable
    private String getCurrentFormId() {
        if (minecraft == null || minecraft.player == null) return null;
        ClientFormDataHolder.FormData data = ClientFormDataHolder.get(minecraft.player.getUUID());
        return data != null ? data.formId() : null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
