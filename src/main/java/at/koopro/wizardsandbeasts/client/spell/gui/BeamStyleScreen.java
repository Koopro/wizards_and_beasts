package at.koopro.wizardsandbeasts.client.spell.gui;

import at.koopro.wizardsandbeasts.client.beam.BeamChannelClient;
import at.koopro.wizardsandbeasts.client.beam.BeamStyleEditor;
import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.gui.widget.ExtendedSlider;

import java.util.ArrayList;
import java.util.List;

/**
 * Live editor for the entity-based beam system: edits a {@link BeamStyleEditor} working copy that
 * {@code BeamEntityRenderer} reads every frame, and keeps a preview beam alive so there is always
 * something on screen to judge.
 *
 * <p>Non-pausing, like the legacy {@code BeamDebugScreen} — the world has to keep rendering behind
 * the panel or the whole thing is pointless.
 *
 * <p>Thirteen-odd knobs do not fit one column, so they are grouped into sections the way the legacy
 * screen groups its three layers: pick a section on the left, its sliders appear on the right.
 */
public class BeamStyleScreen extends Screen {

    private enum Section {
        CORE("Core"),
        GLOW("Glow"),
        SHAPE("Shape");

        final String label;

        Section(String label) {
            this.label = label;
        }
    }

    /** Beam spells plus a colour spread to sanity-check the style against. */
    private static final String[] PRESET_IDS = {
            "crucio", "avada_kedavra", "aguamenti", "stupefy", "incendio", "lumos"
    };

    private static final int PANEL_W = 320;
    private static final int PANEL_H = 210;
    private static final int PAD = 10;
    private static final int LEFT_W = 96;
    private static final int GAP = 8;
    private static final int ROW_H = 16;
    private static final int ROW_GAP = 19;
    private static final int TOP = 26;

    private static final int COLOR_TITLE = 0xFFFFD700;
    private static final int COLOR_LABEL = 0xFFAAAAAA;
    private static final int COLOR_ACTIVE = 0xFF55FF55;
    private static final int COLOR_DIVIDER = 0xFF555555;

    private Section section = Section.CORE;
    private GuiScaleHelper.Layout layout;

    /** Slider -> where its value goes. Rebuilt per section so the render loop stays section-agnostic. */
    private final List<Runnable> sliderWriters = new ArrayList<>();

    public BeamStyleScreen() {
        super(Component.literal("Beam Style Editor"));
    }

    @Override
    protected void init() {
        super.init();
        layout = GuiScaleHelper.Layout.panel(width, height, PANEL_W, PANEL_H);
        // Editing without the override on would silently do nothing.
        BeamStyleEditor.active = true;
        BeamChannelClient.startPreview();
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        sliderWriters.clear();

        int px = layout.panelX();
        int py = layout.panelY();
        int leftX = px + layout.s(PAD);
        int leftW = layout.s(LEFT_W);
        int rightX = leftX + leftW + layout.s(GAP);
        int rightW = layout.panelW() - (leftX - px) - leftW - layout.s(GAP + PAD);
        int rowH = layout.s(ROW_H);
        int rowGap = layout.s(ROW_GAP);

        // ── left: section picker ──
        int y = py + layout.s(TOP);
        for (Section s : Section.values()) {
            boolean selected = s == section;
            addRenderableWidget(Button.builder(
                    Component.literal((selected ? "▸ " : "  ") + s.label),
                    btn -> {
                        section = s;
                        rebuild();
                    }).bounds(leftX, y, leftW, rowH).build());
            y += rowH + layout.s(2);
        }

        // ── left: shape + blend toggles ──
        y += layout.s(6);
        addRenderableWidget(Button.builder(
                Component.literal("Shape: " + (BeamStyleEditor.shapeType == BeamStyleEditor.ShapeType.LIGHTNING
                        ? "Lightning" : "Laser")),
                btn -> {
                    BeamStyleEditor.shapeType =
                            BeamStyleEditor.shapeType == BeamStyleEditor.ShapeType.LIGHTNING
                                    ? BeamStyleEditor.ShapeType.LASER
                                    : BeamStyleEditor.ShapeType.LIGHTNING;
                    rebuild();
                }).bounds(leftX, y, leftW, rowH).build());
        y += rowH + layout.s(2);

        addRenderableWidget(Button.builder(
                Component.literal("Blend: " + (BeamStyleEditor.additive ? "Additive" : "Alpha")),
                btn -> {
                    BeamStyleEditor.additive = !BeamStyleEditor.additive;
                    rebuild();
                }).bounds(leftX, y, leftW, rowH).build());
        y += rowH + layout.s(6);

        // ── left: seed from a spell ──
        for (String id : PRESET_IDS) {
            Spell spell = Spells.byId(id);
            if (spell == null) {
                continue;
            }
            addRenderableWidget(Button.builder(
                    Component.literal(spell.getDisplayName()),
                    btn -> {
                        BeamStyleEditor.loadFromSpell(spell);
                        rebuild();
                    }).bounds(leftX, y, leftW, layout.s(13)).build());
            y += layout.s(14);
        }

        // ── right: sliders for the selected section ──
        int sy = py + layout.s(TOP);
        switch (section) {
            case CORE -> {
                addColorSliders(rightX, sy, rightW, rowH, rowGap, true);
                addSlider(rightX, sy + rowGap * 3, rightW, rowH, "Core Opacity", 0.0, 1.0,
                        BeamStyleEditor.coreOpacity, v -> BeamStyleEditor.coreOpacity = (float) v);
                addSlider(rightX, sy + rowGap * 4, rightW, rowH, "Width (px)", 0.25, 12.0,
                        BeamStyleEditor.width, v -> BeamStyleEditor.width = (float) v);
                addSlider(rightX, sy + rowGap * 5, rightW, rowH, "Height (px)", 0.25, 12.0,
                        BeamStyleEditor.height, v -> BeamStyleEditor.height = (float) v);
            }
            case GLOW -> {
                addColorSliders(rightX, sy, rightW, rowH, rowGap, false);
                addSlider(rightX, sy + rowGap * 3, rightW, rowH, "Glow Opacity", 0.0, 1.0,
                        BeamStyleEditor.glowOpacity, v -> BeamStyleEditor.glowOpacity = (float) v);
                addIntSlider(rightX, sy + rowGap * 4, rightW, rowH, "Bloom Layers", 0, 8,
                        BeamStyleEditor.bloomLayers, v -> BeamStyleEditor.bloomLayers = v);
            }
            case SHAPE -> {
                addSlider(rightX, sy, rightW, rowH, "Range", 2.0, 64.0,
                        BeamStyleEditor.previewRange, v -> {
                            BeamStyleEditor.previewRange = (float) v;
                            BeamChannelClient.syncPreviewRange();
                        });
                addSlider(rightX, sy + rowGap, rightW, rowH, "Spin", -20.0, 20.0,
                        BeamStyleEditor.spin, v -> BeamStyleEditor.spin = (float) v);
                if (BeamStyleEditor.shapeType == BeamStyleEditor.ShapeType.LIGHTNING) {
                    addIntSlider(rightX, sy + rowGap * 2, rightW, rowH, "Segments", 1, 32,
                            BeamStyleEditor.segments, v -> BeamStyleEditor.segments = v);
                    addSlider(rightX, sy + rowGap * 3, rightW, rowH, "Spread (px)", 0.0, 24.0,
                            BeamStyleEditor.spread, v -> BeamStyleEditor.spread = (float) v);
                    addIntSlider(rightX, sy + rowGap * 4, rightW, rowH, "Re-roll (ticks)", 1, 40,
                            BeamStyleEditor.frequency, v -> BeamStyleEditor.frequency = v);
                }
            }
        }

        // ── bottom ──
        int bottomY = py + layout.panelH() - layout.s(PAD + ROW_H);
        int btnW = layout.s(70);
        addRenderableWidget(Button.builder(Component.literal("Reset"), btn -> {
            BeamStyleEditor.reset();
            rebuild();
        }).bounds(leftX, bottomY, btnW, rowH).build());

        addRenderableWidget(Button.builder(
                Component.literal(BeamStyleEditor.active ? "Override: ON" : "Override: OFF"),
                btn -> {
                    BeamStyleEditor.active = !BeamStyleEditor.active;
                    rebuild();
                }).bounds(leftX + btnW + layout.s(4), bottomY, layout.s(96), rowH).build());

        addRenderableWidget(Button.builder(Component.literal("Close"), btn -> onClose())
                .bounds(px + layout.panelW() - layout.s(PAD) - btnW, bottomY, btnW, rowH).build());
    }

    /** R/G/B for either the core or the glow colour, sharing one row layout. */
    private void addColorSliders(int x, int y, int w, int h, int gap, boolean core) {
        int rgb = core ? BeamStyleEditor.coreColor : BeamStyleEditor.glowColor;
        addChannel(x, y, w, h, "Red", rgb, 16, core);
        addChannel(x, y + gap, w, h, "Green", rgb, 8, core);
        addChannel(x, y + gap * 2, w, h, "Blue", rgb, 0, core);
    }

    private void addChannel(int x, int y, int w, int h, String name, int rgb, int shift, boolean core) {
        addSlider(x, y, w, h, name, 0.0, 1.0, BeamStyleEditor.channel(rgb, shift), v -> {
            if (core) {
                BeamStyleEditor.coreColor = BeamStyleEditor.withChannel(BeamStyleEditor.coreColor, shift, (float) v);
            } else {
                BeamStyleEditor.glowColor = BeamStyleEditor.withChannel(BeamStyleEditor.glowColor, shift, (float) v);
            }
        });
    }

    private void addSlider(int x, int y, int w, int h, String label,
                           double min, double max, double value, java.util.function.DoubleConsumer sink) {
        ExtendedSlider slider = new ExtendedSlider(x, y, w, h,
                Component.literal(label + ": "), Component.empty(), min, max, value, true);
        addRenderableWidget(slider);
        sliderWriters.add(() -> sink.accept(slider.getValue()));
    }

    private void addIntSlider(int x, int y, int w, int h, String label,
                              int min, int max, int value, java.util.function.IntConsumer sink) {
        ExtendedSlider slider = new ExtendedSlider(x, y, w, h,
                Component.literal(label + ": "), Component.empty(), min, max, value, false);
        addRenderableWidget(slider);
        sliderWriters.add(() -> sink.accept(slider.getValueInt()));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderMenuBackground(graphics);
        // Push slider values every frame — that is what makes the beam behind the panel live.
        sliderWriters.forEach(Runnable::run);

        int px = layout.panelX();
        int py = layout.panelY();
        McStylePanel.drawTexturedPanel(graphics, px, py, layout.panelW(), layout.panelH());

        graphics.drawCenteredString(font, "Beam Style Editor",
                px + layout.panelW() / 2, py + layout.s(8), COLOR_TITLE);

        int leftX = px + layout.s(PAD);
        int rightX = leftX + layout.s(LEFT_W) + layout.s(GAP);
        graphics.fill(rightX - layout.s(GAP / 2), py + layout.s(TOP - 4),
                rightX - layout.s(GAP / 2) + 1, py + layout.panelH() - layout.s(TOP),
                COLOR_DIVIDER);

        graphics.drawString(font, section.label + " — live",
                rightX, py + layout.s(TOP - 12), COLOR_ACTIVE, false);

        // Swatch for whichever colour this section edits, so the numbers have something to mean.
        if (section != Section.SHAPE) {
            int rgb = section == Section.CORE ? BeamStyleEditor.coreColor : BeamStyleEditor.glowColor;
            int sx = px + layout.panelW() - layout.s(PAD + 24);
            int syy = py + layout.s(TOP - 13);
            graphics.fill(sx, syy, sx + layout.s(24), syy + layout.s(9), 0xFF000000 | rgb);
        }

        if (!BeamStyleEditor.active) {
            graphics.drawString(font, "override off — beams use their spell's look",
                    leftX, py + layout.panelH() - layout.s(PAD + ROW_H + 11), COLOR_LABEL, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        // The preview is the editor's, not the world's — it goes when the screen goes. The override
        // stays as set, so a beam cast right after closing still shows what was just dialled in.
        BeamChannelClient.stopPreview();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
