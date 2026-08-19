package at.koopro.wizardsandbeasts.client.skill.gui;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import at.koopro.wizardsandbeasts.client.gui.WizardsAndBeastsUiTokens;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedButton;
import at.koopro.wizardsandbeasts.client.heritage.state.ClientHeritageDataState;
import at.koopro.wizardsandbeasts.client.skill.state.ClientSkillDataState;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;
import at.koopro.wizardsandbeasts.network.skill.SkillUnlockC2SPayload;
import at.koopro.wizardsandbeasts.skill.Skill;
import at.koopro.wizardsandbeasts.skill.SkillTreeId;
import at.koopro.wizardsandbeasts.skill.SkillTrees;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Star-chart canvas over the player's audience skill web: a three-layer parallax night sky, nodes
 * as tinted star sprites (size = magnitude, state = shape and brightness), connections as glowing
 * ley-lines, constellation glyphs and names fading out as you zoom in to build. Drag to pan, scroll
 * to zoom toward the cursor, click an allocatable star to send the {@link SkillUnlockC2SPayload}
 * roundtrip (state applies on the server's sync response).
 *
 * <p>Nothing on this screen is drawn with a shape primitive. The rings were a midpoint circle of
 * one-pixel {@code fill}s, the edges a Bresenham run, the pips two crossed rectangles and the
 * chrome a set of flat boxes — all of it stair-stepped and flat under sprites that were neither.
 * Every one of those is now a sprite from {@code tools/skill_chart_textures.py} or the
 * {@code star_chart} skin, and the only thing this class draws directly is text.
 */
public class SkillTreeScreen extends Screen {

    private static final double MIN_ZOOM = 0.25;
    private static final double MAX_ZOOM = 2.0;
    private static final double ZOOM_STEP = 1.25;
    private static final int CULL_PAD = 48;

    /**
     * Parallax factors for the three sky layers, back to front.
     *
     * <p>One tile cannot have depth. What reads as distance is the difference in how far each
     * layer travels against the pan, which is why the far field barely moves and the near stars
     * keep up with the chart.
     */
    private static final double PARALLAX_FAR = 0.10;
    private static final double PARALLAX_NEBULA = 0.18;
    private static final double PARALLAX_NEAR = 0.32;

    /** Survey rings (world units) centered on Polaris — the wizard notable band boundaries. */
    private static final int[] SURVEY_RINGS = {120, 200, 280};
    private static final int SURVEY_RING_COLOR = 0x2E9FB8E8;

    private SkillTreeId.@Nullable Audience audience;
    /** Player heritage/variant, synced truth — drives audience selection and which regions are sealed. */
    private @Nullable Heritage heritage;
    private @Nullable HeritageVariant variant;
    /** Regions the player's capability tags seal shut: rendered permanently-locked, non-interactive. */
    private final Set<SkillTreeId> sealedTrees = EnumSet.noneOf(SkillTreeId.class);
    private List<Skill> webNodes = List.of();
    /** Constellation label cache: recomputed only when the node list instance changes. */
    private final Map<SkillTreeId, double[]> labelCentroids = new EnumMap<>(SkillTreeId.class);
    private final Map<SkillTreeId, Component> labelText = new EnumMap<>(SkillTreeId.class);

    private double panX;
    private double panY;
    private double zoom = 1.0;
    private boolean panned; // suppress click-allocate after a drag

    // Panel height is not cached: the chrome takes the whole layout now, and a field nothing reads
    // is a knob that drifts.
    private int panelX;
    private int panelY;
    private int panelW;
    /** The well: the recessed frame the chart sits in, outer edge. */
    private int viewportX;
    private int viewportY;
    private int viewportW;
    private int viewportH;
    /**
     * The chart itself: the well inset by its own frame.
     *
     * <p>Separate from the well because the sky is opaque and would otherwise paint straight over
     * the frame that is supposed to contain it — the well would be drawn, then hidden, every
     * frame. The inset is the raw sprite border, not a scaled one: {@code drawNineSlice} keeps its
     * corners at native size at any panel scale, which is what stops the frame from smearing.
     */
    private int chartX;
    private int chartY;
    private int chartW;
    private int chartH;
    private String resolvedTitle = "Skills";
    private GuiScaleHelper.Layout layout;

    private @Nullable Skill hoveredNode;

    public SkillTreeScreen() {
        super(Component.translatable("screen.wizards_and_beasts.skill_tree.title"));
    }

    @Override
    protected void init() {
        super.init();
        String titleText = Component.translatable("screen.wizards_and_beasts.skill_tree.title").getString();
        resolvedTitle = titleText.startsWith("screen.") ? "Skills" : titleText;
        layout = GuiScaleHelper.Layout.panel(width, height,
                WizardsAndBeastsUiTokens.SkillTree.PANEL_WIDTH, WizardsAndBeastsUiTokens.SkillTree.PANEL_HEIGHT);
        panelW = layout.panelW();
        panelX = layout.panelX();
        panelY = layout.panelY();
        viewportW = layout.s(WizardsAndBeastsUiTokens.SkillTree.VIEWPORT_WIDTH);
        viewportH = layout.s(WizardsAndBeastsUiTokens.SkillTree.VIEWPORT_HEIGHT);
        viewportX = panelX + layout.s(WizardsAndBeastsUiTokens.SkillTree.VIEWPORT_X);
        viewportY = panelY + layout.s(WizardsAndBeastsUiTokens.SkillTree.VIEWPORT_Y);
        int frame = at.koopro.wizardsandbeasts.client.gui.WizardsMetrics.PANEL_SPRITE_BORDER;
        chartX = viewportX + frame;
        chartY = viewportY + frame;
        chartW = Math.max(16, viewportW - frame * 2);
        chartH = Math.max(16, viewportH - frame * 2);

        SkillTreeId.Audience previous = audience;
        heritage = ClientHeritageDataState.get().getSelectedHeritage();
        variant = ClientHeritageDataState.get().getSelectedHeritageVariant();
        audience = SkillTreeId.audienceForHeritage(heritage, variant);
        recomputeSealedRegions();
        refreshWeb();
        if (previous == null) {
            centerOnWeb();
        }
        addVocationButton();
        addChartControls();
    }

    /**
     * The only in-game way into {@link VocationSelectionScreen}. Vocations were command-only, so on a
     * world without cheats the specialization layer could not be reached at all.
     */
    private void addVocationButton() {
        int buttonW = layout.s(WizardsAndBeastsUiTokens.SkillTree.VOCATION_BUTTON_W);
        int buttonH = layout.s(WizardsAndBeastsUiTokens.SkillTree.VOCATION_BUTTON_H);
        Component label = at.koopro.wizardsandbeasts.client.skill.state.ClientVocationCache.primary()
                .map(at.koopro.wizardsandbeasts.skill.vocation.VocationRegistry::get)
                .map(vocation -> Component.translatable("screen.wizards_and_beasts.vocation.button.set",
                        vocation.displayName()))
                .orElse(Component.translatable("screen.wizards_and_beasts.vocation.button.none"));
        // Skinned rather than a vanilla Button: this was the one piece of Minecraft grey stone left
        // on a screen made of night sky and brass.
        addRenderableWidget(chartButton(
                panelX + panelW - layout.s(WizardsAndBeastsUiTokens.SkillTree.SEAL_INSET
                        + McStylePanel.SEAL_SIZE + 4) - buttonW,
                panelY + layout.s(3), buttonW, buttonH, label, null,
                () -> {
                    if (minecraft != null) {
                        minecraft.setScreen(new VocationSelectionScreen(this));
                    }
                }));
    }

    /**
     * Zoom and recenter, bottom-right inside the well.
     *
     * <p>Scroll-to-zoom and drag-to-pan are still the fast path; these exist because a player who
     * has panned off the web has no way back short of closing the screen, and a trackpad without
     * a scroll wheel has no way to zoom at all.
     */
    private void addChartControls() {
        int size = layout.s(WizardsAndBeastsUiTokens.SkillTree.CONTROL_SIZE);
        int gap = layout.s(WizardsAndBeastsUiTokens.SkillTree.CONTROL_GAP);
        int margin = layout.s(WizardsAndBeastsUiTokens.SkillTree.CONTROL_MARGIN);
        int y = chartY + chartH - margin - size;
        int x = chartX + chartW - margin - size;

        addRenderableWidget(iconControl(x, y, size, SkillTreeChartTextures.ICON_RECENTER,
                "recenter", this::centerOnWeb));
        x -= size + gap;
        addRenderableWidget(iconControl(x, y, size, SkillTreeChartTextures.ICON_ZOOM_IN,
                "zoom_in", () -> zoomAboutChartCenter(ZOOM_STEP)));
        x -= size + gap;
        addRenderableWidget(iconControl(x, y, size, SkillTreeChartTextures.ICON_ZOOM_OUT,
                "zoom_out", () -> zoomAboutChartCenter(1.0 / ZOOM_STEP)));
    }

    /**
     * An icon-only control. The label is empty, so the name lives in the tooltip — which is also
     * what the narrator reads, and a button announced as "" is a button a screen reader cannot use.
     */
    private ThemedButton iconControl(int x, int y, int size, Identifier icon, String key, Runnable action) {
        Component name = Component.translatable("screen.wizards_and_beasts.skill_tree.control." + key);
        ThemedButton button = chartButton(x, y, size, size, Component.empty(), icon, action);
        button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(name));
        return button;
    }

    private ThemedButton chartButton(int x, int y, int w, int h, Component label,
                                     @Nullable Identifier icon, Runnable action) {
        return ThemedButton.skinned(x, y, w, h, label, action, McStylePanel.SKIN_STAR_CHART,
                icon, SkillTreeChartTextures.ICON_SIZE,
                SkillTreeChartTextures.CHART_INK, SkillTreeChartTextures.NIGHT_TEXT_DIM);
    }

    /** Seals every region in this audience whose capability requirement the player doesn't meet. */
    private void recomputeSealedRegions() {
        sealedTrees.clear();
        for (SkillTreeId tree : SkillTreeId.values()) {
            if (tree.getAudience() == audience
                    && !SkillTreeId.meetsRequirement(tree.getRequirement(), heritage, variant)) {
                sealedTrees.add(tree);
            }
        }
    }

    /** Re-reads the synced cache; recomputes label centroids only when the list instance changed. */
    private void refreshWeb() {
        List<Skill> latest = SkillTrees.clientWebNodes(audience);
        if (latest == webNodes) {
            return;
        }
        webNodes = latest;
        labelCentroids.clear();
        labelText.clear();
        Map<SkillTreeId, double[]> sums = new EnumMap<>(SkillTreeId.class);
        for (Skill node : webNodes) {
            double[] sum = sums.computeIfAbsent(node.getTree(), k -> new double[3]);
            sum[0] += node.getX();
            sum[1] += node.getY();
            sum[2]++;
        }
        sums.forEach((tree, sum) -> {
            if (sum[2] > 0) {
                labelCentroids.put(tree, new double[]{sum[0] / sum[2], sum[1] / sum[2]});
                labelText.put(tree, Component.translatable("skilltree.region." + tree.getId() + ".constellation")
                        .withStyle(ChatFormatting.ITALIC));
            }
        });
    }

    /** Start centered on the web's bounding box (the wizard web centers on Polaris at 0,0). */
    private void centerOnWeb() {
        if (webNodes.isEmpty()) {
            panX = 0;
            panY = 0;
            return;
        }
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Skill node : webNodes) {
            minX = Math.min(minX, node.getX());
            maxX = Math.max(maxX, node.getX());
            minY = Math.min(minY, node.getY());
            maxY = Math.max(maxY, node.getY());
        }
        panX = (minX + maxX) / 2.0;
        panY = (minY + maxY) / 2.0;
        zoom = 1.0;
    }

    // ── World ↔ screen transform: screen = (world − pan) × zoom + chartCenter ──

    private double toScreenX(double worldX) {
        return (worldX - panX) * zoom + chartX + chartW / 2.0;
    }

    private double toScreenY(double worldY) {
        return (worldY - panY) * zoom + chartY + chartH / 2.0;
    }

    private double toWorldX(double screenX) {
        return (screenX - chartX - chartW / 2.0) / zoom + panX;
    }

    private double toWorldY(double screenY) {
        return (screenY - chartY - chartH / 2.0) / zoom + panY;
    }

    /** Star sprite draw size (px) per node class before zoom. */
    private static int baseSpritePx(Skill node, boolean polaris) {
        if (polaris) return 34;
        return switch (node.getSize()) {
            case SMALL -> 12;
            case NOTABLE -> 18;
            case KEYSTONE -> 26;
        };
    }

    private static int hitRadius(Skill node, boolean polaris) {
        return baseSpritePx(node, polaris) / 2;
    }

    private boolean isPolaris(Skill node) {
        return node.isRoot() && node.getTree().getAudience() == SkillTreeId.Audience.WIZARD;
    }

    // ── Input ──

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
        if (event.button() == 0 && insideChart(event.x(), event.y())) {
            panX -= dragX / zoom;
            panY -= dragY / zoom;
            panned = true;
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!insideChart(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        zoomAbout(mouseX, mouseY, scrollY > 0 ? 1.15 : 1.0 / 1.15);
        return true;
    }

    /** Zoom keeping the world point under {@code (anchorX, anchorY)} pinned there. */
    private void zoomAbout(double anchorX, double anchorY, double factor) {
        double worldX = toWorldX(anchorX);
        double worldY = toWorldY(anchorY);
        zoom = Math.clamp(zoom * factor, MIN_ZOOM, MAX_ZOOM);
        panX = worldX - (anchorX - chartX - chartW / 2.0) / zoom;
        panY = worldY - (anchorY - chartY - chartH / 2.0) / zoom;
    }

    private void zoomAboutChartCenter(double factor) {
        zoomAbout(chartX + chartW / 2.0, chartY + chartH / 2.0, factor);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (event.button() == 0) {
            boolean wasPan = panned;
            panned = false;
            if (!wasPan && hoveredNode != null) {
                tryAllocate(hoveredNode);
                return true;
            }
        }
        return super.mouseReleased(event);
    }

    /**
     * Client-side pre-check is cosmetic UX only — the server re-validates everything
     * (adjacency, cap, affordability, audience) in {@code SkillSystemAPI.evaluateUnlock}.
     */
    private void tryAllocate(Skill node) {
        if (sealedTrees.contains(node.getTree())) {
            return; // sealed region: non-interactive (server would reject the payload anyway)
        }
        PlayerSkillData data = ClientSkillDataState.get();
        if (data.getSkillLevel(node.getId()) >= node.getMaxLevel()) {
            return;
        }
        ClientPacketDistributor.sendToServer(new SkillUnlockC2SPayload(node.getId()));
    }

    private boolean insideChart(double mouseX, double mouseY) {
        return mouseX >= chartX && mouseX <= chartX + chartW
                && mouseY >= chartY && mouseY <= chartY + chartH;
    }

    private boolean onCanvas(double screenX, double screenY) {
        return screenX >= chartX - CULL_PAD && screenX <= chartX + chartW + CULL_PAD
                && screenY >= chartY - CULL_PAD && screenY <= chartY + chartH + CULL_PAD;
    }

    // ── Render ──

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderMenuBackground(graphics);
        refreshWeb(); // hot-swap: a /reload definition resync replaces the cached list instance
        PlayerSkillData data = ClientSkillDataState.get();
        SkillTreeRenderHelper.renderWindowFrame(graphics, font, layout, resolvedTitle);

        // The well is drawn before the scissor so its own frame is not clipped away by it.
        McStylePanel.drawSkinInset(graphics, McStylePanel.SKIN_STAR_CHART,
                viewportX, viewportY, viewportW, viewportH);

        graphics.enableScissor(chartX, chartY, chartX + chartW, chartY + chartH);
        drawSky(graphics);
        drawSurveyRings(graphics);
        drawEdges(graphics, data);
        hoveredNode = drawNodes(graphics, data, mouseX, mouseY);
        drawConstellationLabels(graphics);
        drawVignette(graphics);
        graphics.disableScissor();

        SkillTreeRenderHelper.renderFooter(graphics, font, layout, data);

        super.render(graphics, mouseX, mouseY, partialTick);

        if (hoveredNode != null) {
            boolean adjacencyOpen = hoveredNode.isRoot() || hasAllocatedNeighbor(data, hoveredNode);
            boolean sealed = sealedTrees.contains(hoveredNode.getTree());
            SkillTreeRenderHelper.renderTooltipCard(graphics, font, hoveredNode, mouseX, mouseY,
                    data.getSkillLevel(hoveredNode.getId()), data.getSkillPoints(), adjacencyOpen, sealed);
        }
    }

    /** The three sky layers, back to front, each scrolled at its own fraction of the pan. */
    private void drawSky(GuiGraphics graphics) {
        tileLayer(graphics, SkillTreeChartTextures.STARFIELD_FAR,
                SkillTreeChartTextures.STARFIELD_SIZE, PARALLAX_FAR);
        tileLayer(graphics, SkillTreeChartTextures.NEBULA,
                SkillTreeChartTextures.NEBULA_SIZE, PARALLAX_NEBULA);
        tileLayer(graphics, SkillTreeChartTextures.STARFIELD,
                SkillTreeChartTextures.STARFIELD_SIZE, PARALLAX_NEAR);
    }

    /**
     * One sky layer, tiled across the chart at 1:1.
     *
     * <p>Drawn at native size rather than scaled with the chart, so the grain never swims when the
     * player zooms — a sky that zooms is a wallpaper, not a sky.
     */
    private void tileLayer(GuiGraphics graphics, Identifier tex, int tile, double parallax) {
        int offX = Math.floorMod((int) Math.round(panX * parallax * zoom), tile);
        int offY = Math.floorMod((int) Math.round(panY * parallax * zoom), tile);
        for (int x = chartX - offX; x < chartX + chartW; x += tile) {
            for (int y = chartY - offY; y < chartY + chartH; y += tile) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0.0F, 0.0F,
                        tile, tile, tile, tile);
            }
        }
    }

    /** Faint concentric survey rings centered on Polaris, one stretched sprite each. */
    private void drawSurveyRings(GuiGraphics graphics) {
        int cx = (int) Math.round(toScreenX(0));
        int cy = (int) Math.round(toScreenY(0));
        for (int worldR : SURVEY_RINGS) {
            int diameter = (int) Math.round(worldR * 2 * zoom);
            if (diameter < 8) {
                continue;
            }
            McStylePanel.drawTintedCentered(graphics, SkillTreeChartTextures.SURVEY_RING,
                    cx, cy, diameter, SURVEY_RING_COLOR);
        }
    }

    private void drawEdges(GuiGraphics graphics, PlayerSkillData data) {
        // Slow ley-line shimmer: one global phase, subtle enough to miss on a screenshot.
        float shimmer = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 1600.0 * Math.PI));
        int allocatedAlpha = 205 + (int) (40 * shimmer);
        for (Skill node : webNodes) {
            double ax = toScreenX(node.getX());
            double ay = toScreenY(node.getY());
            for (String neighborId : SkillTrees.clientNeighbors(node.getId())) {
                if (neighborId.compareTo(node.getId()) <= 0) {
                    continue; // draw each symmetric edge once
                }
                Skill neighbor = SkillTrees.clientById(neighborId);
                if (neighbor == null) {
                    continue;
                }
                double bx = toScreenX(neighbor.getX());
                double by = toScreenY(neighbor.getY());
                if (!onCanvas(ax, ay) && !onCanvas(bx, by)) {
                    continue; // cull: both endpoints off-chart
                }
                boolean aLit = data.getSkillLevel(node.getId()) >= 1;
                boolean bLit = data.getSkillLevel(neighborId) >= 1;
                int color;
                int stroke;
                if (aLit && bLit) {
                    color = SkillTreeChartTextures.withAlpha(SkillTreeChartTextures.GOLD, allocatedAlpha);
                    stroke = WizardsAndBeastsUiTokens.SkillTree.LEY_ALLOCATED_STROKE;
                } else if (aLit || bLit) {
                    // Frontier edge: slightly lifted, carrying the region tint when intra-region.
                    int tint = node.getTree() == neighbor.getTree()
                            ? SkillTreeChartTextures.regionTint(node.getTree())
                            : 0xFFB8C0D8;
                    color = SkillTreeChartTextures.withAlpha(tint, 150);
                    stroke = WizardsAndBeastsUiTokens.SkillTree.LEY_FRONTIER_STROKE;
                } else {
                    color = SkillTreeChartTextures.withAlpha(SkillTreeChartTextures.EDGE_LOCKED, 220);
                    stroke = WizardsAndBeastsUiTokens.SkillTree.LEY_LOCKED_STROKE;
                }
                McStylePanel.drawTexturedSegment(graphics, SkillTreeChartTextures.LEY_LINE,
                        ax, ay, bx, by, stroke,
                        SkillTreeChartTextures.LEY_LINE_W, SkillTreeChartTextures.LEY_LINE_H, color);
            }
        }
    }

    private @Nullable Skill drawNodes(GuiGraphics graphics, PlayerSkillData data, int mouseX, int mouseY) {
        Skill hovered = null;
        // One phase for the whole chart, so every takeable star breathes together rather than
        // flickering out of step with its neighbours.
        float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 900.0 * Math.PI));

        for (Skill node : webNodes) {
            boolean polaris = isPolaris(node);
            int cx = (int) Math.round(toScreenX(node.getX()));
            int cy = (int) Math.round(toScreenY(node.getY()));
            if (!onCanvas(cx, cy)) {
                continue;
            }
            int size = Math.max(6, (int) Math.round(baseSpritePx(node, polaris) * zoom));
            int level = data.getSkillLevel(node.getId());
            boolean allocated = level >= 1;
            // A sealed region is permanently non-allocatable regardless of adjacency; it falls
            // through to the locked branch (reused, not a distinct sprite — the seal cue lives in
            // the tooltip).
            boolean sealed = sealedTrees.contains(node.getTree());
            boolean allocatable = !allocated && !sealed && (node.isRoot() || hasAllocatedNeighbor(data, node));

            boolean isHovered = hovered == null && insideChart(mouseX, mouseY)
                    && withinHitRadius(node, polaris, cx, cy, mouseX, mouseY);

            if (isHovered) {
                // Halo under everything else: a hover cue that sits on top would hide the node it
                // is pointing at.
                McStylePanel.drawTintedCentered(graphics, SkillTreeChartTextures.STAR_HALO,
                        cx, cy, size * 3,
                        SkillTreeChartTextures.withAlpha(
                                allocated ? SkillTreeChartTextures.GOLD
                                        : SkillTreeChartTextures.regionTint(node.getTree()), 170));
            } else if (allocatable) {
                McStylePanel.drawTintedCentered(graphics, SkillTreeChartTextures.STAR_HALO,
                        cx, cy, (int) (size * 2.2),
                        SkillTreeChartTextures.withAlpha(
                                SkillTreeChartTextures.regionTint(node.getTree()),
                                60 + (int) (70 * pulse)));
            }

            if (polaris) {
                // Polaris: brightest object on the chart; gold once taken, ice-white before.
                int tint = allocated ? SkillTreeChartTextures.GOLD : 0xFFEFF2FF;
                McStylePanel.drawTintedCentered(graphics, SkillTreeChartTextures.STAR_POLARIS,
                        cx, cy, size, tint);
            } else if (allocated) {
                // Shape + brightness cue: diffraction flare with a hot gold core.
                McStylePanel.drawTintedCentered(graphics, SkillTreeChartTextures.flare(node.getSize()),
                        cx, cy, size, SkillTreeChartTextures.GOLD);
                McStylePanel.drawTintedCentered(graphics, SkillTreeChartTextures.core(node.getSize()),
                        cx, cy, Math.max(4, size * 2 / 3), 0xFFFFF6DC);
            } else if (allocatable) {
                // Soft white core with a region-tinted rim ring.
                McStylePanel.drawTintedCentered(graphics, SkillTreeChartTextures.core(node.getSize()),
                        cx, cy, size, 0xFFE8ECF8);
                McStylePanel.drawTintedCentered(graphics, SkillTreeChartTextures.ring(node.getSize()),
                        cx, cy, size, SkillTreeChartTextures.regionTint(node.getTree()));
            } else {
                // Locked: an unlit socket. Its own shape, so "not yet" is legible without
                // comparing two tints against a tinted sky.
                McStylePanel.drawTintedCentered(graphics, SkillTreeChartTextures.locked(node.getSize()),
                        cx, cy, size,
                        SkillTreeChartTextures.withAlpha(SkillTreeChartTextures.EMBER, 210));
            }

            if (node.getMaxLevel() > 1 && zoom >= 0.5) {
                drawStarPips(graphics, cx,
                        cy + size / 2 + WizardsAndBeastsUiTokens.SkillTree.PIP_GAP,
                        level, node.getMaxLevel());
            }

            if (isHovered) {
                hovered = node;
            }
        }
        return hovered;
    }

    private boolean withinHitRadius(Skill node, boolean polaris, int cx, int cy, int mouseX, int mouseY) {
        int r = Math.max(4, hitRadius(node, polaris) * (int) Math.ceil(zoom)) + 2;
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        return dx * dx + dy * dy <= (double) r * r;
    }

    /** Level pips as a row of tiny stars: lit up to {@code level}, hollow sockets for the rest. */
    private static void drawStarPips(GuiGraphics graphics, int cx, int y, int level, int maxLevel) {
        int pip = WizardsAndBeastsUiTokens.SkillTree.PIP_DRAW_SIZE;
        int spacing = WizardsAndBeastsUiTokens.SkillTree.PIP_SPACING;
        int startX = cx - ((maxLevel - 1) * spacing) / 2;
        for (int i = 0; i < maxLevel; i++) {
            boolean lit = i < level;
            McStylePanel.drawTintedCentered(graphics,
                    lit ? SkillTreeChartTextures.PIP_ON : SkillTreeChartTextures.PIP_OFF,
                    startX + i * spacing, y + pip / 2, pip,
                    lit ? SkillTreeChartTextures.GOLD
                            : SkillTreeChartTextures.withAlpha(SkillTreeChartTextures.NIGHT_TEXT_DIM, 170));
        }
    }

    /**
     * Constellation glyph and name over each cluster; both fade out as the view closes in to build.
     *
     * <p>The glyph is the identity the names alone could not carry — "Fornax" tells a player
     * nothing about which region they are looking at, and the asterism beside it is the same shape
     * that appears on the node's tooltip.
     */
    private void drawConstellationLabels(GuiGraphics graphics) {
        int alpha = labelAlpha();
        if (alpha < 10) {
            return;
        }
        int glyph = WizardsAndBeastsUiTokens.SkillTree.LABEL_GLYPH_SIZE;
        int gap = WizardsAndBeastsUiTokens.SkillTree.LABEL_GLYPH_GAP;
        for (Map.Entry<SkillTreeId, double[]> entry : labelCentroids.entrySet()) {
            Component text = labelText.get(entry.getKey());
            if (text == null) {
                continue;
            }
            int cx = (int) Math.round(toScreenX(entry.getValue()[0]));
            int cy = (int) Math.round(toScreenY(entry.getValue()[1]));
            if (!onCanvas(cx, cy)) {
                continue;
            }
            int tint = SkillTreeChartTextures.regionTint(entry.getKey());
            int textW = font.width(text);
            int groupX = cx - (glyph + gap + textW) / 2;
            McStylePanel.drawTintedCentered(graphics, SkillTreeChartTextures.regionGlyph(entry.getKey()),
                    groupX + glyph / 2, cy, glyph, SkillTreeChartTextures.withAlpha(tint, alpha));
            graphics.drawString(font, text, groupX + glyph + gap, cy - font.lineHeight / 2,
                    SkillTreeChartTextures.withAlpha(tint, alpha), false);
        }
    }

    /**
     * Edge darkening over the chart, inside the scissor.
     *
     * <p>Drawn last of the canvas layers so it dims the sky, the ley-lines and the outer stars
     * alike, which is what stops the web from looking pasted onto a rectangle of sky.
     */
    private void drawVignette(GuiGraphics graphics) {
        int size = SkillTreeChartTextures.VIGNETTE_SIZE;
        graphics.blit(RenderPipelines.GUI_TEXTURED, SkillTreeChartTextures.VIGNETTE,
                chartX, chartY, 0.0F, 0.0F, chartW, chartH, size, size, size, size);
    }

    /** Full strength while zoomed out; fades to nothing as the view closes past ~0.9× for building. */
    private int labelAlpha() {
        if (zoom <= 0.9) {
            return 150;
        }
        return (int) (150 * Mth.clamp(1.0 - (zoom - 0.9) / 0.35, 0.0, 1.0));
    }

    private static boolean hasAllocatedNeighbor(PlayerSkillData data, Skill node) {
        for (String neighborId : SkillTrees.clientNeighbors(node.getId())) {
            if (data.getSkillLevel(neighborId) >= 1) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
