package at.koopro.wizardsandbeasts.client.wand.gui;

import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui;
import at.koopro.wizardsandbeasts.client.gui.ui.UiSurface;
import at.koopro.wizardsandbeasts.client.gui.ui.UiText;
import at.koopro.wizardsandbeasts.client.gui.widget.CyclerWidget;
import at.koopro.wizardsandbeasts.item.wand.WandBlankItem;
import at.koopro.wizardsandbeasts.item.wand.WandCoreMaterialItem;
import at.koopro.wizardsandbeasts.network.wand.SetFlexibilityPayload;
import at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu.BLANK_X;
import static at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu.CORE_X;
import static at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu.OUTPUT_X;
import static at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu.STATION_Y;

/**
 * The wandmaker's bench: a station row (blank + core → wand), the flexibility the bench will shape for,
 * what it is waiting for, how well-equipped the workshop is, and the player's inventory.
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
    /** What the bench is waiting for: up to two lines, so a full sentence is read rather than cut. */
    private static final int STATUS_Y = 98;
    private static final int STATUS_LINES = 2;
    /** Bench tier row: caption, bar, value. */
    private static final int TIER_Y = 122;
    private static final int TIER_BAR_H = 6;

    /** Full marks on the tier scale, scaled by 100 the way the menu reports it. */
    private static final float TIER_FULL = 300.0f;

    private CyclerWidget<WandFlexibility> flexCycler;

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
        renderTier(g, x, y);

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

    /**
     * The workshop tier: a caption, the bar over its real range (0 to {@link #TIER_FULL}), the value.
     * The bar's colour climbs grey → gold → violet: semantic, not thematic — how far up the ladder this
     * bench is — so it keeps its own ramp rather than a palette ink.
     */
    private void renderTier(GuiGraphics g, int x, int y) {
        int tier = menu.getTierScoreScaled();
        float t = Math.min(1.0f, tier / TIER_FULL);
        int barColor = t < 0.5f
                ? interpolateColor(0xFF888888, 0xFFD4A020, t * 2.0f)
                : interpolateColor(0xFFD4A020, 0xFF8040C0, (t - 0.5f) * 2.0f);
        Component caption = Component.translatable("wandcraft.bench.tier_label");
        String value = String.format(Locale.ROOT, "%.2f", tier / 100.0f);
        int left = x + SIDE;
        int right = x + imageWidth - SIDE;
        UiText.draw(g, font, S, caption, left, y + TIER_Y, S.ink2());
        UiText.drawRight(g, font, S, Component.literal(value), right, y + TIER_Y, S.ink());
        Ui.Rect bar = tierBar(x, y);
        Ui.bar(g, S, bar.x(), bar.y(), bar.w(), bar.h(), t, barColor, -1.0f);
    }

    /** The bar between the caption and the value. Shared by the painting and the tooltip hit-test. */
    private Ui.Rect tierBar(int x, int y) {
        int from = x + SIDE + font.width(Component.translatable("wandcraft.bench.tier_label")) + 6;
        int to = x + imageWidth - SIDE - font.width("0.00") - 6;
        return new Ui.Rect(from, y + TIER_Y + 1, Math.max(8, to - from), TIER_BAR_H);
    }

    /** The whole tier row: caption, bar and value all explain themselves on hover. */
    private Ui.Rect tierRow(int x, int y) {
        return new Ui.Rect(x + SIDE, y + TIER_Y - 1, imageWidth - 2 * SIDE, 10);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, this.title, this.titleLabelX, this.titleLabelY, WizardsPalette.PAGE_INK, false);
        g.drawString(font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
                WizardsPalette.PAGE_INK_2, false);
    }

    /** What the bench is waiting for, or {@code null} while it has nothing to say. */
    private @Nullable Component statusMessage() {
        return switch (menu.getStatus()) {
            // With a shaped blank seated the bench knows the cheapest thing that wood can become, so
            // it can name the tier system before the player has found both ingredients.
            case WandmakersBenchMenu.STATUS_MISSING_INPUT -> menu.getRequiredTierScaled() > 0
                    ? Component.translatable("wandcraft.bench.needs_core",
                            menu.getRequiredTierScaled() / 100.0f, menu.getTierScoreScaled() / 100.0f)
                    : Component.translatable("wandcraft.bench.needs_input");
            case WandmakersBenchMenu.STATUS_BLANK_UNSHAPED -> Component.translatable("wandcraft.bench.blank_unshaped");
            case WandmakersBenchMenu.STATUS_NO_RECIPE -> Component.translatable("wandcraft.bench.no_recipe");
            case WandmakersBenchMenu.STATUS_WITHDRAWN -> Component.translatable("wandcraft.bench.status.withdrawn");
            case WandmakersBenchMenu.STATUS_BENCH_TOO_PLAIN -> Component.translatable("wandcraft.bench.tier_too_low",
                    menu.getRequiredTierScaled() / 100.0f, menu.getTierScoreScaled() / 100.0f);
            default -> null;
        };
    }

    private static int interpolateColor(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
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
        renderTierTooltip(g, mouseX, mouseY);
    }

    /**
     * Why nothing is coming out. Drawn only while the output is empty: with a wand sitting there the
     * bench has said everything it has to say, and the wand's tooltip carries the rest. Wrapped to two
     * lines, so the sentence is read rather than cut.
     */
    private void renderStatus(GuiGraphics g) {
        if (menu.getBench().getInventory().getAmountAsLong(2) > 0) {
            return;
        }
        Component reason = statusMessage();
        if (reason == null) {
            return;
        }
        UiText.drawWrapped(g, font, S, UiText.Role.LABEL, reason, leftPos + SIDE, topPos + STATUS_Y,
                imageWidth - 2 * SIDE, STATUS_LINES);
    }

    /** What the tier means, on the shared tooltip card, anywhere on the tier row. */
    private void renderTierTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (!tierRow(leftPos, topPos).contains(mouseX, mouseY)) {
            return;
        }
        List<Identifier> enhancers = menu.getEnhancerBlockIds();
        g.setTooltipForNextFrame(font, List.of(
                Component.translatable("wandcraft.gui.tier_score", menu.getTierScoreScaled() / 100.0f),
                Component.translatable("wandcraft.gui.enhancers", enhancers.size()),
                Component.translatable("wandcraft.gui.tier_hint")
        ), Optional.empty(), mouseX, mouseY);
    }
}
