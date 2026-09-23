package at.koopro.wizardsandbeasts.client.skill.gui;

import at.koopro.wizardsandbeasts.client.gui.WizardsPalette.GuiSkin;
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
 * Star-chart canvas over the player's audience skill web, drawn as a page of an engraved celestial
 * atlas: pale vellum with a dotted graticule, nodes as engraved stars (size and ray count =
 * magnitude, state = shape), connections as ink lines -- dotted where uncharted, dashed on the
 * frontier, solid over silver where taken -- hand-coloured region washes, and constellation glyphs
 * and names fading out as you zoom in to build. Drag to pan, scroll to zoom toward the cursor,
 * click an allocatable star to send the {@link SkillUnlockC2SPayload} roundtrip (state applies on
 * the server's sync response).
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
     * How far the page layers travel against the pan: all the way.
     *
     * <p>The night sky had three depths moving at 0.10, 0.18 and 0.32 of the pan. The atlas page
     * has none -- its vellum, stipple and background stars are printed on the same sheet as the
     * web, and a graticule that slid under the stars would read as two sheets, not a sky.
     */
    private static final double PAGE_TRAVEL = 1.0;

    /** Survey rings (world units) centered on Polaris — the wizard notable band boundaries. */
    private static final int[] SURVEY_RINGS = {120, 200, 280};
    private static final int SURVEY_RING_COLOR =
            SkillTreeChartTextures.withAlpha(SkillTreeChartTextures.CHART_INK_2, 0x70);
    /** Region wash strength: hand-colouring laid thin enough that the engraving reads through it. */
    private static final int REGION_WASH_ALPHA = 26;
    /** Margin (world units) a region's wash reaches past its outermost node. */
    private static final int REGION_WASH_MARGIN = 24;

    /** Dash patterns (screen px, dash then gap) for untaken lines: dotted, and the dashed frontier. */
    private static final int DOT_ON = 1;
    private static final int DOT_OFF = 2;
    private static final int DASH_ON = 4;
    private static final int DASH_OFF = 3;

    private SkillTreeId.@Nullable Audience audience;
    /** Player heritage/variant, synced truth — drives audience selection and which regions are sealed. */
    private @Nullable Heritage heritage;
    private @Nullable HeritageVariant variant;
    /** Regions the player's capability tags seal shut: rendered permanently-locked, non-interactive. */
    private final Set<SkillTreeId> sealedTrees = EnumSet.noneOf(SkillTreeId.class);
    private List<Skill> webNodes = List.of();
    /** Constellation label cache ({x, y, radius} in world units): recomputed only when the node list instance changes. */
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
        // on the chart. It sits on the header rule, clear of the frame's own ink rules.
        addRenderableWidget(chartButton(
                panelX + panelW - layout.s(WizardsAndBeastsUiTokens.SkillTree.SEAL_INSET
                        + McStylePanel.SEAL_SIZE + 4) - buttonW,
                SkillTreeRenderHelper.headerRowY(layout, buttonH), buttonW, buttonH, label, null,
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
        return ThemedButton.skinned(x, y, w, h, label, action, GuiSkin.STAR_CHART,
                icon, SkillTreeChartTextures.ICON_SIZE);
    }

    /** Seals every region in this audience whose capability requirement the player doesn't meet. */
    private void recomputeSealedRegions() {
        sealedTrees.clear();
        for (SkillTreeId tree : SkillTreeId.values()) {
            if (tree.getAudience() == audience
                    && !SkillTreeId.meetsRequirement(tree.getRequirement(), ClientHeritageDataState.get())) {
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
                labelCentroids.put(tree, new double[]{sum[0] / sum[2], sum[1] / sum[2], 0.0});
                labelText.put(tree, Component.translatable("skilltree.region." + tree.getId() + ".constellation")
                        .withStyle(ChatFormatting.ITALIC));
            }
        });
        // The region's reach from its centroid, which sizes its wash.
        for (Skill node : webNodes) {
            double[] centre = labelCentroids.get(node.getTree());
            if (centre != null) {
                centre[2] = Math.max(centre[2], Math.hypot(node.getX() - centre[0], node.getY() - centre[1]));
            }
        }
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
        McStylePanel.drawSkinInset(graphics, GuiSkin.STAR_CHART,
                viewportX, viewportY, viewportW, viewportH);

        graphics.enableScissor(chartX, chartY, chartX + chartW, chartY + chartH);
        drawPage(graphics);
        drawRegionWashes(graphics);
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
                    data.getSkillLevel(hoveredNode.getId()), data.getSkillPoints(), adjacencyOpen, sealed,
                    prerequisiteNames(hoveredNode), unallocatedNeighbourNames(hoveredNode, data));
        }
    }

    /** The three page layers, back to front: vellum and graticule, stipple, background stars. */
    private void drawPage(GuiGraphics graphics) {
        tileLayer(graphics, SkillTreeChartTextures.STARFIELD_FAR,
                SkillTreeChartTextures.STARFIELD_SIZE, PAGE_TRAVEL);
        tileLayer(graphics, SkillTreeChartTextures.NEBULA,
                SkillTreeChartTextures.NEBULA_SIZE, PAGE_TRAVEL);
        tileLayer(graphics, SkillTreeChartTextures.STARFIELD,
                SkillTreeChartTextures.STARFIELD_SIZE, PAGE_TRAVEL);
    }

    /**
     * One page layer, tiled across the chart at 1:1.
     *
     * <p>Drawn at native size rather than scaled with the chart, so the grain and the engraving
     * stay pixel-sharp at every zoom instead of smearing into blocks.
     */
    private void tileLayer(GuiGraphics graphics, Identifier tex, int tile, double travel) {
        int offX = Math.floorMod((int) Math.round(panX * travel * zoom), tile);
        int offY = Math.floorMod((int) Math.round(panY * travel * zoom), tile);
        for (int x = chartX - offX; x < chartX + chartW; x += tile) {
            for (int y = chartY - offY; y < chartY + chartH; y += tile) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0.0F, 0.0F,
                        tile, tile, tile, tile);
            }
        }
    }

    /**
     * A thin wash of each region's ink over its part of the web, as the printed atlases were
     * hand-coloured after the press. Under everything but the page, so the engraving reads
     * through it; the region is recognisable before a single label is read.
     */
    private void drawRegionWashes(GuiGraphics graphics) {
        for (Map.Entry<SkillTreeId, double[]> entry : labelCentroids.entrySet()) {
            double[] region = entry.getValue();
            int cx = (int) Math.round(toScreenX(region[0]));
            int cy = (int) Math.round(toScreenY(region[1]));
            int size = (int) Math.round((region[2] + REGION_WASH_MARGIN) * 2 * zoom);
            if (size < 8) {
                continue;
            }
            McStylePanel.drawTintedCentered(graphics, SkillTreeChartTextures.STAR_HALO, cx, cy, size,
                    SkillTreeChartTextures.withAlpha(SkillTreeChartTextures.regionTint(entry.getKey()),
                            REGION_WASH_ALPHA));
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

    /**
     * Constellation lines in the engraver's three hands: dotted faint ink between uncharted stars,
     * dashed region ink on the frontier, and a solid ink line laid over silver leaf once both ends
     * are taken. Pattern and weight carry the state, so it survives a colour-blind reader.
     */
    private void drawEdges(GuiGraphics graphics, PlayerSkillData data) {
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
                if (aLit && bLit) {
                    drawLine(graphics, ax, ay, bx, by, WizardsAndBeastsUiTokens.SkillTree.LEY_ALLOCATED_STROKE,
                            SkillTreeChartTextures.SILVER);
                    drawLine(graphics, ax, ay, bx, by, WizardsAndBeastsUiTokens.SkillTree.LEY_LOCKED_STROKE,
                            SkillTreeChartTextures.CHART_INK);
                } else if (aLit || bLit) {
                    // Frontier: dashed, in the region's ink when the line stays inside one region.
                    int ink = node.getTree() == neighbor.getTree()
                            ? SkillTreeChartTextures.regionTint(node.getTree())
                            : SkillTreeChartTextures.CHART_INK_2;
                    drawDashed(graphics, ax, ay, bx, by, DASH_ON, DASH_OFF, ink);
                } else {
                    drawDashed(graphics, ax, ay, bx, by, DOT_ON, DOT_OFF,
                            SkillTreeChartTextures.withAlpha(SkillTreeChartTextures.CHART_INK_FAINT, 220));
                }
            }
        }
    }

    private static void drawLine(GuiGraphics graphics, double ax, double ay, double bx, double by,
                                 int stroke, int color) {
        McStylePanel.drawTexturedSegment(graphics, SkillTreeChartTextures.LEY_LINE, ax, ay, bx, by, stroke,
                SkillTreeChartTextures.LEY_LINE_W, SkillTreeChartTextures.LEY_LINE_H, color);
    }

    /**
     * A one-pixel line broken into dashes, one strip blit per dash.
     *
     * <p>Laid here rather than baked into the strip: the strip is stretched to each edge's length,
     * so a pattern in the texture would stretch with it and every edge would have different
     * dashes. Screen-space lengths keep the pattern identical on every line at every zoom.
     */
    private static void drawDashed(GuiGraphics graphics, double ax, double ay, double bx, double by,
                                   int on, int off, int color) {
        double dx = bx - ax;
        double dy = by - ay;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length < 1.0) {
            return;
        }
        double ux = dx / length;
        double uy = dy / length;
        for (double t = 0; t < length; t += on + off) {
            // Nudged past `on`: a one-pixel dot along a unit vector can measure 0.9999…, and
            // drawTexturedSegment skips anything under a pixel, which would erase every dot.
            double end = Math.min(length, t + on + 0.01);
            McStylePanel.drawTexturedSegment(graphics, SkillTreeChartTextures.LEY_LINE,
                    ax + ux * t, ay + uy * t, ax + ux * end, ay + uy * end,
                    WizardsAndBeastsUiTokens.SkillTree.LEY_LOCKED_STROKE,
                    SkillTreeChartTextures.LEY_LINE_W, SkillTreeChartTextures.LEY_LINE_H, color);
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
            boolean open = !sealed && (allocated || node.isRoot() || hasAllocatedNeighbor(data, node));
            boolean maxed = level >= node.getMaxLevel();
            // Three questions, not one. "Can I put a point here" and "can I pay for it" were the
            // same boolean before, so the chart pulsed an invitation at a player with no points and
            // said nothing until they hovered.
            boolean allocatable = open && !maxed;
            boolean affordable = allocatable && data.getSkillPoints() >= node.getPointCost();

            boolean isHovered = hovered == null && insideChart(mouseX, mouseY)
                    && withinHitRadius(node, polaris, cx, cy, mouseX, mouseY);

            int regionInk = SkillTreeChartTextures.regionTint(node.getTree());
            if (isHovered) {
                // Wash under everything else: a hover cue that sits on top would hide the node it
                // is pointing at. Tarnished silver over a taken star, the region's ink otherwise.
                McStylePanel.drawTintedCentered(graphics, SkillTreeChartTextures.STAR_HALO,
                        cx, cy, size * 3,
                        SkillTreeChartTextures.withAlpha(
                                allocated ? SkillTreeChartTextures.SILVER_DARK : regionInk, 110));
            } else if (affordable) {
                // Only a star the player can actually buy right now breathes. The pulse is an
                // invitation, and an invitation you cannot accept is worse than none.
                McStylePanel.drawTintedCentered(graphics, SkillTreeChartTextures.STAR_HALO,
                        cx, cy, (int) (size * 2.2),
                        SkillTreeChartTextures.withAlpha(regionInk, 40 + (int) (50 * pulse)));
            } else if (allocatable) {
                // Reachable but unaffordable: a still, thin wash. Legible as "open" without
                // claiming to be takeable.
                McStylePanel.drawTintedCentered(graphics, SkillTreeChartTextures.STAR_HALO,
                        cx, cy, (int) (size * 1.7),
                        SkillTreeChartTextures.withAlpha(regionInk, 35));
            }

            Skill.Size sprite = node.getSize();
            if (polaris) {
                // Polaris: the compass rose, the largest object on the chart. On a silver roundel
                // once taken; before that, thin ink over a vellum knockout.
                McStylePanel.drawTintedCentered(graphics, SkillTreeChartTextures.core(Skill.Size.KEYSTONE),
                        cx, cy, size * 4 / 5,
                        allocated ? SkillTreeChartTextures.SILVER : SkillTreeChartTextures.VELLUM);
                McStylePanel.drawTintedCentered(graphics, SkillTreeChartTextures.STAR_POLARIS,
                        cx, cy, size,
                        allocated ? SkillTreeChartTextures.CHART_INK : SkillTreeChartTextures.CHART_INK_2);
            } else if (allocated) {
                // Taken: the engraved star, solid ink with its magnitude's rays, on silver leaf.
                //
                // A maxed node gets a second, wider star behind the first in thin ink. One-of-three
                // and three-of-three drew identically before, so the chart could not answer "is
                // there anything left in this node" without a hover — on a 163-node web that is the
                // single question a player asks most.
                if (maxed && node.getMaxLevel() > 1) {
                    McStylePanel.drawTintedCentered(graphics, SkillTreeChartTextures.flare(sprite),
                            cx, cy, (int) (size * 1.45),
                            SkillTreeChartTextures.withAlpha(SkillTreeChartTextures.CHART_INK_2, 110));
                }
                McStylePanel.drawTintedCentered(graphics, SkillTreeChartTextures.core(sprite),
                        cx, cy, size, SkillTreeChartTextures.SILVER);
                McStylePanel.drawTintedCentered(graphics, SkillTreeChartTextures.flare(sprite),
                        cx, cy, size, SkillTreeChartTextures.CHART_INK);
            } else {
                // Untaken: a vellum knockout first, so the lines stop short of the star as they do
                // on an engraved plate, then an open circle -- the region's ink when it can be
                // taken, a faint outline when it cannot. Their own shapes, so "not yet" is
                // legible without comparing two tints.
                McStylePanel.drawTintedCentered(graphics, SkillTreeChartTextures.core(sprite),
                        cx, cy, size, SkillTreeChartTextures.VELLUM);
                if (allocatable) {
                    McStylePanel.drawTintedCentered(graphics, SkillTreeChartTextures.ring(sprite),
                            cx, cy, size, regionInk);
                } else {
                    McStylePanel.drawTintedCentered(graphics, SkillTreeChartTextures.locked(sprite),
                            cx, cy, size,
                            SkillTreeChartTextures.withAlpha(SkillTreeChartTextures.CHART_INK_FAINT, 230));
                }
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

    /** Level pips as a row of tiny inked stars up to {@code level}, faint open circles for the rest. */
    private static void drawStarPips(GuiGraphics graphics, int cx, int y, int level, int maxLevel) {
        int pip = WizardsAndBeastsUiTokens.SkillTree.PIP_DRAW_SIZE;
        int spacing = WizardsAndBeastsUiTokens.SkillTree.PIP_SPACING;
        int startX = cx - ((maxLevel - 1) * spacing) / 2;
        for (int i = 0; i < maxLevel; i++) {
            boolean lit = i < level;
            McStylePanel.drawTintedCentered(graphics,
                    lit ? SkillTreeChartTextures.PIP_ON : SkillTreeChartTextures.PIP_OFF,
                    startX + i * spacing, y + pip / 2, pip,
                    lit ? SkillTreeChartTextures.CHART_INK
                            : SkillTreeChartTextures.withAlpha(SkillTreeChartTextures.CHART_INK_FAINT, 220));
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
            // Shadowless, in the region's ink darkened to AA on the vellum: a label fades by alpha
            // as the view closes in, never by being drawn in a weaker colour.
            graphics.drawString(font, text, groupX + glyph + gap, cy - font.lineHeight / 2,
                    SkillTreeChartTextures.withAlpha(SkillTreeChartTextures.regionTextInk(entry.getKey()), alpha),
                    false);
        }
    }

    /**
     * Faint toning at the plate's edge, inside the scissor.
     *
     * <p>Drawn last of the canvas layers so it ages the vellum, the lines and the outer stars
     * alike, which is what stops the web from looking pasted onto a rectangle of paper.
     */
    private void drawVignette(GuiGraphics graphics) {
        int size = SkillTreeChartTextures.VIGNETTE_SIZE;
        graphics.blit(RenderPipelines.GUI_TEXTURED, SkillTreeChartTextures.VIGNETTE,
                chartX, chartY, 0.0F, 0.0F, chartW, chartH, size, size, size, size);
    }

    /**
     * Full strength while zoomed out; fades to nothing as the view closes past ~0.9× for building.
     * Full strength is near-opaque: ink on paper at half alpha is grey, and grey is not legible.
     */
    private int labelAlpha() {
        if (zoom <= 0.9) {
            return LABEL_ALPHA;
        }
        return (int) (LABEL_ALPHA * Mth.clamp(1.0 - (zoom - 0.9) / 0.35, 0.0, 1.0));
    }

    private static final int LABEL_ALPHA = 235;

    /**
     * The neighbours that would open this node, by display name.
     *
     * <p>Allocation needs any one edge-neighbour at level 1 or more, so the answer is a list and the
     * tooltip says "any of". Empty for a root node, which needs nothing.
     */
    private List<Component> prerequisiteNames(Skill node) {
        if (node.isRoot()) {
            return List.of();
        }
        List<Component> names = new java.util.ArrayList<>();
        for (String neighborId : SkillTrees.clientNeighbors(node.getId())) {
            Skill neighbor = SkillTrees.clientById(neighborId);
            if (neighbor != null) {
                names.add(Component.literal(
                        SkillTreeRenderHelper.resolveDisplayName(neighbor.getDisplayName())));
            }
        }
        return names;
    }

    /**
     * What taking this node would open next: its neighbours the player has not taken yet.
     *
     * <p>The same edge list the prerequisites come from, read the other way round. A web has no direction, so
     * "leads to" and "requires" are the same neighbours seen from either side of the point you are standing on
     * — which is exactly why the card only prints this for a node the player has not allocated.
     */
    private List<Component> unallocatedNeighbourNames(Skill node, PlayerSkillData data) {
        List<Component> names = new java.util.ArrayList<>();
        for (String neighborId : SkillTrees.clientNeighbors(node.getId())) {
            if (data.getSkillLevel(neighborId) >= 1) {
                continue;
            }
            Skill neighbor = SkillTrees.clientById(neighborId);
            if (neighbor != null) {
                names.add(Component.literal(
                        SkillTreeRenderHelper.resolveDisplayName(neighbor.getDisplayName())));
            }
        }
        return names;
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
