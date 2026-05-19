package at.koopro.wizardsandbeasts.client.skill.gui;

import at.koopro.wizardsandbeasts.client.gui.ScreenLayoutScaler;
import at.koopro.wizardsandbeasts.client.gui.WizardsAndBeastsUiTokens;
import at.koopro.wizardsandbeasts.client.skill.state.ClientSkillDataState;
import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;
import at.koopro.wizardsandbeasts.network.skill.SkillUnlockC2SPayload;
import at.koopro.wizardsandbeasts.skill.Skill;
import at.koopro.wizardsandbeasts.skill.SkillTreeId;
import at.koopro.wizardsandbeasts.skill.SkillTrees;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SkillTreeScreen extends Screen {

    private SkillTreeId activeTree = SkillTreeId.SPELL_MASTERY;
    private final List<TabBounds> tabs = new ArrayList<>();
    private final List<Skill> visibleSkills = new ArrayList<>();
    private final List<NodeBounds> visibleNodes = new ArrayList<>();

    private int panelX;
    private int panelY;
    private int viewportX;
    private int viewportY;

    private int panX = 0;
    private int panY = 0;
    private int graphMinX = 0;
    private int graphMaxX = 0;
    private int graphMinY = 0;
    private int graphMaxY = 0;

    private NodeBounds hoveredNode;
    private NodeBounds selectedNode;
    private String resolvedTitle = "Skills";
    private ScreenLayoutScaler layout;
    private int panelW;
    private int panelH;
    private int viewportW;
    private int viewportH;

    public SkillTreeScreen() {
        super(Component.translatable("screen.wizards_and_beasts.skill_tree.title"));
    }

    @Override
    protected void init() {
        super.init();
        String titleText = Component.translatable("screen.wizards_and_beasts.skill_tree.title").getString();
        resolvedTitle = titleText.startsWith("screen.") ? "Skills" : titleText;
        layout = ScreenLayoutScaler.forScreen(width, height, WizardsAndBeastsUiTokens.SkillTree.PANEL_WIDTH, WizardsAndBeastsUiTokens.SkillTree.PANEL_HEIGHT);
        panelW = layout.panelW();
        panelH = layout.panelH();
        panelX = layout.panelX();
        panelY = layout.panelY();
        viewportW = layout.s(WizardsAndBeastsUiTokens.SkillTree.VIEWPORT_WIDTH);
        viewportH = layout.s(WizardsAndBeastsUiTokens.SkillTree.VIEWPORT_HEIGHT);
        viewportX = panelX + layout.s(WizardsAndBeastsUiTokens.SkillTree.VIEWPORT_X);
        viewportY = panelY + layout.s(WizardsAndBeastsUiTokens.SkillTree.VIEWPORT_Y);
        buildTabs();
        centerGraphInViewport();
        rebuildTree();
    }

    private void buildTabs() {
        tabs.clear();
        int x = panelX + layout.s(WizardsAndBeastsUiTokens.SkillTree.TABS_X);
        int y = panelY + layout.s(WizardsAndBeastsUiTokens.SkillTree.TABS_Y);
        for (SkillTreeId tree : SkillTreeId.values()) {
            tabs.add(new TabBounds(tree, x, y, layout.s(WizardsAndBeastsUiTokens.SkillTree.TAB_WIDTH), layout.s(WizardsAndBeastsUiTokens.SkillTree.TAB_HEIGHT)));
            x += layout.s(WizardsAndBeastsUiTokens.SkillTree.TAB_WIDTH + WizardsAndBeastsUiTokens.SkillTree.TAB_GAP);
        }
    }

    private void rebuildTree() {
        visibleSkills.clear();
        visibleNodes.clear();
        List<Skill> treeSkills = SkillTrees.getTree(activeTree);
        visibleSkills.addAll(treeSkills);
        recalculateGraphBounds(treeSkills);
        for (Skill skill : treeSkills) {
            int graphX = skill.getColumn() * layout.s(WizardsAndBeastsUiTokens.SkillTree.NODE_X_SPACING);
            int graphY = skill.getTier() * layout.s(WizardsAndBeastsUiTokens.SkillTree.NODE_Y_SPACING);
            int screenX = viewportX + panX + graphX;
            int screenY = viewportY + panY + graphY;
            int nodeSize = layout.s(WizardsAndBeastsUiTokens.SkillTree.NODE_SIZE);
            visibleNodes.add(new NodeBounds(skill, screenX, screenY, nodeSize, nodeSize, graphX, graphY));
        }
        rebuildInteractiveWidgets();
    }

    private void rebuildInteractiveWidgets() {
        clearWidgets();
        for (TabBounds tab : tabs) {
            Button tabButton = Button.builder(Component.literal(tab.tree().getDisplayName()), b -> {
                activeTree = tab.tree();
                selectedNode = null;
                centerGraphInViewport();
                rebuildTree();
            }).bounds(tab.x(), tab.y(), tab.width(), tab.height()).build();
            tabButton.setAlpha(0.0F);
            addRenderableWidget(tabButton);
        }

        for (NodeBounds node : visibleNodes) {
            if (!insideViewport(node.x() + 1, node.y() + 1)) {
                continue;
            }
            Button nodeButton = Button.builder(Component.literal(""), b -> {
                selectedNode = node;
                tryUnlockNode(node);
            }).bounds(node.x(), node.y(), node.width(), node.height()).build();
            nodeButton.setAlpha(0.0F);
            addRenderableWidget(nodeButton);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        panY += (int) (scrollY * layout.s(WizardsAndBeastsUiTokens.SkillTree.SCROLL_PAN_STEP));
        clampPanToBounds();
        rebuildTree();
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderMenuBackground(graphics);
        PlayerSkillData data = ClientSkillDataState.get();
        SkillTreeRenderHelper.renderWindowFrame(graphics, font, panelX, panelY, panelW, panelH, resolvedTitle);
        SkillTreeRenderHelper.renderTabs(graphics, font, panelX, panelY, activeTree, tabs, mouseX, mouseY);

        hoveredNode = null;
        for (NodeBounds node : visibleNodes) {
            if (node.contains(mouseX, mouseY)) {
                hoveredNode = node;
                break;
            }
        }

        SkillTreeRenderHelper.renderViewportAndGraph(
                graphics,
                viewportX,
                viewportY,
                viewportW,
                viewportH,
                visibleSkills,
                indexNodes(),
                hoveredNode,
                selectedNode,
                panX,
                panY,
                data.getUnlockedSkills(),
                data.getSkillPoints()
        );

        SkillTreeRenderHelper.renderFooter(graphics, font, panelX, panelY, panelW,
                panelH, data.getSkillPoints(), data.getTotalPointsEarned(),
                visibleSkills, data);

        super.render(graphics, mouseX, mouseY, partialTick);

        if (hoveredNode != null) {
            Skill hoveredSkill = hoveredNode.skill();
            int level = data.getSkillLevel(hoveredSkill.getId());
            SkillTreeRenderHelper.renderTooltipCard(graphics, font, hoveredSkill, hoveredNode, mouseX, mouseY, level,
                    data.getSkillPoints(), prerequisitesMet(data, hoveredSkill));
        }
    }

    private Map<String, NodeBounds> indexNodes() {
        java.util.LinkedHashMap<String, NodeBounds> index = new java.util.LinkedHashMap<>();
        for (NodeBounds node : visibleNodes) {
            index.put(node.skill().getId(), node);
        }
        return index;
    }

    private void tryUnlockNode(NodeBounds node) {
        PlayerSkillData data = ClientSkillDataState.get();
        Skill skill = node.skill();
        int level = data.getSkillLevel(skill.getId());
        if (level >= skill.getMaxLevel()) {
            return;
        }
        if (!prerequisitesMet(data, skill)) {
            return;
        }
        if (data.getSkillPoints() < skill.getPointCost()) {
            return;
        }
        ClientPacketDistributor.sendToServer(new SkillUnlockC2SPayload(skill.getId()));
    }

    private boolean prerequisitesMet(PlayerSkillData data, Skill skill) {
        for (String prereqId : skill.getPrerequisites()) {
            if (!data.hasSkill(prereqId)) {
                return false;
            }
        }
        return true;
    }

    private boolean insideViewport(double mouseX, double mouseY) {
        return mouseX >= viewportX
                && mouseX <= viewportX + viewportW
                && mouseY >= viewportY
                && mouseY <= viewportY + viewportH;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void recalculateGraphBounds(List<Skill> skills) {
        if (skills.isEmpty()) {
            graphMinX = 0;
            graphMaxX = layout.s(WizardsAndBeastsUiTokens.SkillTree.NODE_SIZE);
            graphMinY = 0;
            graphMaxY = layout.s(WizardsAndBeastsUiTokens.SkillTree.NODE_SIZE);
            return;
        }
        graphMinX = Integer.MAX_VALUE;
        graphMaxX = Integer.MIN_VALUE;
        graphMinY = Integer.MAX_VALUE;
        graphMaxY = Integer.MIN_VALUE;
        for (Skill skill : skills) {
            int graphX = skill.getColumn() * layout.s(WizardsAndBeastsUiTokens.SkillTree.NODE_X_SPACING);
            int graphY = skill.getTier() * layout.s(WizardsAndBeastsUiTokens.SkillTree.NODE_Y_SPACING);
            graphMinX = Math.min(graphMinX, graphX);
            graphMaxX = Math.max(graphMaxX, graphX + layout.s(WizardsAndBeastsUiTokens.SkillTree.NODE_SIZE));
            graphMinY = Math.min(graphMinY, graphY);
            graphMaxY = Math.max(graphMaxY, graphY + layout.s(WizardsAndBeastsUiTokens.SkillTree.NODE_SIZE));
        }
    }

    private void centerGraphInViewport() {
        List<Skill> treeSkills = SkillTrees.getTree(activeTree);
        recalculateGraphBounds(treeSkills);
        int graphCenterX = (graphMinX + graphMaxX) / 2;
        int graphCenterY = (graphMinY + graphMaxY) / 2;
        panX = (viewportW / 2) - graphCenterX;
        panY = (viewportH / 2) - graphCenterY;
        clampPanToBounds();
    }

    private void clampPanToBounds() {
        int contentWidth = Math.max(layout.s(WizardsAndBeastsUiTokens.SkillTree.NODE_SIZE), graphMaxX - graphMinX);
        int contentHeight = Math.max(layout.s(WizardsAndBeastsUiTokens.SkillTree.NODE_SIZE), graphMaxY - graphMinY);

        int minX = WizardsAndBeastsUiTokens.SkillTree.PAN_MIN_X;
        int maxX = WizardsAndBeastsUiTokens.SkillTree.PAN_MAX_X;
        int minY = WizardsAndBeastsUiTokens.SkillTree.PAN_MIN_Y;
        int maxY = WizardsAndBeastsUiTokens.SkillTree.PAN_MAX_Y;

        if (contentWidth > viewportW) {
            minX = Math.max(minX, viewportW - graphMaxX);
            maxX = Math.min(maxX, -graphMinX);
        }
        if (contentHeight > viewportH) {
            minY = Math.max(minY, viewportH - graphMaxY);
            maxY = Math.min(maxY, -graphMinY);
        }

        if (minX > maxX) {
            int centeredX = (minX + maxX) / 2;
            minX = centeredX;
            maxX = centeredX;
        }
        if (minY > maxY) {
            int centeredY = (minY + maxY) / 2;
            minY = centeredY;
            maxY = centeredY;
        }

        panX = clamp(panX, minX, maxX);
        panY = clamp(panY, minY, maxY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public record TabBounds(SkillTreeId tree, int x, int y, int width, int height) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    public record NodeBounds(Skill skill, int x, int y, int width, int height, int graphX, int graphY) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }

        public int centerX() {
            return x + (width / 2);
        }

        public int centerY() {
            return y + (height / 2);
        }
    }
}
