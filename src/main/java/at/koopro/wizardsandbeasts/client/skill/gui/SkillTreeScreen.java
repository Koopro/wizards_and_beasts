package at.koopro.wizardsandbeasts.client.skill.gui;

import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import at.koopro.wizardsandbeasts.client.gui.WizardsAndBeastsUiTokens;
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
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Star-chart canvas over the player's audience skill web (Phase 5 skin): night-sky starfield with
 * parallax, nodes as tinted star sprites (size = magnitude, state = brightness + shape), allocated
 * paths as gold ley-lines with a slow shimmer, constellation labels fading out as you zoom in.
 * Drag to pan, scroll to zoom toward the cursor, click an allocatable star to send the
 * {@link SkillUnlockC2SPayload} roundtrip (state applies on the server's sync response).
 */
public class SkillTreeScreen extends Screen {

    private static final double MIN_ZOOM = 0.25;
    private static final double MAX_ZOOM = 2.0;
    private static final double PARALLAX = 0.3;
    private static final int CULL_PAD = 48;
    /** Survey rings (world units) centered on Polaris — the wizard notable band boundaries. */
    private static final int[] SURVEY_RINGS = {120, 200, 280};
    private static final int SURVEY_RING_COLOR = 0x1E9FB8E8;

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

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int viewportX;
    private int viewportY;
    private int viewportW;
    private int viewportH;
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
        panelH = layout.panelH();
        panelX = layout.panelX();
        panelY = layout.panelY();
        viewportW = layout.s(WizardsAndBeastsUiTokens.SkillTree.VIEWPORT_WIDTH);
        viewportH = layout.s(WizardsAndBeastsUiTokens.SkillTree.VIEWPORT_HEIGHT);
        viewportX = panelX + layout.s(WizardsAndBeastsUiTokens.SkillTree.VIEWPORT_X);
        viewportY = panelY + layout.s(WizardsAndBeastsUiTokens.SkillTree.VIEWPORT_Y);

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
    }

    /**
     * The only in-game way into {@link VocationSelectionScreen}. Vocations were command-only, so on a world
     * without cheats the specialization layer could not be reached at all.
     */
    private void addVocationButton() {
        int buttonW = layout.s(96);
        int buttonH = layout.s(16);
        Component label = at.koopro.wizardsandbeasts.client.skill.state.ClientVocationCache.primary()
                .map(at.koopro.wizardsandbeasts.skill.vocation.VocationRegistry::get)
                .map(vocation -> Component.translatable("screen.wizards_and_beasts.vocation.button.set",
                        vocation.displayName()))
                .orElse(Component.translatable("screen.wizards_and_beasts.vocation.button.none"));
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(label,
                        b -> {
                            if (minecraft != null) {
                                minecraft.setScreen(new VocationSelectionScreen(this));
                            }
                        })
                .bounds(panelX + panelW - layout.s(8) - buttonW, panelY + layout.s(4), buttonW, buttonH)
                .build());
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

    // ── World ↔ screen transform: screen = (world − pan) × zoom + viewportCenter ──

    private double toScreenX(double worldX) {
        return (worldX - panX) * zoom + viewportX + viewportW / 2.0;
    }

    private double toScreenY(double worldY) {
        return (worldY - panY) * zoom + viewportY + viewportH / 2.0;
    }

    private double toWorldX(double screenX) {
        return (screenX - viewportX - viewportW / 2.0) / zoom + panX;
    }

    private double toWorldY(double screenY) {
        return (screenY - viewportY - viewportH / 2.0) / zoom + panY;
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
        if (event.button() == 0 && insideViewport(event.x(), event.y())) {
            panX -= dragX / zoom;
            panY -= dragY / zoom;
            panned = true;
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!insideViewport(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        double worldX = toWorldX(mouseX);
        double worldY = toWorldY(mouseY);
        zoom = Math.clamp(zoom * (scrollY > 0 ? 1.15 : 1.0 / 1.15), MIN_ZOOM, MAX_ZOOM);
        panX = worldX - (mouseX - viewportX - viewportW / 2.0) / zoom;
        panY = worldY - (mouseY - viewportY - viewportH / 2.0) / zoom;
        return true;
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

    private boolean insideViewport(double mouseX, double mouseY) {
        return mouseX >= viewportX && mouseX <= viewportX + viewportW
                && mouseY >= viewportY && mouseY <= viewportY + viewportH;
    }

    private boolean onCanvas(double screenX, double screenY) {
        return screenX >= viewportX - CULL_PAD && screenX <= viewportX + viewportW + CULL_PAD
                && screenY >= viewportY - CULL_PAD && screenY <= viewportY + viewportH + CULL_PAD;
    }

    // ── Render ──

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderMenuBackground(graphics);
        refreshWeb(); // hot-swap: a /reload definition resync replaces the cached list instance
        PlayerSkillData data = ClientSkillDataState.get();
        SkillTreeRenderHelper.renderWindowFrame(graphics, font, panelX, panelY, panelW, panelH, resolvedTitle);

        graphics.enableScissor(viewportX, viewportY, viewportX + viewportW, viewportY + viewportH);
        drawStarfield(graphics);
        drawSurveyRings(graphics);
        drawEdges(graphics, data);
        hoveredNode = drawNodes(graphics, data, mouseX, mouseY);
        drawConstellationLabels(graphics);
        graphics.disableScissor();

        SkillTreeRenderHelper.drawBorderRect(graphics, viewportX, viewportY, viewportW, viewportH,
                WizardsAndBeastsUiTokens.SkillTree.BORDER_COLOR);
        SkillTreeRenderHelper.renderFooter(graphics, font, panelX, panelY, panelW, panelH, data);

        super.render(graphics, mouseX, mouseY, partialTick);

        if (hoveredNode != null) {
            boolean adjacencyOpen = hoveredNode.isRoot() || hasAllocatedNeighbor(data, hoveredNode);
            boolean sealed = sealedTrees.contains(hoveredNode.getTree());
            SkillTreeRenderHelper.renderTooltipCard(graphics, font, hoveredNode, mouseX, mouseY,
                    data.getSkillLevel(hoveredNode.getId()), data.getSkillPoints(), adjacencyOpen, sealed);
        }
    }

    /** Deep-sky tile scrolled at ~0.3× pan for parallax; drawn 1:1 so the grain never swims with zoom. */
    private void drawStarfield(GuiGraphics graphics) {
        int tile = SkillTreeChartTextures.STARFIELD_SIZE;
        int offX = Math.floorMod((int) Math.round(panX * PARALLAX * zoom), tile);
        int offY = Math.floorMod((int) Math.round(panY * PARALLAX * zoom), tile);
        for (int x = viewportX - offX; x < viewportX + viewportW; x += tile) {
            for (int y = viewportY - offY; y < viewportY + viewportH; y += tile) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, SkillTreeChartTextures.STARFIELD,
                        x, y, 0.0F, 0.0F, tile, tile, tile, tile);
            }
        }
    }

    /** Faint concentric survey rings centered on Polaris — drawn, not textured. */
    private void drawSurveyRings(GuiGraphics graphics) {
        int cx = (int) Math.round(toScreenX(0));
        int cy = (int) Math.round(toScreenY(0));
        for (int worldR : SURVEY_RINGS) {
            SkillTreeRenderHelper.drawCircleOutline(graphics, cx, cy,
                    (int) Math.round(worldR * zoom), SURVEY_RING_COLOR);
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
                    continue; // cull: both endpoints off-viewport
                }
                boolean aLit = data.getSkillLevel(node.getId()) >= 1;
                boolean bLit = data.getSkillLevel(neighborId) >= 1;
                int color;
                int stroke;
                if (aLit && bLit) {
                    color = SkillTreeChartTextures.withAlpha(SkillTreeChartTextures.GOLD, allocatedAlpha);
                    stroke = 2;
                } else if (aLit || bLit) {
                    // Frontier edge: slightly lifted, carrying the region tint when intra-region.
                    int tint = node.getTree() == neighbor.getTree()
                            ? SkillTreeChartTextures.regionTint(node.getTree())
                            : 0xFFB8C0D8;
                    color = SkillTreeChartTextures.withAlpha(tint, 120);
                    stroke = 1;
                } else {
                    color = SkillTreeChartTextures.EDGE_LOCKED;
                    stroke = 1;
                }
                SkillTreeRenderHelper.drawLine(graphics,
                        (int) Math.round(ax), (int) Math.round(ay),
                        (int) Math.round(bx), (int) Math.round(by), color, stroke);
            }
        }
    }

    private @Nullable Skill drawNodes(GuiGraphics graphics, PlayerSkillData data, int mouseX, int mouseY) {
        Skill hovered = null;
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
            // A sealed region is permanently non-allocatable regardless of adjacency; it falls through to
            // the locked ember branch (reused, not a distinct sprite — the seal cue lives in the tooltip).
            boolean sealed = sealedTrees.contains(node.getTree());
            boolean allocatable = !allocated && !sealed && (node.isRoot() || hasAllocatedNeighbor(data, node));

            if (polaris) {
                // Polaris: brightest object on the chart; gold once taken, ice-white before.
                int tint = allocated ? SkillTreeChartTextures.GOLD : 0xFFEFF2FF;
                blitCentered(graphics, SkillTreeChartTextures.STAR_POLARIS, cx, cy, size, tint);
            } else if (allocated) {
                // Shape + brightness cue: diffraction flare with a hot gold core.
                blitCentered(graphics, SkillTreeChartTextures.flare(node.getSize()), cx, cy, size,
                        SkillTreeChartTextures.GOLD);
                blitCentered(graphics, SkillTreeChartTextures.core(node.getSize()), cx, cy,
                        Math.max(4, size * 2 / 3), 0xFFFFF6DC);
            } else if (allocatable) {
                // Soft white core with a region-tinted rim ring.
                blitCentered(graphics, SkillTreeChartTextures.core(node.getSize()), cx, cy, size, 0xFFE8ECF8);
                blitCentered(graphics, SkillTreeChartTextures.ring(node.getSize()), cx, cy, size,
                        SkillTreeChartTextures.regionTint(node.getTree()));
            } else {
                // Locked: dim ember dot — brightness cue lives in the tint.
                blitCentered(graphics, SkillTreeChartTextures.core(node.getSize()), cx, cy, size,
                        SkillTreeChartTextures.withAlpha(SkillTreeChartTextures.EMBER, 195));
            }

            if (node.getMaxLevel() > 1 && zoom >= 0.5) {
                drawStarPips(graphics, cx, cy + size / 2 + 3, level, node.getMaxLevel());
            }

            if (hovered == null && insideViewport(mouseX, mouseY)) {
                int r = Math.max(4, hitRadius(node, polaris) * (int) Math.ceil(zoom)) + 2;
                double dx = mouseX - cx;
                double dy = mouseY - cy;
                if (dx * dx + dy * dy <= (double) r * r) {
                    hovered = node;
                }
            }
        }
        return hovered;
    }

    /** Level pips as a row of tiny stars: gold-filled up to {@code level}, slate for the rest. */
    private static void drawStarPips(GuiGraphics graphics, int cx, int y, int level, int maxLevel) {
        int spacing = 5;
        int startX = cx - ((maxLevel - 1) * spacing) / 2;
        for (int i = 0; i < maxLevel; i++) {
            int x = startX + i * spacing;
            int color = i < level ? SkillTreeChartTextures.GOLD : 0xFF3A4258;
            graphics.fill(x - 1, y, x + 2, y + 1, color);
            graphics.fill(x, y - 1, x + 1, y + 2, color);
        }
    }

    /** Constellation names hang faintly over their clusters; they fade out as you zoom in to build. */
    private void drawConstellationLabels(GuiGraphics graphics) {
        int alpha = labelAlpha();
        if (alpha < 10) {
            return;
        }
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
            graphics.drawCenteredString(font, text, cx, cy - font.lineHeight / 2,
                    SkillTreeChartTextures.withAlpha(tint, alpha));
        }
    }

    /** Full strength while zoomed out; fades to nothing as the view closes past ~0.9× for building. */
    private int labelAlpha() {
        if (zoom <= 0.9) {
            return 150;
        }
        return (int) (150 * Mth.clamp(1.0 - (zoom - 0.9) / 0.35, 0.0, 1.0));
    }

    private static void blitCentered(GuiGraphics graphics, net.minecraft.resources.Identifier sprite,
                                     int cx, int cy, int size, int tint) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, sprite,
                cx - size / 2, cy - size / 2, 0.0F, 0.0F, size, size, size, size, tint);
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
