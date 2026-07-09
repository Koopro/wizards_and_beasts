package at.koopro.wizardsandbeasts.client.skill.gui;

import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import at.koopro.wizardsandbeasts.client.gui.WizardsAndBeastsUiTokens;
import at.koopro.wizardsandbeasts.client.heritage.state.ClientHeritageDataState;
import at.koopro.wizardsandbeasts.client.skill.state.ClientSkillDataState;
import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;
import at.koopro.wizardsandbeasts.network.skill.SkillUnlockC2SPayload;
import at.koopro.wizardsandbeasts.skill.Skill;
import at.koopro.wizardsandbeasts.skill.SkillTreeId;
import at.koopro.wizardsandbeasts.skill.SkillTrees;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Pan/zoom canvas over the player's audience skill web (Phase 2 infrastructure — plain shapes;
 * the star-chart visual treatment is Phase 3). Nodes render at datapack {@code x}/{@code y}
 * coordinates; drag to pan, scroll to zoom toward the cursor, click an allocatable node to send
 * the {@link SkillUnlockC2SPayload} roundtrip (state applies on the server's sync response).
 */
public class SkillTreeScreen extends Screen {

    private static final double MIN_ZOOM = 0.25;
    private static final double MAX_ZOOM = 2.0;

    // Node visual states (plain shapes until Phase 3).
    private static final int COLOR_ALLOCATED = 0xFFE8C24A;
    private static final int COLOR_ALLOCATED_RIM = 0xFFFFF0B0;
    private static final int COLOR_ALLOCATABLE = 0xFF6FA8DC;
    private static final int COLOR_ALLOCATABLE_RIM = 0xFFB8D8F8;
    private static final int COLOR_LOCKED = 0xFF4A4A55;
    private static final int COLOR_LOCKED_RIM = 0xFF6A6A78;
    private static final int COLOR_EDGE_DIM = 0xFF3A3A48;
    private static final int COLOR_EDGE_LIT = 0xFFD8C070;
    private static final int COLOR_ROOT_CORE = 0xFF2A2A36;

    private SkillTreeId.@Nullable Audience audience;
    private List<Skill> webNodes = List.of();

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
        audience = SkillTreeId.audienceForHeritage(ClientHeritageDataState.get().getSelectedHeritage());
        webNodes = SkillTrees.clientWebNodes(audience);
        if (previous == null) {
            centerOnWeb();
        }
    }

    /** Start centered on the web's bounding box (the wizard web centers on its root at 0,0). */
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

    private static int nodeRadius(Skill node) {
        return switch (node.getSize()) {
            case SMALL -> 4;
            case NOTABLE -> 6;
            case KEYSTONE -> 9;
        };
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
        // Zoom toward the cursor: the world point under the mouse stays under the mouse.
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

    // ── Render ──

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderMenuBackground(graphics);
        // Re-fetch each frame so a /reload definition resync hot-swaps the open canvas.
        if (audience != null) {
            webNodes = SkillTrees.clientWebNodes(audience);
        }
        PlayerSkillData data = ClientSkillDataState.get();
        SkillTreeRenderHelper.renderWindowFrame(graphics, font, panelX, panelY, panelW, panelH, resolvedTitle);

        graphics.enableScissor(viewportX, viewportY, viewportX + viewportW, viewportY + viewportH);
        graphics.fill(viewportX, viewportY, viewportX + viewportW, viewportY + viewportH,
                WizardsAndBeastsUiTokens.SkillTree.VIEWPORT_BG);

        // Edges below nodes; an edge is lit when both endpoints are at level ≥ 1.
        for (Skill node : webNodes) {
            int fromX = (int) Math.round(toScreenX(node.getX()));
            int fromY = (int) Math.round(toScreenY(node.getY()));
            for (String neighborId : SkillTrees.clientNeighbors(node.getId())) {
                if (neighborId.compareTo(node.getId()) <= 0) {
                    continue; // draw each symmetric edge once
                }
                Skill neighbor = SkillTrees.clientById(neighborId);
                if (neighbor == null) {
                    continue;
                }
                boolean lit = data.getSkillLevel(node.getId()) >= 1 && data.getSkillLevel(neighborId) >= 1;
                SkillTreeRenderHelper.drawLine(graphics, fromX, fromY,
                        (int) Math.round(toScreenX(neighbor.getX())), (int) Math.round(toScreenY(neighbor.getY())),
                        lit ? COLOR_EDGE_LIT : COLOR_EDGE_DIM, lit ? 2 : 1);
            }
        }

        hoveredNode = null;
        for (Skill node : webNodes) {
            int cx = (int) Math.round(toScreenX(node.getX()));
            int cy = (int) Math.round(toScreenY(node.getY()));
            int r = Math.max(2, (int) Math.round(nodeRadius(node) * zoom));

            int level = data.getSkillLevel(node.getId());
            boolean allocated = level >= 1;
            boolean allocatable = !allocated && (node.isRoot() || hasAllocatedNeighbor(data, node));

            int fill = allocated ? COLOR_ALLOCATED : allocatable ? COLOR_ALLOCATABLE : COLOR_LOCKED;
            int rim = allocated ? COLOR_ALLOCATED_RIM : allocatable ? COLOR_ALLOCATABLE_RIM : COLOR_LOCKED_RIM;
            SkillTreeRenderHelper.drawCircle(graphics, cx, cy, r, rim);
            SkillTreeRenderHelper.drawCircle(graphics, cx, cy, Math.max(1, r - 1), fill);
            if (node.isRoot()) {
                SkillTreeRenderHelper.drawCircle(graphics, cx, cy, Math.max(1, r / 3), COLOR_ROOT_CORE);
            }

            // Level pips on multi-level nodes.
            if (node.getMaxLevel() > 1 && zoom >= 0.5) {
                String pips = level + "/" + node.getMaxLevel();
                graphics.drawCenteredString(font, pips, cx, cy + r + 2,
                        allocated ? COLOR_ALLOCATED : 0xFFAAAAAA);
            }

            if (hoveredNode == null && insideViewport(mouseX, mouseY)) {
                double dx = mouseX - cx;
                double dy = mouseY - cy;
                if (dx * dx + dy * dy <= (double) (r + 2) * (r + 2)) {
                    hoveredNode = node;
                }
            }
        }

        graphics.disableScissor();
        SkillTreeRenderHelper.drawBorderRect(graphics, viewportX, viewportY, viewportW, viewportH,
                WizardsAndBeastsUiTokens.SkillTree.BORDER_COLOR);

        SkillTreeRenderHelper.renderFooter(graphics, font, panelX, panelY, panelW, panelH, data);

        super.render(graphics, mouseX, mouseY, partialTick);

        if (hoveredNode != null) {
            boolean adjacencyOpen = hoveredNode.isRoot() || hasAllocatedNeighbor(data, hoveredNode);
            SkillTreeRenderHelper.renderTooltipCard(graphics, font, hoveredNode, mouseX, mouseY,
                    data.getSkillLevel(hoveredNode.getId()), data.getSkillPoints(), adjacencyOpen);
        }
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
