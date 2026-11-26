package at.koopro.wizardsandbeasts.client.wand.gui;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui;
import at.koopro.wizardsandbeasts.client.gui.ui.UiSurface;
import at.koopro.wizardsandbeasts.client.gui.util.GuiText;
import at.koopro.wizardsandbeasts.client.gui.widget.CyclerWidget;
import at.koopro.wizardsandbeasts.item.wand.WandBlankItem;
import at.koopro.wizardsandbeasts.item.wand.WandCoreMaterialItem;
import at.koopro.wizardsandbeasts.network.wand.BenchActPayload;
import at.koopro.wizardsandbeasts.network.wand.SetFlexibilityPayload;
import at.koopro.wizardsandbeasts.wand.bench.BenchMaking;
import at.koopro.wizardsandbeasts.wand.bench.BenchRole;
import at.koopro.wizardsandbeasts.wand.bench.BenchTool;
import at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu;
import at.koopro.wizardsandbeasts.wand.making.BenchCraft;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu.BLANK_X;
import static at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu.BLANK_Y;
import static at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu.CORE_X;
import static at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu.CORE_Y;
import static at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu.TOP_H;

/**
 * The wandmaker's bench, seen from above: an artefact screen, as Hermione's bag is a bag (owner, 2026-11-12). The
 * blank lies between the lathe's centres, the core in a glass dish, the finished wand in an open Ollivanders wand box
 * (PS ch.5, "narrow boxes"). The tools sit in a leather roll, the stages are marked on a boxwood rule, the act's
 * odds and the flexibility are written on a pinned slip, and the act is begun by pressing the red wax seal.
 *
 * <p>Art: {@code tools/bench_screen.py} ({@code container/wandmakers_bench/bench_top}); every well in it is placed at
 * {@link WandmakersBenchMenu}'s numbers, which the slots use too. The player's inventory is a parchment strip under
 * the bench's front edge.
 *
 * <p>Owner 2026-11-26: "i like it very much". The Mend and Restyle tags open those roles on the same bench
 * ({@link BenchChrome}).
 */
public class WandmakersBenchScreen extends AbstractContainerScreen<WandmakersBenchMenu> {

    private static final UiSurface PAGE = UiSurface.PAGE;

    // Mirrored from tools/bench_screen.py.
    private static final int PLATE_X = 6, PLATE_Y = 4;
    private static final int TOOL_X0 = 11, TOOL_STEP = 21, TOOL_Y = 25;
    private static final int RULE_X0 = 6, RULE_Y0 = 82, RULE_X1 = 226, RULE_Y1 = 94;
    private static final int SLIP_X0 = 6, SLIP_Y0 = 100, SLIP_X1 = 152;
    private static final int SEAL_X = 194, SEAL_Y = 120, SEAL_R = 19;

    /** The rule's five marks: the four stages a making passes, then sealed. */
    private static final String[] RULE_KEYS = {"seated", "turned", "bored", "married", "sealed"};

    private @Nullable CyclerWidget<WandFlexibility> flexCycler;
    private @Nullable SealButton seal;

    public WandmakersBenchScreen(WandmakersBenchMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = WandmakersBenchMenu.GUI_W;
        this.imageHeight = WandmakersBenchMenu.GUI_H;
        this.titleLabelX = PLATE_X + 5;
        this.titleLabelY = PLATE_Y + 3;
        this.inventoryLabelX = WandmakersBenchMenu.INV_X;
        this.inventoryLabelY = WandmakersBenchMenu.INV_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        rebuildWidgets();
    }

    /**
     * (Re)builds the flexibility cycler at the menu's value, and the seal. Rebuilt rather than mutated: the menu is the
     * authority and can change under us (another player, the server correcting a value).
     */
    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        List<WandFlexibility> values = Arrays.asList(WandFlexibility.values());
        WandFlexibility current = values.get(Math.floorMod(menu.getFlexibilityOrdinal(), values.size()));
        flexCycler = new CyclerWidget<>(values, current, f -> f.label().getString(),
                f -> ClientPacketDistributor.sendToServer(new SetFlexibilityPayload(menu.containerId, f.ordinal())));
        flexCycler.setBounds(leftPos + SLIP_X0 + 62, topPos + SLIP_Y0 + 26, SLIP_X1 - SLIP_X0 - 68, 13);
        flexCycler.buttons().forEach(this::addRenderableWidget);
        seal = new SealButton(leftPos + SEAL_X - SEAL_R, topPos + SEAL_Y - SEAL_R, 2 * SEAL_R,
                () -> ClientPacketDistributor.sendToServer(new BenchActPayload(menu.containerId)));
        addRenderableWidget(seal);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (flexCycler != null && flexCycler.selected().ordinal() != menu.getFlexibilityOrdinal()) {
            rebuildWidgets();
        }
        if (seal != null) {
            seal.active = menu.getStatus() == BenchMaking.Status.READY;
        }
    }

    // ── painting ─────────────────────────────────────────────────────────────

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        List<int[]> origins = WandmakersBenchMenu.slotOrigins();
        BenchChrome.back(g, x, y, imageWidth, TOP_H, imageHeight, BenchChrome.TOP_MAKE, BenchRole.MAKE,
                origins.subList(3, origins.size()));
        renderTools(g, x, y);
        renderRule(g, x, y);
        renderCarriedTarget(g, x, y);
    }

    /** Each tool that lies on the bench sits in its pocket of the roll. */
    private void renderTools(GuiGraphics g, int x, int y) {
        int mask = menu.getToolMask();
        for (BenchTool tool : BenchTool.values()) {
            if ((mask & tool.bit()) != 0) {
                g.renderItem(BenchToolsRow.stack(tool), x + TOOL_X0 + tool.ordinal() * TOOL_STEP, y + TOOL_Y);
            }
        }
    }

    /**
     * The boxwood rule: one mark per stage. A stage the making has passed is burnt dark; the one the next act reaches
     * is ringed in brass.
     */
    private void renderRule(GuiGraphics g, int x, int y) {
        int seg = (RULE_X1 - RULE_X0) / RULE_KEYS.length;
        int reached = menu.getStage().ordinal();           // NONE 0, SEATED 1 ... MARRIED 4
        int next = menu.getStage() == BenchCraft.Stage.NONE ? -1 : menu.getNextAct().ordinal() + 1;
        for (int i = 0; i < RULE_KEYS.length; i++) {
            int sx = x + RULE_X0 + i * seg;
            boolean burnt = i < reached;
            if (burnt) {
                g.fill(sx + 2, y + RULE_Y0 + 3, sx + seg - 1, y + RULE_Y1 - 1, 0xB04A2E16);
            }
            if (i == next) {
                outline(g, sx + 1, y + RULE_Y0 + 1, seg - 1, RULE_Y1 - RULE_Y0 - 1, WizardsPalette.GILT);
            }
            Component label = Component.translatable("wandcraft.bench.stage." + RULE_KEYS[i]);
            int lx = sx + (seg - font.width(label)) / 2 + 1;
            g.drawString(font, label, lx, y + RULE_Y0 + 3, burnt ? WizardsPalette.PAGE_LIGHT : WizardsPalette.PAGE_INK_2, false);
        }
    }

    /** While a blank or a core is carried, its place on the bench is ringed. */
    private void renderCarriedTarget(GuiGraphics g, int x, int y) {
        ItemStack carried = menu.getCarried();
        if (carried.getItem() instanceof WandBlankItem) {
            Ui.slotTarget(g, x + BLANK_X, y + BLANK_Y, 0, WizardsPalette.GILT_LIGHT);
        } else if (WandCoreMaterialItem.isBenchCore(carried)) {
            Ui.slotTarget(g, x + CORE_X, y + CORE_Y, 0, WizardsPalette.GILT_LIGHT);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Engraved on the brass plate, and the inventory caption on the strip.
        g.drawString(font, title, titleLabelX, titleLabelY, WizardsPalette.PAGE_INK, false);
        BenchChrome.tagLabels(g, font, imageWidth, BenchRole.MAKE);
        renderSlip(g);
        renderSealLabel(g);
    }

    /** The pinned slip: the act (or why there is none), its odds, and the flexibility row. */
    private void renderSlip(GuiGraphics g) {
        int tx = SLIP_X0 + 12;
        int w = SLIP_X1 - tx - 4;
        BenchMaking.Status status = menu.getStatus();
        if (status == BenchMaking.Status.READY && menu.getRiskPermille() >= 0) {
            BenchCraft.Act act = menu.getNextAct();
            GuiText.drawFitted(g, font, Component.translatable(act.key()).getString(), tx, SLIP_Y0 + 4, w, WizardsPalette.PAGE_INK);
            GuiText.drawFitted(g, font, odds(act).getString(), SLIP_X0 + 5, SLIP_Y0 + 15, SLIP_X1 - SLIP_X0 - 9,
                    act.risk() == BenchCraft.Risk.NONE ? WizardsPalette.PAGE_INK_2 : WizardsPalette.PAGE_RUBRIC);
        } else {
            // What the bench waits for gets both lines: a sentence is read, not cut.
            at.koopro.wizardsandbeasts.client.gui.ui.UiText.drawWrapped(g, font, PAGE,
                    at.koopro.wizardsandbeasts.client.gui.ui.UiText.Role.LABEL,
                    Component.translatable(status.key()), tx, SLIP_Y0 + 4, w, 2);
        }
        g.drawString(font, Component.translatable("wandcraft.bench.flexibility"), SLIP_X0 + 5, SLIP_Y0 + 29,
                WizardsPalette.PAGE_INK_2, false);
    }

    private Component odds(BenchCraft.Act act) {
        String risk = String.format(Locale.ROOT, "%.0f", menu.getRiskPermille() / 10.0f);
        // The window is felt at the bench, not read: the slip says what can be lost and what is forgiven.
        Component odds = act.risk() == BenchCraft.Risk.NONE
                ? Component.translatable("wandcraft.bench.odds.safe")
                : Component.translatable("wandcraft.bench.odds." + act.risk().name().toLowerCase(Locale.ROOT), risk);
        return menu.getForgive() > 0 ? Component.translatable("wandcraft.bench.odds.forgive", odds, menu.getForgive()) : odds;
    }

    /** The act's name pressed into the wax: bright while it can begin, sunk while the bench waits. */
    private void renderSealLabel(GuiGraphics g) {
        Component label = Component.translatable(menu.getNextAct().key() + ".short");
        boolean live = seal != null && seal.active;
        boolean hot = live && seal.isHoveredOrFocused();
        int colour = !live ? 0xFF5A1414 : hot ? WizardsPalette.GILT_LIGHT : WizardsPalette.TEXT;
        g.drawString(font, label, SEAL_X - font.width(label) / 2 + 1, SEAL_Y - 4, colour, live);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (flexCycler != null) {
            flexCycler.renderLabel(g);
        }
        renderTooltip(g, mouseX, mouseY);
        renderToolTooltip(g, mouseX, mouseY);
    }

    private void renderToolTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int mask = menu.getToolMask();
        for (BenchTool tool : BenchTool.values()) {
            int tx = leftPos + TOOL_X0 + tool.ordinal() * TOOL_STEP;
            int ty = topPos + TOOL_Y;
            if (mouseX >= tx - 1 && mouseX < tx + 17 && mouseY >= ty - 1 && mouseY < ty + 17) {
                boolean here = (mask & tool.bit()) != 0;
                g.setTooltipForNextFrame(font, List.of(
                        BenchToolsRow.stack(tool).getHoverName(),
                        Component.translatable("item.wizards_and_beasts.bench_tool.stage." + tool.getSerializedName()),
                        Component.translatable(here ? "wandcraft.bench.tool_here" : "wandcraft.bench.tool_missing")
                ), java.util.Optional.empty(), mouseX, mouseY);
                return;
            }
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && BenchChrome.clickTag(event.x(), event.y(), leftPos, topPos, imageWidth,
                BenchRole.MAKE, menu.containerId)) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int colour) {
        g.fill(x, y, x + w, y + 1, colour);
        g.fill(x, y + h - 1, x + w, y + h, colour);
        g.fill(x, y + 1, x + 1, y + h - 1, colour);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, colour);
    }

    /** The wax seal as a button: the disc is painted in the bench art, the label by the screen. */
    private static final class SealButton extends AbstractButton {
        private final Runnable action;

        SealButton(int x, int y, int size, Runnable action) {
            super(x, y, size, size, Component.translatable("wandcraft.bench.seal"));
            this.action = action;
        }

        @Override
        public void onPress(InputWithModifiers input) {
            action.run();
        }

        @Override
        protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            // The disc is in the art; only a pressed-in rim while hovered.
            if (active && isHoveredOrFocused()) {
                int r = getWidth() / 2;
                int cx = getX() + r;
                int cy = getY() + r;
                g.fill(cx - r + 4, cy + r - 5, cx + r - 4, cy + r - 4, 0x60FFE0A0);
            }
        }

        @Override
        protected void renderDefaultLabel(ActiveTextCollector collector) {
            // The screen presses the label into the wax.
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
