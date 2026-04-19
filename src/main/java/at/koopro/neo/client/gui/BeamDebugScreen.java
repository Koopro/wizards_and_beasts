package at.koopro.neo.client.gui;

import at.koopro.neo.client.wand.BeamSettings;
import at.koopro.neo.spell.Spell;
import at.koopro.neo.spell.Spells;
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

    private static final int PANEL_W = 430;
    private static final int PANEL_H = 380;

    // MC-style colors
    private static final int COL_BG = 0xC8101010;
    private static final int COL_HIGHLIGHT_TOP = 0xFF555555;
    private static final int COL_SHADOW_BOT = 0xFF2D2D2D;
    private static final int COL_TITLE = 0xFFFFFFFF;
    private static final int COL_LABEL = 0xFFA0A0A0;
    private static final int COL_ACTIVE = 0xFFFFFF55;
    private static final int COL_DIVIDER = 0xFF373737;

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

    public BeamDebugScreen() {
        super(Component.literal("Beam Debug Editor"));
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

        int leftX = px + 10;
        int leftW = 125;
        int rightX = px + 150;
        int rightW = 260;
        int sliderH = 20;

        // ── Spell Presets (left panel, top) ──
        int presetY = py + 32;
        for (int i = 0; i < PRESET_IDS.length; i++) {
            Spell spell = Spells.byId(PRESET_IDS[i]);
            if (spell == null) continue;
            final Spell s = spell;
            int btnY = presetY + i * 18;
            addRenderableWidget(Button.builder(
                    Component.literal(spell.getDisplayName()),
                    btn -> {
                        BeamSettings.applySpellColor(s.getColor());
                        rebuild();
                    }
            ).bounds(leftX + 14, btnY, leftW - 14, 16).build());
        }

        // ── Layer Selection (left panel, middle) ──
        int layerY = presetY + PRESET_IDS.length * 18 + 18;
        for (int i = 0; i < 3; i++) {
            final int layerIdx = i;
            boolean selected = (i == selectedLayer);
            String prefix = selected ? "\u25B8 " : "  ";
            addRenderableWidget(Button.builder(
                    Component.literal(prefix + BeamSettings.LAYER_NAMES[i]),
                    btn -> {
                        selectedLayer = layerIdx;
                        rebuild();
                    }
            ).bounds(leftX, layerY + i * 22, leftW, 18).build());
        }

        // ── Layer sliders (right panel) ──
        BeamSettings.LayerSettings layer = BeamSettings.layers[selectedLayer];
        int sy = py + 40;
        int gap = 26;

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
        }).bounds(rightX, sy + gap * 6 + 4, 100, 20).build());

        // ── Global sliders (right panel, below divider) ──
        int globalY = py + 246;

        sliderRange = new ExtendedSlider(rightX, globalY, rightW, sliderH,
                Component.literal("Range: "), Component.empty(),
                1.0, 100.0, BeamSettings.range, true);
        addRenderableWidget(sliderRange);

        sliderSpeed = new ExtendedSlider(rightX, globalY + 24, rightW, sliderH,
                Component.literal("Speed: "), Component.empty(),
                0.01, 0.20, BeamSettings.speed, true);
        addRenderableWidget(sliderSpeed);

        sliderSegments = new ExtendedSlider(rightX, globalY + 48, rightW, sliderH,
                Component.literal("Segs: "), Component.empty(),
                1.0, 10.0, BeamSettings.segmentsPerUnit, false);
        addRenderableWidget(sliderSegments);

        sliderExtension = new ExtendedSlider(rightX, globalY + 72, rightW, sliderH,
                Component.literal("Extension: "), Component.literal(" b/t"),
                1.0, 30.0, BeamSettings.extensionSpeed, true);
        addRenderableWidget(sliderExtension);

        // ── Bottom buttons ──
        int bottomY = py + PANEL_H - 26;

        addRenderableWidget(Button.builder(Component.literal("Reset All"), btn -> {
            BeamSettings.resetAll();
            rebuild();
        }).bounds(px + PANEL_W / 2 - 130, bottomY, 80, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Close"), btn -> onClose())
                .bounds(px + PANEL_W / 2 + 50, bottomY, 80, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Write slider values to BeamSettings every frame for live preview
        applySliderValues();

        int px = (width - PANEL_W) / 2;
        int py = (height - PANEL_H) / 2;

        McStylePanel.drawPanel(graphics, px, py, PANEL_W, PANEL_H, COL_BG, COL_HIGHLIGHT_TOP, COL_SHADOW_BOT);

        // Title
        graphics.drawCenteredString(font, "Beam Debug Editor", px + PANEL_W / 2, py + 8, COL_TITLE);

        // Vertical divider between left and right panels
        graphics.fill(px + 143, py + 25, px + 144, py + PANEL_H - 30, COL_DIVIDER);

        // ── Left panel labels ──
        graphics.drawString(font, "Presets", px + 10, py + 22, COL_LABEL, false);

        // Spell color dots next to preset buttons
        int presetY = py + 32;
        for (int i = 0; i < PRESET_IDS.length; i++) {
            Spell spell = Spells.byId(PRESET_IDS[i]);
            if (spell == null) continue;
            int dotY = presetY + i * 18 + 4;
            graphics.fill(px + 12, dotY, px + 22, dotY + 8, spell.getColor());
        }

        int layerLabelY = presetY + PRESET_IDS.length * 18 + 6;
        graphics.drawString(font, "Layers", px + 10, layerLabelY, COL_LABEL, false);

        // ── Right panel labels ──
        String layerName = BeamSettings.LAYER_NAMES[selectedLayer];
        graphics.drawString(font, layerName, px + 150, py + 28, COL_ACTIVE, false);

        // Color preview swatch
        BeamSettings.LayerSettings layer = BeamSettings.layers[selectedLayer];
        int previewColor = 0xFF000000
                | (Math.min(255, (int) (layer.r * 255)) << 16)
                | (Math.min(255, (int) (layer.g * 255)) << 8)
                | Math.min(255, (int) (layer.b * 255));
        int swatchX = px + 150 + font.width(layerName) + 8;
        int swatchY = py + 27;
        graphics.fill(swatchX, swatchY, swatchX + 14, swatchY + 10, previewColor);
        McStylePanel.drawBorder(graphics, swatchX, swatchY, 14, 10, COL_HIGHLIGHT_TOP, COL_SHADOW_BOT);

        // Horizontal divider above Global section
        graphics.fill(px + 150, py + 232, px + 150 + 260, py + 233, COL_DIVIDER);
        graphics.drawString(font, "Global", px + 150, py + 236, COL_LABEL, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void applySliderValues() {
        // Global
        BeamSettings.range = (float) sliderRange.getValue();
        BeamSettings.speed = (float) sliderSpeed.getValue();
        BeamSettings.segmentsPerUnit = (int) sliderSegments.getValue();
        BeamSettings.extensionSpeed = (float) sliderExtension.getValue();

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
