package at.koopro.wizardsandbeasts.client.wand.gui;

import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui;
import at.koopro.wizardsandbeasts.client.gui.util.GuiText;
import at.koopro.wizardsandbeasts.wand.bench.BenchRole;
import at.koopro.wizardsandbeasts.wand.bench.BenchTool;
import at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu;
import at.koopro.wizardsandbeasts.wand.refine.SetRefineActionPayload;
import at.koopro.wizardsandbeasts.wand.refine.WandRefineMenu;
import at.koopro.wizardsandbeasts.wand.refine.WandRefinement.Action;
import at.koopro.wizardsandbeasts.wand.refine.WandRefinement.Status;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.List;
import java.util.Locale;

import static at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu.BLANK_X;
import static at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu.BLANK_Y;
import static at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu.CORE_X;
import static at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu.CORE_Y;
import static at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu.TOP_H;

/**
 * Mending a finished wand, on the same bench top as making one (P5, 2026-11-26): the wand lies between the lathe's
 * centres where a blank would, the material in the glass dish, and the mended wand waits in the wand box; taking it
 * pays. The five works are burnt into the boxwood rule, and pressing one chooses it. The slip says what the chosen
 * work needs and what is missing. A tin of beeswax sits where the making side's seal is: mending begins no act.
 *
 * <p>The rules are {@link at.koopro.wizardsandbeasts.wand.refine.WandRefinement}'s (never risky, 2026-10-22).
 */
public class WandRefineScreen extends AbstractContainerScreen<WandRefineMenu> {

    private static final String KEY = "gui.wizards_and_beasts.wand_refine.";

    // Mirrored from tools/bench_screen.py, as on the making side.
    private static final int PLATE_X = 6, PLATE_Y = 4;
    private static final int TOOL_X0 = 11, TOOL_STEP = 21, TOOL_Y = 25;
    private static final int RULE_X0 = 6, RULE_Y0 = 82, RULE_X1 = 226, RULE_Y1 = 94;
    private static final int SLIP_X0 = 6, SLIP_Y0 = 100, SLIP_X1 = 152;

    public WandRefineScreen(WandRefineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = WandmakersBenchMenu.GUI_W;
        this.imageHeight = WandmakersBenchMenu.GUI_H;
        this.titleLabelX = PLATE_X + 5;
        this.titleLabelY = PLATE_Y + 3;
        this.inventoryLabelX = WandmakersBenchMenu.INV_X;
        this.inventoryLabelY = WandmakersBenchMenu.INV_Y - 11;
    }

    // ── painting ─────────────────────────────────────────────────────────────

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        List<int[]> origins = WandmakersBenchMenu.slotOrigins();
        BenchChrome.back(g, x, y, imageWidth, TOP_H, imageHeight, BenchChrome.TOP_MEND, BenchRole.MEND,
                origins.subList(3, origins.size()));
        int mask = menu.toolMask();
        for (BenchTool tool : BenchTool.values()) {
            if ((mask & tool.bit()) != 0) {
                g.renderItem(BenchToolsRow.stack(tool), x + TOOL_X0 + tool.ordinal() * TOOL_STEP, y + TOOL_Y);
            }
        }
        renderRule(g, x, y, mouseX, mouseY);
        if (WandRefineMenu.isFinishedWand(menu.getCarried())) {
            Ui.slotTarget(g, x + BLANK_X, y + BLANK_Y, 0, WizardsPalette.GILT_LIGHT);
        } else if (!menu.getCarried().isEmpty()) {
            Ui.slotTarget(g, x + CORE_X, y + CORE_Y, 0, WizardsPalette.GILT_LIGHT);
        }
    }

    /** The five works on the rule: the chosen one burnt and ringed in brass, the one under the mouse lifted. */
    private void renderRule(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        Action[] actions = Action.values();
        int seg = (RULE_X1 - RULE_X0) / actions.length;
        Action chosen = menu.action();
        int hover = ruleIndexAt(mouseX, mouseY);
        for (int i = 0; i < actions.length; i++) {
            int sx = x + RULE_X0 + i * seg;
            boolean on = actions[i] == chosen;
            if (on) {
                g.fill(sx + 2, y + RULE_Y0 + 3, sx + seg - 1, y + RULE_Y1 - 1, 0xB04A2E16);
                outline(g, sx + 1, y + RULE_Y0 + 1, seg - 1, RULE_Y1 - RULE_Y0 - 1, WizardsPalette.GILT);
            } else if (i == hover) {
                g.fill(sx + 2, y + RULE_Y0 + 3, sx + seg - 1, y + RULE_Y1 - 1, 0x40FFF0C0);
            }
            Component label = Component.translatable(actions[i].key() + ".short");
            int lx = sx + (seg - font.width(label)) / 2 + 1;
            g.drawString(font, label, lx, y + RULE_Y0 + 3, on ? WizardsPalette.PAGE_LIGHT : WizardsPalette.PAGE_INK_2, false);
        }
    }

    private int ruleIndexAt(double mx, double my) {
        int seg = (RULE_X1 - RULE_X0) / Action.values().length;
        double rx = mx - leftPos - RULE_X0;
        double ry = my - topPos;
        if (rx < 0 || rx >= seg * Action.values().length || ry < RULE_Y0 || ry > RULE_Y1) {
            return -1;
        }
        return (int) (rx / seg);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, WizardsPalette.PAGE_INK, false);
        BenchChrome.tagLabels(g, font, imageWidth, BenchRole.MEND);
        renderSlip(g);
    }

    /** The chosen work, what the bench says about it, and what it is paid with. */
    private void renderSlip(GuiGraphics g) {
        Action action = menu.action();
        int tx = SLIP_X0 + 12;
        GuiText.drawFitted(g, font, Component.translatable(action.key()).getString(), tx, SLIP_Y0 + 4,
                SLIP_X1 - tx - 4, WizardsPalette.PAGE_INK);
        Status status = menu.status();
        int colour = status == Status.READY ? WizardsPalette.PAGE_GOOD : WizardsPalette.PAGE_RUBRIC;
        // Two lines at most, green when the work is ready to take, red for what is missing.
        var lines = font.split(statusMessage(status, action), SLIP_X1 - SLIP_X0 - 10);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            g.drawString(font, lines.get(i), SLIP_X0 + 5, SLIP_Y0 + 14 + i * 9, colour, false);
        }
        if (status == Status.READY) {
            return;                                         // "Take it. Uses …" already says what it costs.
        }
        Component pays = Component.translatable(KEY + "pays",
                Component.translatable(KEY + "material." + action.name().toLowerCase(Locale.ROOT)));
        GuiText.drawFitted(g, font, pays.getString(), SLIP_X0 + 5, SLIP_Y0 + 33, SLIP_X1 - SLIP_X0 - 10,
                WizardsPalette.PAGE_INK_2);
    }

    private Component statusMessage(Status status, Action action) {
        Component material = Component.translatable(KEY + "material." + action.name().toLowerCase(Locale.ROOT));
        return switch (status) {
            case READY, NO_MATERIAL, WRONG_MATERIAL -> Component.translatable(status.key(), material);
            case NEEDS_TOOL -> Component.translatable(status.key(), menu.requiredTool() == null
                    ? Component.empty() : BenchToolsRow.stack(menu.requiredTool()).getHoverName());
            default -> Component.translatable(status.key());
        };
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        renderToolTooltip(g, mouseX, mouseY);
    }

    private void renderToolTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int mask = menu.toolMask();
        for (BenchTool tool : BenchTool.values()) {
            int tx = leftPos + TOOL_X0 + tool.ordinal() * TOOL_STEP;
            int ty = topPos + TOOL_Y;
            if (mouseX >= tx - 1 && mouseX < tx + 17 && mouseY >= ty - 1 && mouseY < ty + 17) {
                boolean here = (mask & tool.bit()) != 0;
                g.setTooltipForNextFrame(font, List.of(
                        BenchToolsRow.stack(tool).getHoverName(),
                        Component.translatable(here ? "wandcraft.bench.tool_here" : "wandcraft.bench.tool_missing")
                ), java.util.Optional.empty(), mouseX, mouseY);
                return;
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            if (BenchChrome.clickTag(event.x(), event.y(), leftPos, topPos, imageWidth, BenchRole.MEND, menu.containerId)) {
                return true;
            }
            int i = ruleIndexAt(event.x(), event.y());
            if (i >= 0) {
                // The server holds the work; the screen follows the synced value, never assumes it was accepted.
                ClientPacketDistributor.sendToServer(new SetRefineActionPayload(menu.containerId, i));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int colour) {
        g.fill(x, y, x + w, y + 1, colour);
        g.fill(x, y + h - 1, x + w, y + h, colour);
        g.fill(x, y + 1, x + 1, y + h - 1, colour);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, colour);
    }
}
