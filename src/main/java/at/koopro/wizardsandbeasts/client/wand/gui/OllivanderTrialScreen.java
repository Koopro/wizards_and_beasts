package at.koopro.wizardsandbeasts.client.wand.gui;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.network.wand.ChooseTrialWandPayload;
import at.koopro.wizardsandbeasts.network.wand.SelectTrialWandPayload;
import at.koopro.wizardsandbeasts.wand.gui.OllivanderTrialMenu;
import at.koopro.wizardsandbeasts.wand.ollivander.OllivanderPoolEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.List;

public class OllivanderTrialScreen extends AbstractContainerScreen<OllivanderTrialMenu> {

    public OllivanderTrialScreen(OllivanderTrialMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageHeight = 200;
        this.imageWidth = 256;
    }

    /**
     * Ollivander's tray, in the mod's leather and gold.
     *
     * <p>This screen used to draw itself in a cold blue-lavender scheme of its own — panel
     * {@code #1e1a28}, cards {@code #2a2535}, lavender and cyan text — that shared no hue with
     * any other screen in the mod, on what is one of the first things a new wizard sees. Every
     * colour here now comes from {@link WizardsPalette}.
     *
     * <p>Resonance still reads at a glance without a green/blue signal colour: a wand that
     * answers you fills its bar in bright brass, one that does not stays dim leather.
     */
    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xD0000000);
        McStylePanel.drawPanel(graphics, x + 6, y + 6, imageWidth - 12, imageHeight - 12,
                WizardsPalette.PLATE, WizardsPalette.EDGE_HI, WizardsPalette.INK);
        McStylePanel.drawBorder(graphics, x + 8, y + 8, imageWidth - 16, imageHeight - 16,
                WizardsPalette.BRASS, WizardsPalette.LINE);

        List<OllivanderPoolEntry> trials = menu.getTrials();
        float thresh = menu.getMatchThreshold();
        for (int i = 0; i < 3; i++) {
            int cx = x + 20 + i * 78;
            int cy = y + 40;
            boolean sel = menu.getSelectedIndex() == i;
            McStylePanel.drawPanel(graphics, cx, cy, 70, 120,
                    sel ? WizardsPalette.SELECT : WizardsPalette.WELL,
                    sel ? WizardsPalette.BRASS : WizardsPalette.RAIL,
                    WizardsPalette.INK);

            OllivanderPoolEntry e = trials.get(i);
            graphics.drawString(font, shorten(e.woodKey().getPath()), cx + 4, cy + 6,
                    WizardsPalette.BRASS_HI, false);
            graphics.drawString(font, shorten(e.coreKey().getPath()), cx + 4, cy + 18,
                    WizardsPalette.TEXT, false);
            graphics.drawString(font, e.flexibility(), cx + 4, cy + 30,
                    WizardsPalette.TEXT_DIM, false);

            float score = menu.getResonanceScore(i);
            graphics.drawString(font, Component.translatable("wandcraft.gui.resonance_fmt", score),
                    cx + 4, cy + 44, WizardsPalette.BRASS, false);

            int barW = 62;
            boolean answers = score >= thresh;
            graphics.fill(cx + 4, cy + 58, cx + 4 + barW, cy + 64, WizardsPalette.INK);
            graphics.fill(cx + 5, cy + 59, cx + 5 + (int) ((barW - 2) * Math.min(1, score)), cy + 63,
                    answers ? WizardsPalette.BRASS_HI : WizardsPalette.RAIL);

            if (answers) {
                graphics.drawString(font, Component.translatable("wandcraft.gui.choose_wand"),
                        cx + 4, cy + 72, WizardsPalette.BRASS_HI, false);
            } else {
                // Without this the tray just sits there inert and the wizard cannot tell why.
                int hintY = cy + 72;
                for (var line : font.split(Component.translatable("wandcraft.gui.wand_refuses", thresh), 62)) {
                    graphics.drawString(font, line, cx + 4, hintY, WizardsPalette.TEXT_DIM, false);
                    hintY += 10;
                }
            }
        }
    }

    /**
     * The title only, in a colour that survives this screen's background.
     *
     * <p>Two defects in the inherited version. Vanilla draws both labels in {@code #404040}, which against
     * the old cold-lavender panel is a contrast ratio of about 1.5 : 1 — the title was effectively
     * invisible. And the second label is {@code playerInventoryTitle}, drawn at {@code imageHeight - 94}
     * = y 106: {@link OllivanderTrialMenu} has no slots at all, so that was a heading for an inventory
     * this screen does not show, printed straight across the middle trial card.
     */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, this.title, this.titleLabelX, this.titleLabelY, WizardsPalette.BRASS_HI, false);
    }

    private static String shorten(String s) {
        return s.length() > 12 ? s.substring(0, 10) + "…" : s;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (event.button() != 0) {
            return super.mouseClicked(event, isDoubleClick);
        }
        double mouseX = event.x();
        double mouseY = event.y();
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        for (int i = 0; i < 3; i++) {
            int cx = x + 20 + i * 78;
            int cy = y + 40;
            if (mouseX >= cx && mouseX < cx + 70 && mouseY >= cy && mouseY < cy + 120) {
                if (mouseY < cy + 58) {
                    ClientPacketDistributor.sendToServer(new SelectTrialWandPayload(menu.containerId, i));
                } else if (menu.getResonanceScore(i) >= menu.getMatchThreshold()) {
                    ClientPacketDistributor.sendToServer(new ChooseTrialWandPayload(menu.containerId, i));
                }
                return true;
            }
        }
        return super.mouseClicked(event, isDoubleClick);
    }
}
