package at.koopro.wizardsandbeasts.client.spell.gui;

import at.koopro.wizardsandbeasts.client.wand.BeamSettings;
import at.koopro.wizardsandbeasts.spell.cast.BeamRayResolver;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsAndBeastsUiTokens;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.gui.widget.ExtendedSlider;

/**
 * Debug GUI for live-editing beam VFX parameters.
 * Non-pausing so the beam renders behind the screen.
 */
public class BeamDebugScreen extends Screen {

    // Spell presets — representative color spectrum
    private static final String[] PRESET_IDS = {
            "stupefy", "incendio", "bombarda", "lumos",
            "avada_kedavra", "flipendo", "protego", "imperio"
    };

    private int selectedLayer = BeamSettings.OUTER;

    // Layer sliders
    private ExtendedSlider sliderWidth;
    private ExtendedSlider sliderR;
    private ExtendedSlider sliderG;
    private ExtendedSlider sliderB;
    private ExtendedSlider sliderAlpha;
    private ExtendedSlider sliderNoise;

    // Global sliders
    private ExtendedSlider sliderRange;
    private ExtendedSlider sliderSpeed;
    private ExtendedSlider sliderSegments;
    private ExtendedSlider sliderExtension;
    private GuiScaleHelper.Layout layout;

    public BeamDebugScreen() {
        super(Component.literal("Beam Debug Editor"));
    }

    @Override
    protected void init() {
        super.init();
        layout = GuiScaleHelper.Layout.panel(width, height,
                WizardsAndBeastsUiTokens.BeamDebug.PANEL_WIDTH,
                WizardsAndBeastsUiTokens.BeamDebug.PANEL_HEIGHT);
        rebuild();
    }

    private void rebuild() {
        clearWidgets();

        int px = layout.panelX();
        int py = layout.panelY();

        int leftX = px + layout.s(WizardsAndBeastsUiTokens.BeamDebug.LEFT_X);
        int leftW = layout.s(WizardsAndBeastsUiTokens.BeamDebug.LEFT_WIDTH);
        int rightX = leftX + leftW + layout.s(WizardsAndBeastsUiTokens.BeamDebug.LEFT_TO_RIGHT_GAP);
        int rightW = layout.s(WizardsAndBeastsUiTokens.BeamDebug.RIGHT_WIDTH);
        int sliderH = layout.s(WizardsAndBeastsUiTokens.BeamDebug.SLIDER_HEIGHT);

        // ── Spell Presets (left panel, top) ──
        int presetY = py + layout.s(WizardsAndBeastsUiTokens.BeamDebug.PRESET_START_Y);
        for (int i = 0; i < PRESET_IDS.length; i++) {
            Spell spell = Spells.byId(PRESET_IDS[i]);
            if (spell == null) continue;
            final Spell s = spell;
            int btnY = presetY + i * layout.s(WizardsAndBeastsUiTokens.BeamDebug.PRESET_BUTTON_Y_SPACING);
            addRenderableWidget(Button.builder(
                    Component.literal(spell.getDisplayName()),
                    btn -> {
                        BeamSettings.applySpellColor(s.getColor());
                        rebuild();
                    }
            ).bounds(leftX + layout.s(WizardsAndBeastsUiTokens.BeamDebug.PRESET_BUTTON_X_OFFSET),
                    btnY,
                    leftW - layout.s(WizardsAndBeastsUiTokens.BeamDebug.PRESET_BUTTON_X_OFFSET),
                    layout.s(WizardsAndBeastsUiTokens.BeamDebug.PRESET_BUTTON_HEIGHT)).build());
        }

        // ── Layer Selection (left panel, middle) ──
        int layerY = presetY + PRESET_IDS.length * WizardsAndBeastsUiTokens.BeamDebug.PRESET_BUTTON_Y_SPACING
                + layout.s(WizardsAndBeastsUiTokens.BeamDebug.LAYER_SECTION_GAP);
        for (int i = 0; i < WizardsAndBeastsUiTokens.BeamDebug.LAYER_COUNT; i++) {
            final int layerIdx = i;
            boolean selected = (i == selectedLayer);
            String prefix = selected ? "\u25B8 " : "  ";
            addRenderableWidget(Button.builder(
                    Component.literal(prefix + BeamSettings.LAYER_NAMES[i]),
                    btn -> {
                        selectedLayer = layerIdx;
                        rebuild();
                    }
            ).bounds(leftX,
                    layerY + i * layout.s(WizardsAndBeastsUiTokens.BeamDebug.LAYER_BUTTON_Y_SPACING),
                    leftW,
                    layout.s(WizardsAndBeastsUiTokens.BeamDebug.LAYER_BUTTON_HEIGHT)).build());
        }

        // ── Layer sliders (right panel) ──
        BeamSettings.LayerSettings layer = BeamSettings.layers[selectedLayer];
        int sy = py + layout.s(WizardsAndBeastsUiTokens.BeamDebug.LAYER_SLIDER_START_Y);
        int gap = layout.s(WizardsAndBeastsUiTokens.BeamDebug.LAYER_SLIDER_GAP);

        sliderWidth = new ExtendedSlider(rightX, sy, rightW, sliderH,
                Component.literal("Width: "), Component.empty(),
                0.01, 0.50, layer.width, true);
        addRenderableWidget(sliderWidth);

        sliderR = new ExtendedSlider(rightX, sy + gap, rightW, sliderH,
                Component.literal("Red: "), Component.empty(),
                0.0, 1.0, layer.r, true);
        addRenderableWidget(sliderR);

        sliderG = new ExtendedSlider(rightX, sy + gap * 2, rightW, sliderH,
                Component.literal("Green: "), Component.empty(),
                0.0, 1.0, layer.g, true);
        addRenderableWidget(sliderG);

        sliderB = new ExtendedSlider(rightX, sy + gap * 3, rightW, sliderH,
                Component.literal("Blue: "), Component.empty(),
                0.0, 1.0, layer.b, true);
        addRenderableWidget(sliderB);

        sliderAlpha = new ExtendedSlider(rightX, sy + gap * 4, rightW, sliderH,
                Component.literal("Alpha: "), Component.empty(),
                0.0, 1.0, layer.alpha, true);
        addRenderableWidget(sliderAlpha);

        sliderNoise = new ExtendedSlider(rightX, sy + gap * 5, rightW, sliderH,
                Component.literal("Noise: "), Component.empty(),
                0.0, 5.0, layer.noiseAmp, true);
        addRenderableWidget(sliderNoise);

        // Reset Layer
        addRenderableWidget(Button.builder(Component.literal("Reset Layer"), btn -> {
            BeamSettings.resetLayer(selectedLayer);
            rebuild();
        }).bounds(rightX,
                sy + gap * 6 + WizardsAndBeastsUiTokens.BeamDebug.RESET_LAYER_Y_EXTRA,
                layout.s(WizardsAndBeastsUiTokens.BeamDebug.RESET_LAYER_WIDTH),
                layout.s(WizardsAndBeastsUiTokens.BeamDebug.RESET_LAYER_HEIGHT)).build());

        // ── Global sliders (right panel, below divider) ──
        int globalY = py + layout.s(WizardsAndBeastsUiTokens.BeamDebug.GLOBAL_START_Y);

        sliderRange = new ExtendedSlider(rightX, globalY, rightW, sliderH,
                Component.literal("Range: "), Component.empty(),
                1.0, 100.0, BeamSettings.range, true);
        addRenderableWidget(sliderRange);

        sliderSpeed = new ExtendedSlider(rightX, globalY + layout.s(WizardsAndBeastsUiTokens.BeamDebug.GLOBAL_SLIDER_GAP), rightW, sliderH,
                Component.literal("Flicker: "), Component.empty(),
                0.01, 0.25, BeamSettings.speed, true);
        addRenderableWidget(sliderSpeed);

        sliderSegments = new ExtendedSlider(rightX, globalY + layout.s(WizardsAndBeastsUiTokens.BeamDebug.GLOBAL_SLIDER_GAP) * 2, rightW, sliderH,
                Component.literal("Segs: "), Component.empty(),
                1.0, 10.0, BeamSettings.segmentsPerUnit, false);
        addRenderableWidget(sliderSegments);

        sliderExtension = new ExtendedSlider(rightX, globalY + layout.s(WizardsAndBeastsUiTokens.BeamDebug.GLOBAL_SLIDER_GAP) * 3, rightW, sliderH,
                Component.literal("Extension: "), Component.literal(" b/t"),
                1.0, 30.0, BeamRayResolver.extensionBlocksPerTick(), true);
        addRenderableWidget(sliderExtension);

        // ── Bottom buttons ──
        int bottomY = py + layout.panelH() - layout.s(WizardsAndBeastsUiTokens.BeamDebug.BOTTOM_BUTTONS_BOTTOM_OFFSET);

        addRenderableWidget(Button.builder(Component.literal("Preset: Low"), btn -> {
            BeamSettings.applyPerformancePreset(BeamSettings.PerformancePreset.LOW);
            rebuild();
        }).bounds(px + layout.s(WizardsAndBeastsUiTokens.BeamDebug.LEFT_X),
                bottomY,
                layout.s(WizardsAndBeastsUiTokens.BeamDebug.BOTTOM_BUTTON_WIDTH),
                layout.s(WizardsAndBeastsUiTokens.BeamDebug.BOTTOM_BUTTON_HEIGHT)).build());
        addRenderableWidget(Button.builder(Component.literal("Preset: Medium"), btn -> {
            BeamSettings.applyPerformancePreset(BeamSettings.PerformancePreset.MEDIUM);
            rebuild();
        }).bounds(px + layout.s(WizardsAndBeastsUiTokens.BeamDebug.LEFT_X + WizardsAndBeastsUiTokens.BeamDebug.BOTTOM_BUTTON_WIDTH + 4),
                bottomY,
                layout.s(WizardsAndBeastsUiTokens.BeamDebug.BOTTOM_BUTTON_WIDTH),
                layout.s(WizardsAndBeastsUiTokens.BeamDebug.BOTTOM_BUTTON_HEIGHT)).build());
        addRenderableWidget(Button.builder(Component.literal("Preset: High"), btn -> {
            BeamSettings.applyPerformancePreset(BeamSettings.PerformancePreset.HIGH);
            rebuild();
        }).bounds(px + layout.s(WizardsAndBeastsUiTokens.BeamDebug.LEFT_X + (WizardsAndBeastsUiTokens.BeamDebug.BOTTOM_BUTTON_WIDTH + 4) * 2),
                bottomY,
                layout.s(WizardsAndBeastsUiTokens.BeamDebug.BOTTOM_BUTTON_WIDTH),
                layout.s(WizardsAndBeastsUiTokens.BeamDebug.BOTTOM_BUTTON_HEIGHT)).build());

        addRenderableWidget(Button.builder(Component.literal("Reset All"), btn -> {
            BeamSettings.resetAll();
            rebuild();
        }).bounds(px + layout.panelW() / 2 + layout.s(WizardsAndBeastsUiTokens.BeamDebug.RESET_ALL_X_OFFSET),
                bottomY,
                layout.s(WizardsAndBeastsUiTokens.BeamDebug.BOTTOM_BUTTON_WIDTH),
                layout.s(WizardsAndBeastsUiTokens.BeamDebug.BOTTOM_BUTTON_HEIGHT)).build());

        addRenderableWidget(Button.builder(Component.literal("Close"), btn -> onClose())
                .bounds(px + layout.panelW() / 2 + layout.s(WizardsAndBeastsUiTokens.BeamDebug.CLOSE_X_OFFSET),
                        bottomY,
                        layout.s(WizardsAndBeastsUiTokens.BeamDebug.BOTTOM_BUTTON_WIDTH),
                        layout.s(WizardsAndBeastsUiTokens.BeamDebug.BOTTOM_BUTTON_HEIGHT)).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderMenuBackground(graphics);
        // Write slider values to BeamSettings every frame for live preview
        applySliderValues();

        int px = layout.panelX();
        int py = layout.panelY();

        McStylePanel.drawTexturedPanel(graphics, px, py, layout.panelW(), layout.panelH());
        // Title
        graphics.drawCenteredString(font, "Beam Debug Editor",
                px + layout.panelW() / 2,
                py + layout.s(WizardsAndBeastsUiTokens.BeamDebug.TITLE_Y),
                WizardsAndBeastsUiTokens.BeamDebug.COLOR_TITLE);

        // Vertical divider between left and right panels
        graphics.fill(px + WizardsAndBeastsUiTokens.BeamDebug.VDIVIDER_X,
                py + WizardsAndBeastsUiTokens.BeamDebug.VDIVIDER_Y,
                px + WizardsAndBeastsUiTokens.BeamDebug.VDIVIDER_X + 1,
                py + WizardsAndBeastsUiTokens.BeamDebug.PANEL_HEIGHT - WizardsAndBeastsUiTokens.BeamDebug.VDIVIDER_BOTTOM_OFFSET,
                WizardsAndBeastsUiTokens.BeamDebug.COLOR_DIVIDER);

        // ── Left panel labels ──
        graphics.drawString(font, "Presets",
                px + WizardsAndBeastsUiTokens.BeamDebug.LEFT_X,
                py + WizardsAndBeastsUiTokens.BeamDebug.PRESET_LABEL_Y,
                WizardsAndBeastsUiTokens.BeamDebug.COLOR_LABEL, false);

        // Spell color dots next to preset buttons
        int presetY = py + WizardsAndBeastsUiTokens.BeamDebug.PRESET_START_Y;
        for (int i = 0; i < PRESET_IDS.length; i++) {
            Spell spell = Spells.byId(PRESET_IDS[i]);
            if (spell == null) continue;
            int dotY = presetY + i * WizardsAndBeastsUiTokens.BeamDebug.PRESET_BUTTON_Y_SPACING
                    + WizardsAndBeastsUiTokens.BeamDebug.PRESET_DOT_Y_OFFSET;
            graphics.fill(px + WizardsAndBeastsUiTokens.BeamDebug.PRESET_DOT_X, dotY,
                    px + WizardsAndBeastsUiTokens.BeamDebug.PRESET_DOT_X + WizardsAndBeastsUiTokens.BeamDebug.PRESET_DOT_WIDTH,
                    dotY + WizardsAndBeastsUiTokens.BeamDebug.PRESET_DOT_HEIGHT, spell.getColor());
        }

        int layerLabelY = presetY + PRESET_IDS.length * WizardsAndBeastsUiTokens.BeamDebug.PRESET_BUTTON_Y_SPACING
                + WizardsAndBeastsUiTokens.BeamDebug.LAYER_LABEL_EXTRA;
        graphics.drawString(font, "Layers", px + WizardsAndBeastsUiTokens.BeamDebug.LEFT_X, layerLabelY,
                WizardsAndBeastsUiTokens.BeamDebug.COLOR_LABEL, false);

        // ── Right panel labels ──
        String layerName = BeamSettings.LAYER_NAMES[selectedLayer];
        int rightX = px + WizardsAndBeastsUiTokens.BeamDebug.LEFT_X + WizardsAndBeastsUiTokens.BeamDebug.LEFT_WIDTH + WizardsAndBeastsUiTokens.BeamDebug.LEFT_TO_RIGHT_GAP;
        graphics.drawString(font, layerName, rightX, py + WizardsAndBeastsUiTokens.BeamDebug.RIGHT_TITLE_Y,
                WizardsAndBeastsUiTokens.BeamDebug.COLOR_ACTIVE, false);

        // Color preview swatch
        BeamSettings.LayerSettings layer = BeamSettings.layers[selectedLayer];
        int previewColor = 0xFF000000
                | (Math.min(255, (int) (layer.r * 255)) << 16)
                | (Math.min(255, (int) (layer.g * 255)) << 8)
                | Math.min(255, (int) (layer.b * 255));
        int swatchX = rightX + font.width(layerName) + WizardsAndBeastsUiTokens.BeamDebug.SWATCH_X_GAP;
        int swatchY = py + WizardsAndBeastsUiTokens.BeamDebug.SWATCH_Y;
        graphics.fill(swatchX, swatchY,
                swatchX + WizardsAndBeastsUiTokens.BeamDebug.SWATCH_WIDTH,
                swatchY + WizardsAndBeastsUiTokens.BeamDebug.SWATCH_HEIGHT, previewColor);
        McStylePanel.drawBorder(graphics, swatchX, swatchY,
                WizardsAndBeastsUiTokens.BeamDebug.SWATCH_WIDTH,
                WizardsAndBeastsUiTokens.BeamDebug.SWATCH_HEIGHT,
                WizardsAndBeastsUiTokens.BeamDebug.COLOR_HIGHLIGHT_TOP,
                WizardsAndBeastsUiTokens.BeamDebug.COLOR_SHADOW_BOTTOM);

        // Horizontal divider above Global section
        graphics.fill(rightX, py + WizardsAndBeastsUiTokens.BeamDebug.GLOBAL_DIVIDER_Y,
                rightX + WizardsAndBeastsUiTokens.BeamDebug.RIGHT_WIDTH, py + WizardsAndBeastsUiTokens.BeamDebug.GLOBAL_DIVIDER_Y + 1,
                WizardsAndBeastsUiTokens.BeamDebug.COLOR_DIVIDER);
        graphics.drawString(font, "Global", rightX, py + WizardsAndBeastsUiTokens.BeamDebug.GLOBAL_LABEL_Y,
                WizardsAndBeastsUiTokens.BeamDebug.COLOR_LABEL, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void applySliderValues() {
        // Global
        BeamSettings.range = (float) sliderRange.getValue();
        BeamSettings.speed = (float) sliderSpeed.getValue();
        BeamSettings.segmentsPerUnit = (int) sliderSegments.getValue();
        BeamRayResolver.setExtensionBlocksPerTick((float) sliderExtension.getValue());

        // Selected layer
        BeamSettings.LayerSettings layer = BeamSettings.layers[selectedLayer];
        layer.width = (float) sliderWidth.getValue();
        layer.r = (float) sliderR.getValue();
        layer.g = (float) sliderG.getValue();
        layer.b = (float) sliderB.getValue();
        layer.alpha = (float) sliderAlpha.getValue();
        layer.noiseAmp = (float) sliderNoise.getValue();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
