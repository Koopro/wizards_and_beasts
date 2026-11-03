package at.koopro.wizardsandbeasts.client.wand.gui;

import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette.GuiSkin;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui;
import at.koopro.wizardsandbeasts.client.gui.ui.UiSurface;
import at.koopro.wizardsandbeasts.client.gui.ui.UiText;
import at.koopro.wizardsandbeasts.client.gui.util.GuiText;
import at.koopro.wizardsandbeasts.client.gui.widget.CyclerWidget;
import at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu;
import at.koopro.wizardsandbeasts.wand.refine.SetRefineActionPayload;
import at.koopro.wizardsandbeasts.wand.refine.WandRefineMenu;
import at.koopro.wizardsandbeasts.wand.refine.WandRefinement.Action;
import at.koopro.wizardsandbeasts.wand.refine.WandRefinement.Status;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu.BLANK_X;
import static at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu.CORE_X;
import static at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu.OUTPUT_X;
import static at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu.STATION_Y;

/**
 * The bench's second role: refining a finished wand. The same bench and the same paper as making one -- the wand
 * where the blank goes, the material where the core goes, the action on the row where flexibility is chosen, the
 * refined wand in the output slot -- so the two roles read as one craft. The server decides everything
 * ({@link WandRefineMenu}); this shows the action and why nothing is coming out.
 *
 * <p>Drawn procedurally on {@link WandmakersBenchMenu}'s geometry, as {@link WandmakersBenchScreen} is. It used to
 * bind the bench's old generated sheet, which was retired with the bench's redraw and left this screen drawing the
 * missing-texture checkerboard.
 */
public class WandRefineScreen extends AbstractContainerScreen<WandRefineMenu> {

    private static final UiSurface S = UiSurface.PAGE;
    private static final GuiSkin SKIN = GuiSkin.WORKBENCH;
    private static final String KEY = "gui.wizards_and_beasts.wand_refine.";

    // The bench screen's rows, so switching role does not move anything on the sheet.
    private static final int TITLE_Y = 10;
    private static final int TITLE_RULE_Y = 19;
    private static final int INVENTORY_RULE_Y = 130;
    private static final int OUTPUT_PAD = 4;
    private static final int SLOT_LABEL_Y = STATION_Y + 16 + OUTPUT_PAD + 4;
    private static final int SIDE = 14;
    private static final int ACTION_LABEL_Y = 66;
    private static final int ACTION_ROW_Y = 76;
    private static final int ACTION_ROW_H = 18;
    private static final int STATUS_Y = 98;
    private static final int TIER_Y = 122;
    private static final int TIER_BAR_H = 6;
    private static final float TIER_FULL = 300.0f;

    private CyclerWidget<Action> actionCycler;

    public WandRefineScreen(WandRefineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = WandmakersBenchMenu.GUI_W;
        this.imageHeight = WandmakersBenchMenu.GUI_H;
        this.titleLabelX = WandmakersBenchMenu.INV_X;
        this.titleLabelY = TITLE_Y;
        this.inventoryLabelX = WandmakersBenchMenu.INV_X;
        this.inventoryLabelY = WandmakersBenchMenu.INV_Y - 10;
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
        actionCycler.setBounds(leftPos + SIDE, topPos + ACTION_ROW_Y, imageWidth - 2 * SIDE, ACTION_ROW_H);
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
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        Ui.panel(g, S, x, y, imageWidth, imageHeight);
        Ui.divider(g, S, x + SIDE - 4, y + TITLE_RULE_Y, imageWidth - 2 * (SIDE - 4));
        Ui.divider(g, S, x + SIDE - 4, y + INVENTORY_RULE_Y, imageWidth - 2 * (SIDE - 4));

        renderStation(g, x, y);
        renderTier(g, x, y);

        List<int[]> origins = WandmakersBenchMenu.slotOrigins();
        for (int i = 3; i < origins.size(); i++) {
            Ui.slot(g, x + origins.get(i)[0], y + origins.get(i)[1]);
        }
    }

    /** Wand + material → refined wand, as the bench's blank + core → wand. */
    private void renderStation(GuiGraphics g, int x, int y) {
        Ui.slot(g, x + BLANK_X, y + STATION_Y);
        Ui.slot(g, x + CORE_X, y + STATION_Y);
        Ui.resultSlot(g, x + OUTPUT_X, y + STATION_Y, OUTPUT_PAD);

        if (WandRefineMenu.isFinishedWand(menu.getCarried())) {
            Ui.slotTarget(g, x + BLANK_X, y + STATION_Y, 0, WizardsPalette.GILT_DARK);
        }

        int midY = y + STATION_Y + 8;
        int plusX = x + (BLANK_X + 17 + CORE_X - 1) / 2;
        g.fill(plusX - 3, midY, plusX + 4, midY + 1, S.ink3());
        g.fill(plusX, midY - 3, plusX + 1, midY + 4, S.ink3());

        int arrow = menu.status() == Status.READY ? WizardsPalette.GILT_DARK : S.ink2();
        int shaftFrom = x + CORE_X + 17 + 6;
        int tipX = x + OUTPUT_X - 1 - OUTPUT_PAD - 2 - 6 - Ui.ARROW;
        g.fill(shaftFrom, midY - 1, tipX + 2, midY + 2, arrow);
        Ui.arrow(g, 1, tipX, midY - 3, arrow);

        caption(g, KEY + "slot.wand", x + BLANK_X + 8, y);
        caption(g, KEY + "slot.material", x + CORE_X + 8, y);
        caption(g, KEY + "slot.refined", x + OUTPUT_X + 8, y);
    }

    private void caption(GuiGraphics g, String key, int cx, int top) {
        UiText.drawCentered(g, font, S, Component.translatable(key), cx, top + SLOT_LABEL_Y, S.ink2());
    }

    /** The bench's tier row, read the same way as on the making side. */
    private void renderTier(GuiGraphics g, int x, int y) {
        float t = Math.min(1.0f, menu.tierScaled() / TIER_FULL);
        Component caption = Component.translatable("wandcraft.bench.tier_label");
        String value = String.format(Locale.ROOT, "%.2f", menu.tierScaled() / 100.0f);
        int left = x + SIDE;
        int right = x + imageWidth - SIDE;
        UiText.draw(g, font, S, caption, left, y + TIER_Y, S.ink2());
        UiText.drawRight(g, font, S, Component.literal(value), right, y + TIER_Y, S.ink());
        int from = left + font.width(caption) + 6;
        int to = right - font.width("0.00") - 6;
        Ui.bar(g, S, from, y + TIER_Y + 1, Math.max(8, to - from), TIER_BAR_H, t, WizardsPalette.GILT_DARK, -1.0f);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, WizardsPalette.PAGE_INK, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, WizardsPalette.PAGE_INK_2, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        UiText.draw(g, font, S, Component.translatable(KEY + "action"), leftPos + SIDE, topPos + ACTION_LABEL_Y, S.ink2());
        actionCycler.renderLabel(g);
        Component status = statusMessage();
        if (status != null) {
            GuiText.drawFitted(g, font, status.getString(), leftPos + SIDE, topPos + STATUS_Y,
                    imageWidth - 2 * SIDE, S.ink2());
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
