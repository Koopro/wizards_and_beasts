package at.koopro.wizardsandbeasts.client.wand.gui;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette.GuiSkin;
import at.koopro.wizardsandbeasts.client.gui.util.GuiText;
import at.koopro.wizardsandbeasts.client.gui.widget.CyclerWidget;
import at.koopro.wizardsandbeasts.wand.refine.SetRefineActionPayload;
import at.koopro.wizardsandbeasts.wand.refine.WandRefineMenu;
import at.koopro.wizardsandbeasts.wand.refine.WandRefinement.Action;
import at.koopro.wizardsandbeasts.wand.refine.WandRefinement.Status;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The bench's second role: refining a finished wand. The same bench and the same paper as making one -- the wand
 * where the blank goes, the material where the core goes, the action on the row where flexibility is chosen, the
 * refined wand in the output slot -- so the two roles read as one craft. The server decides everything
 * ({@link WandRefineMenu}); this shows the action and why nothing is coming out.
 */
public class WandRefineScreen extends AbstractContainerScreen<WandRefineMenu> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            WizardsAndBeastsMod.MODID, "textures/gui/wandmakers_bench.png");
    private static final GuiSkin SKIN = GuiSkin.WORKBENCH;
    private static final String KEY = "gui.wizards_and_beasts.wand_refine.";

    // Shared with the bench's art (gui_chrome.a_wandmakers_bench); see WandmakersBenchScreen for why.
    private static final int ROW_Y = 66;
    private static final int ROW_H = 18;
    private static final int INSET_X = 16;
    private static final int STATUS_Y = 90;
    private static final int BAR_H = 6;
    private static final int BAR_BOTTOM_MARGIN = 9;
    private static final float TIER_FULL = 300.0f;

    private CyclerWidget<Action> actionCycler;

    public WandRefineScreen(WandRefineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 196;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        rebuildCycler();
    }

    private void rebuildCycler() {
        clearWidgets();
        List<Action> values = Arrays.asList(Action.values());
        actionCycler = new CyclerWidget<>(values, menu.action(),
                action -> Component.translatable(action.key()).getString(),
                action -> ClientPacketDistributor.sendToServer(new SetRefineActionPayload(menu.containerId, action.ordinal())))
                .skin(SKIN);
        actionCycler.setBounds(leftPos + INSET_X, topPos + ROW_Y, imageWidth - 2 * INSET_X, ROW_H);
        actionCycler.buttons().forEach(this::addRenderableWidget);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // The server holds the action; re-seed when it moves, including when it refuses what was asked.
        if (actionCycler != null && actionCycler.selected() != menu.action()) {
            rebuildCycler();
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        McStylePanel.drawTexture(graphics, TEXTURE, leftPos, topPos, imageWidth, imageHeight, imageWidth, imageHeight);
        float t = Math.min(1.0f, menu.tierScaled() / TIER_FULL);
        int bx = leftPos + INSET_X;
        int by = topPos + imageHeight - BAR_BOTTOM_MARGIN;
        int bw = imageWidth - 2 * INSET_X;
        graphics.fill(bx + 1, by + 1, bx + 1 + (int) ((bw - 2) * t), by + BAR_H - 1, 0xFFD4A020);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, SKIN.ink(), false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, SKIN.muted(), false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        graphics.drawString(font, Component.translatable(KEY + "action"), leftPos + INSET_X,
                topPos + ROW_Y - WizardsMetrics.LINE_TIGHT, SKIN.muted(), false);
        actionCycler.renderLabel(graphics);
        Component status = statusMessage();
        if (status != null) {
            GuiText.drawFitted(graphics, font, status.getString(), leftPos + INSET_X, topPos + STATUS_Y,
                    imageWidth - 2 * INSET_X, SKIN.muted());
        }
    }

    /** Why nothing is in the output slot -- or, when something is, what taking it will cost. */
    private @Nullable Component statusMessage() {
        Status status = menu.status();
        Action action = menu.action();
        Component material = Component.translatable(KEY + "material." + action.name().toLowerCase(Locale.ROOT));
        return switch (status) {
            case READY -> Component.translatable(status.key(), material);
            case TOO_PLAIN -> Component.translatable(status.key(), menu.requiredTierScaled() / 100.0f,
                    menu.tierScaled() / 100.0f);
            case NO_MATERIAL, WRONG_MATERIAL -> Component.translatable(status.key(), material);
            default -> Component.translatable(status.key());
        };
    }
}
