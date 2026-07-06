package at.koopro.wizardsandbeasts.client.gui;

import at.koopro.wizardsandbeasts.form.FormRegistry;
import at.koopro.wizardsandbeasts.client.form.state.ClientFormDataState;
import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import at.koopro.wizardsandbeasts.network.form.FormChangeRequestC2SPayload;
import at.koopro.wizardsandbeasts.network.form.SizeOverrideC2SPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.gui.widget.ExtendedSlider;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Debug GUI for the form/morph system.
 * Provides form selection, scale sliders, and transition testing.
 */
public class MorphDebugScreen extends Screen {

    private static final int PANEL_W = 400;
    private static final int PANEL_H = 300;

    private ExtendedSlider sliderHitboxH;
    private ExtendedSlider sliderModelScale;
    private ExtendedSlider sliderAspectX;
    private ExtendedSlider sliderAspectZ;

    @Nullable private String selectedFormId;
    private int scrollOffset = 0;
    private GuiScaleHelper.Layout layout;

    public MorphDebugScreen() {
        super(Component.literal("Morph Debug"));
    }

    @Override
    protected void init() {
        super.init();
        layout = GuiScaleHelper.Layout.panel(width, height, PANEL_W, PANEL_H);
        rebuild();
    }

    private void rebuild() {
        clearWidgets();

        int px = layout.panelX();
        int py = layout.panelY();

        // ── Form list (left side) ──
        List<String> formIds = FormRegistry.getAllFormIds();
        int listX = px + layout.s(10);
        int listY = py + layout.s(25);
        int btnW = layout.s(150);
        int btnH = layout.s(16);
        int maxVisible = Math.max(8, (layout.panelH() - layout.s(70)) / (btnH + layout.s(2)));

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
            }).bounds(listX + btnW + layout.s(4), listY, layout.s(20), layout.s(16)).build());
        }
        if (scrollOffset + maxVisible < formIds.size()) {
            addRenderableWidget(Button.builder(Component.literal("\u25BC"), btn -> {
                scrollOffset = Math.min(formIds.size() - maxVisible, scrollOffset + 5);
                rebuild();
            }).bounds(listX + btnW + layout.s(4), listY + (maxVisible - 1) * (btnH + layout.s(2)), layout.s(20), layout.s(16)).build());
        }

        // ── Size sliders (right side) ──
        int sliderX_pos = px + layout.s(190);
        int sliderY_pos = py + layout.s(25);
        int sliderW = layout.s(190);

        sliderHitboxH = new ExtendedSlider(sliderX_pos, sliderY_pos, sliderW, layout.s(20),
                Component.literal("Hitbox H: "), Component.empty(),
                0.1, 9.0, 1.8, true);
        sliderModelScale = new ExtendedSlider(sliderX_pos, sliderY_pos + layout.s(28), sliderW, layout.s(20),
                Component.literal("Model Scale: "), Component.empty(),
                0.1, 5.0, 1.0, true);
        sliderAspectX = new ExtendedSlider(sliderX_pos, sliderY_pos + layout.s(56), sliderW, layout.s(20),
                Component.literal("Aspect X: "), Component.empty(),
                0.1, 3.0, 1.0, true);
        sliderAspectZ = new ExtendedSlider(sliderX_pos, sliderY_pos + layout.s(84), sliderW, layout.s(20),
                Component.literal("Aspect Z: "), Component.empty(),
                0.1, 3.0, 1.0, true);

        addRenderableWidget(sliderHitboxH);
        addRenderableWidget(sliderModelScale);
        addRenderableWidget(sliderAspectX);
        addRenderableWidget(sliderAspectZ);

        // Apply Size button
        addRenderableWidget(Button.builder(Component.literal("Apply Size"), btn -> {
            if (minecraft != null && minecraft.player != null) {
                UUID uuid = minecraft.player.getUUID();
                ClientPacketDistributor.sendToServer(new SizeOverrideC2SPayload(
                        uuid,
                        (float) sliderHitboxH.getValue(),
                        (float) sliderModelScale.getValue(),
                        (float) sliderAspectX.getValue(),
                        (float) sliderAspectZ.getValue()));
            }
        }).bounds(sliderX_pos, sliderY_pos + layout.s(118), sliderW, layout.s(20)).build());

        // Apply Form button
        addRenderableWidget(Button.builder(Component.literal("Apply Form"), btn -> {
            if (selectedFormId != null && minecraft != null && minecraft.player != null) {
                ClientPacketDistributor.sendToServer(new FormChangeRequestC2SPayload(
                        minecraft.player.getUUID(), selectedFormId));
            }
        }).bounds(sliderX_pos, sliderY_pos + layout.s(146), sliderW, layout.s(20)).build());

        // Transition Test button
        addRenderableWidget(Button.builder(Component.literal("Test Transition"), btn -> {
            if (selectedFormId != null && minecraft != null && minecraft.player != null) {
                String currentFormId = getCurrentFormId();
                if (currentFormId != null && !currentFormId.equals(selectedFormId)) {
                    ClientPacketDistributor.sendToServer(new FormChangeRequestC2SPayload(
                            minecraft.player.getUUID(), selectedFormId));
                }
            }
        }).bounds(sliderX_pos, sliderY_pos + layout.s(174), sliderW, layout.s(20)).build());

        // Close button
        addRenderableWidget(Button.builder(Component.literal("Close"), btn -> onClose())
                .bounds(px + layout.panelW() / 2 - layout.s(40), py + layout.panelH() - layout.s(30), layout.s(80), layout.s(20)).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderMenuBackground(graphics);
        int px = layout.panelX();
        int py = layout.panelY();
        int panelW = layout.panelW();
        int panelH = layout.panelH();

        McStylePanel.drawTexturedPanel(graphics, px, py, panelW, panelH);

        graphics.drawCenteredString(font, "Morph Debug", px + panelW / 2, py + layout.s(6), 0xFF404040);

        // Divider between list and controls
        graphics.fill(px + layout.s(180), py + layout.s(20), px + layout.s(181), py + panelH - layout.s(35), 0xFF8B8B8B);

        // Selected form info
        if (selectedFormId != null) {
            int infoY = py + panelH - layout.s(80);
            graphics.drawString(font, "Selected: " + selectedFormId,
                    px + layout.s(190), infoY, 0xFF404040, false);

            var form = FormRegistry.get(selectedFormId);
            if (form != null) {
                graphics.drawString(font, "Model: " + form.modelType().getDisplayName(),
                        px + layout.s(190), infoY + layout.s(12), 0xFF606060, false);
                graphics.drawString(font, "Size: " + form.sizeProfileId(),
                        px + layout.s(190), infoY + layout.s(24), 0xFF606060, false);
            }
        }

        // Current form info
        String currentForm = getCurrentFormId();
        if (currentForm != null) {
            graphics.drawString(font, "Current: " + currentForm,
                    px + layout.s(10), py + panelH - layout.s(45), 0xFF404040, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Nullable
    private String getCurrentFormId() {
        if (minecraft == null || minecraft.player == null) return null;
        ClientFormDataState.FormData data = ClientFormDataState.get(minecraft.player.getUUID());
        return data != null ? data.formId() : null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
