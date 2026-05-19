package at.koopro.wizardsandbeasts.client.wand.gui;

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

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xD0101010);
        graphics.fill(x + 6, y + 6, x + imageWidth - 6, y + imageHeight - 6, 0xFF1e1a28);

        List<OllivanderPoolEntry> trials = menu.getTrials();
        float thresh = menu.getMatchThreshold();
        for (int i = 0; i < 3; i++) {
            int cx = x + 20 + i * 78;
            int cy = y + 40;
            boolean sel = menu.getSelectedIndex() == i;
            graphics.fill(cx, cy, cx + 70, cy + 120, sel ? 0xFF3a3548 : 0xFF2a2535);
            OllivanderPoolEntry e = trials.get(i);
            graphics.drawString(font, shorten(e.woodKey().getPath()), cx + 4, cy + 6, 0xFFddbb77, false);
            graphics.drawString(font, shorten(e.coreKey().getPath()), cx + 4, cy + 18, 0xFFcc99ee, false);
            graphics.drawString(font, e.flexibility(), cx + 4, cy + 30, 0xFFaaaaaa, false);
            float score = menu.getResonanceScore(i);
            graphics.drawString(font, Component.translatable("wandcraft.gui.resonance_fmt", score), cx + 4, cy + 44, 0xFF88eeff, false);
            int barW = 62;
            graphics.fill(cx + 4, cy + 58, cx + 4 + barW, cy + 64, 0xFF000000);
            graphics.fill(cx + 5, cy + 59, cx + 5 + (int) ((barW - 2) * Math.min(1, score)), cy + 63,
                    score >= thresh ? 0xFF44ff88 : 0xFF6688aa);
            if (score >= thresh) {
                graphics.drawString(font, Component.translatable("wandcraft.gui.choose_wand"), cx + 4, cy + 72, 0xFF88ffaa, false);
            }
        }
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
