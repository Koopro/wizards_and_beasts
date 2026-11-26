package at.koopro.wizardsandbeasts.client.wand.gui;

import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui;
import at.koopro.wizardsandbeasts.client.gui.ui.UiSurface;
import at.koopro.wizardsandbeasts.client.gui.ui.UiText;
import at.koopro.wizardsandbeasts.client.gui.widget.CyclerWidget;
import at.koopro.wizardsandbeasts.item.wand.WandBlankItem;
import at.koopro.wizardsandbeasts.item.wand.WandCoreMaterialItem;
import at.koopro.wizardsandbeasts.client.gui.util.GuiText;
import at.koopro.wizardsandbeasts.network.wand.BenchActPayload;
import at.koopro.wizardsandbeasts.network.wand.SetFlexibilityPayload;
import at.koopro.wizardsandbeasts.wand.bench.BenchMaking;
import at.koopro.wizardsandbeasts.wand.making.BenchCraft;
import at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
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
 * The wandmaker's bench: a station row (blank + core → wand), the flexibility the bench will shape for,
 * the next act with its odds and the button that begins it, the tools on the bench, and the player's inventory.
 * The act itself is played in the world ({@code BenchBeatClient}); P5 remakes this screen as Make / Mend / Restyle.
 *
 * <p>Drawn on the shared parchment, procedurally, from {@link WandmakersBenchMenu}'s own geometry
 * ({@code GUI_W}, {@code GUI_H}, the slot origins): the frames are placed by the same numbers as the
 * slots, so no artwork can sit off one. It used to be a 176x196 generated sheet whose wells had to be
 * kept in step with the menu by hand, with an unlabelled tier bar under the hotbar and a status line cut
 * to one line ("Place a shaped blank and…").
 *
 * <p>The flexibility picker shows one of Ollivander's five words and asks the server for a change; it
 * never shows a value the server has not confirmed.
 */
public class WandmakersBenchScreen extends AbstractContainerScreen<WandmakersBenchMenu> {

    private static final UiSurface S = UiSurface.PAGE;

    /** Under the title, and between the station and the inventory. */
    private static final int TITLE_Y = 10;
    private static final int TITLE_RULE_Y = 19;
    private static final int INVENTORY_RULE_Y = 130;
    /** The finished wand's frame is this much wider than a slot on each side. */
    private static final int OUTPUT_PAD = 4;
    /** The small captions under the station slots. */
    private static final int SLOT_LABEL_Y = STATION_Y + 16 + OUTPUT_PAD + 4;

    private static final int SIDE = 14;
    private static final int FLEX_LABEL_Y = 66;
    private static final int FLEX_ROW_Y = 76;
    private static final int FLEX_ROW_H = 18;
    /** What the bench is waiting for, or the next act's odds: one fitted line. */
    private static final int STATUS_Y = 97;
    /** The act button (left) and the tool wells (right). */
    private static final int ACT_Y = 108;
    private static final int ACT_W = 60;
    private static final int ACT_H = 18;

    private CyclerWidget<WandFlexibility> flexCycler;
    private @Nullable Button actButton;

    public WandmakersBenchScreen(WandmakersBenchMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = WandmakersBenchMenu.GUI_W;
        this.imageHeight = WandmakersBenchMenu.GUI_H;
        this.titleLabelX = WandmakersBenchMenu.INV_X;
        // Clear of the sheet's frame rules (4 and 6px in), which vanilla's y 6 sat on.
        this.titleLabelY = TITLE_Y;
        this.inventoryLabelX = WandmakersBenchMenu.INV_X;
        this.inventoryLabelY = WandmakersBenchMenu.INV_Y - 10;
    }

    @Override
    protected void init() {
        super.init();
        rebuildCycler();
    }

    /**
     * (Re)builds the flexibility cycler at the menu's current value.
     *
     * <p>Rebuilt rather than mutated because {@link CyclerWidget} holds its own index: the menu is
     * the authority here and it can change under us — another player at the same bench, or the
     * server correcting a value — so the cycler is re-seeded from {@code menu} each time rather
     * than trusted to still agree with it.
     */
    private void rebuildCycler() {
        clearWidgets();
        List<WandFlexibility> values = Arrays.asList(WandFlexibility.values());
        WandFlexibility current = values.get(Math.floorMod(menu.getFlexibilityOrdinal(), values.size()));
        flexCycler = new CyclerWidget<>(values, current,
                flexibility -> flexibility.label().getString(), this::chooseFlexibility);
        flexCycler.setBounds(leftPos + SIDE, topPos + FLEX_ROW_Y, imageWidth - 2 * SIDE, FLEX_ROW_H);
        flexCycler.buttons().forEach(this::addRenderableWidget);
        actButton = Button.builder(actLabel(), b -> ClientPacketDistributor.sendToServer(new BenchActPayload(menu.containerId)))
                .bounds(leftPos + SIDE, topPos + ACT_Y, ACT_W, ACT_H).build();
        addRenderableWidget(actButton);
    }

    private Component actLabel() {
        return Component.translatable(menu.getNextAct().key() + ".short");
    }

    /**
     * Tells the server which flexibility the bench should shape for. The cycler is <em>not</em> moved
     * optimistically: the value shown is the menu's synced value, re-seeded in {@link #containerTick}.
     */
    private void chooseFlexibility(WandFlexibility flexibility) {
        ClientPacketDistributor.sendToServer(new SetFlexibilityPayload(menu.containerId, flexibility.ordinal()));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // Re-seed when the server's value moves, including when it rejects what we asked for.
        if (flexCycler != null && flexCycler.selected().ordinal() != menu.getFlexibilityOrdinal()) {
            rebuildCycler();
        }
        if (actButton != null) {
            actButton.active = menu.getStatus() == BenchMaking.Status.READY;
            actButton.setMessage(actLabel());
        }
    }

    // ── painting ─────────────────────────────────────────────────────────────

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        Ui.panel(g, S, x, y, imageWidth, imageHeight);
        Ui.divider(g, S, x + SIDE - 4, y + TITLE_RULE_Y, imageWidth - 2 * (SIDE - 4));
        Ui.divider(g, S, x + SIDE - 4, y + INVENTORY_RULE_Y, imageWidth - 2 * (SIDE - 4));

        renderStation(g, x, y);
        BenchToolsRow.wells(g, x + imageWidth - SIDE, y + ACT_Y, menu.getToolMask());

        // Inventory wells, at the menu's own slot origins (3.. are the player's).
        List<int[]> origins = WandmakersBenchMenu.slotOrigins();
        for (int i = 3; i < origins.size(); i++) {
            Ui.slot(g, x + origins.get(i)[0], y + origins.get(i)[1]);
        }
    }

    /**
     * Blank + core → wand. The station slots are captioned (they were three blank squares), the wand's
     * frame is larger and ringed, and while the player carries a blank or a core the slot that takes it
     * is outlined.
     */
    private void renderStation(GuiGraphics g, int x, int y) {
        Ui.slot(g, x + BLANK_X, y + STATION_Y);
        Ui.slot(g, x + CORE_X, y + STATION_Y);
        Ui.resultSlot(g, x + OUTPUT_X, y + STATION_Y, OUTPUT_PAD);

        ItemStack carried = menu.getCarried();
        if (carried.getItem() instanceof WandBlankItem) {
            Ui.slotTarget(g, x + BLANK_X, y + STATION_Y, 0, WizardsPalette.GILT_DARK);
        } else if (WandCoreMaterialItem.isBenchCore(carried)) {
            Ui.slotTarget(g, x + CORE_X, y + STATION_Y, 0, WizardsPalette.GILT_DARK);
        }

        int midY = y + STATION_Y + 8;
        // "+" between the two inputs.
        int plusX = x + (BLANK_X + 17 + CORE_X - 1) / 2;
        g.fill(plusX - 3, midY, plusX + 4, midY + 1, S.ink3());
        g.fill(plusX, midY - 3, plusX + 1, midY + 4, S.ink3());

        // The arrow: ink while the bench waits, gilt once a wand sits in the output.
        boolean made = menu.getBench().getInventory().getAmountAsLong(2) > 0;
        int arrow = made ? WizardsPalette.GILT_DARK : S.ink2();
        int shaftFrom = x + CORE_X + 17 + 6;
        int tipX = x + OUTPUT_X - 1 - OUTPUT_PAD - 2 - 6 - Ui.ARROW;
        g.fill(shaftFrom, midY - 1, tipX + 2, midY + 2, arrow);
        Ui.arrow(g, 1, tipX, midY - 3, arrow);

        caption(g, "wandcraft.bench.slot.blank", x + BLANK_X + 8, y);
        caption(g, "wandcraft.bench.slot.core", x + CORE_X + 8, y);
        caption(g, "wandcraft.bench.slot.wand", x + OUTPUT_X + 8, y);
    }

    private void caption(GuiGraphics g, String key, int cx, int top) {
        UiText.drawCentered(g, font, S, Component.translatable(key), cx, top + SLOT_LABEL_Y, S.ink2());
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, this.title, this.titleLabelX, this.titleLabelY, WizardsPalette.PAGE_INK, false);
        if (menu.getStage() != BenchCraft.Stage.NONE) {
            Component stage = Component.translatable(menu.getStage().key());
            g.drawString(font, stage, imageWidth - SIDE - font.width(stage), this.titleLabelY, WizardsPalette.GILT_DARK, false);
        }
        g.drawString(font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
                WizardsPalette.PAGE_INK_2, false);
    }

    /** What the bench is waiting for, or the next act's odds when it can begin. */
    private Component statusMessage() {
        BenchMaking.Status status = menu.getStatus();
        if (status != BenchMaking.Status.READY || menu.getRiskPermille() < 0) {
            return Component.translatable(status.key());
        }
        BenchCraft.Act act = menu.getNextAct();
        String risk = String.format(Locale.ROOT, "%.0f", menu.getRiskPermille() / 10.0f);
        Component odds = act.risk() == BenchCraft.Risk.NONE
                ? Component.translatable("wandcraft.bench.odds.safe", menu.getWindow())
                : Component.translatable("wandcraft.bench.odds." + act.risk().name().toLowerCase(Locale.ROOT),
                        risk, menu.getWindow());
        return menu.getForgive() > 0
                ? Component.translatable("wandcraft.bench.odds.forgive", odds, menu.getForgive())
                : odds;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        UiText.draw(g, font, S, Component.translatable("wandcraft.bench.flexibility"),
                leftPos + SIDE, topPos + FLEX_LABEL_Y, S.ink2());
        // The cycler draws only its label plate; its arrows are registered widgets.
        flexCycler.renderLabel(g);
        renderStatus(g);
        renderTooltip(g, mouseX, mouseY);
        BenchToolsRow.tooltip(g, font, leftPos + imageWidth - SIDE, topPos + ACT_Y, menu.getToolMask(), mouseX, mouseY);
    }

    /** One fitted line: what the bench waits for, or the odds of the next act. */
    private void renderStatus(GuiGraphics g) {
        GuiText.drawFitted(g, font, statusMessage().getString(), leftPos + SIDE, topPos + STATUS_Y,
                imageWidth - 2 * SIDE, S.ink2());
    }
}
