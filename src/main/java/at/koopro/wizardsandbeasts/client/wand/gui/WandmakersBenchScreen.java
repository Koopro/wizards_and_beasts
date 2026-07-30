package at.koopro.wizardsandbeasts.client.wand.gui;

import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import at.koopro.wizardsandbeasts.network.wand.SetFlexibilityPayload;
import at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Bench UI: procedural background (artist texture {@code textures/gui/wandmakers_bench.png} described in project docs).
 */
public class WandmakersBenchScreen extends AbstractContainerScreen<WandmakersBenchMenu> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            WizardsAndBeastsMod.MODID, "textures/gui/wandmakers_bench.png");

    /** Flexibility picker geometry. The stride is what gets drawn, so it is what the centring must use. */
    private static final int FLEX_BUTTON_W = 20;
    private static final int FLEX_BUTTON_H = 14;
    private static final int FLEX_BUTTON_STRIDE = 22;
    private static final int FLEX_ROW_Y = 72;

    /**
     * Tier bar geometry. The bar sits in the strip of bare panel below the hotbar — the artwork's last
     * slot row ends at y 186 and the panel's own border starts at y 194, leaving 187-193 free.
     */
    private static final int BAR_H = 6;
    private static final int BAR_INSET_X = 16;
    private static final int BAR_BOTTOM_MARGIN = 9;

    public WandmakersBenchScreen(WandmakersBenchMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 196;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        McStylePanel.drawTexture(graphics, TEXTURE, x, y, this.imageWidth, this.imageHeight,
                this.imageWidth, this.imageHeight);

        int tier = menu.getTierScoreScaled();
        float t = Math.min(1.0f, tier / 300.0f);
        int barColor = t < 0.5f
                ? interpolateColor(0xFF888888, 0xFFd4a020, t * 2.0f)
                : interpolateColor(0xFFd4a020, 0xFF8040c0, (t - 0.5f) * 2.0f);
        int bx = x + BAR_INSET_X;
        int by = barTop(y);
        int bw = barWidth();
        graphics.fill(bx, by, bx + bw, by + BAR_H, 0xFF000000);
        graphics.fill(bx + 1, by + 1, bx + 1 + (int) ((bw - 2) * t), by + BAR_H - 1, barColor);

        WandFlexibility[] values = WandFlexibility.values();
        int fx = flexRowLeft(x, values.length);
        int fy = y + FLEX_ROW_Y;
        for (int i = 0; i < values.length; i++) {
            int bx0 = fx + i * FLEX_BUTTON_STRIDE;
            boolean sel = menu.getFlexibilityOrdinal() == i;
            graphics.fill(bx0, fy, bx0 + FLEX_BUTTON_W, fy + FLEX_BUTTON_H, sel ? WizardsPalette.SELECT : WizardsPalette.WELL);
            graphics.drawString(font, String.valueOf(i + 1), bx0 + 6, fy + 3, WizardsPalette.BRASS_HI, false);
        }

        // The bench only speaks when it has nothing to hand over. There used to be a six-line preview of
        // the finished wand here, drawn from y+16 in 10-12px steps — straight through the slot artwork,
        // which puts the blank, arrow and output frames at y 35-50. It was also redundant: the stack
        // sitting in the output slot is the wand, and WandItem's own tooltip already lists wood, core,
        // flexibility, integrity, corruption, length and allegiance. Deleted rather than relocated; the
        // panel has no free band tall enough to hold it, and nothing was lost by removing it.
        if (menu.getBench().getInventory().getAmountAsLong(2) > 0) {
            return;
        }
        // An empty output slot used to be the bench's only answer to every failure.
        Component reason = statusMessage();
        if (reason != null) {
            int px = x + 10;
            int py = y + 16;
            // Two lines is what fits above the ingredient slots; the strings are written to it.
            for (var line : font.split(reason, this.imageWidth - 20).stream().limit(2).toList()) {
                graphics.drawString(font, line, px, py, WizardsPalette.TEXT_DIM, false);
                py += 10;
            }
        }
    }

    /**
     * Top edge of the tier bar. Shared by the drawing pass and the hover test so the two cannot drift:
     * they were separate copies of the same arithmetic, and the bar was drawn straight through the top of
     * the hotbar slots.
     */
    private int barTop(int panelTop) {
        return panelTop + this.imageHeight - BAR_BOTTOM_MARGIN;
    }

    private int barWidth() {
        return this.imageWidth - 2 * BAR_INSET_X;
    }

    /** Left edge of the flexibility picker, centred using the stride the buttons are actually drawn at. */
    private int flexRowLeft(int panelLeft, int buttonCount) {
        int span = (buttonCount - 1) * FLEX_BUTTON_STRIDE + FLEX_BUTTON_W;
        return panelLeft + (this.imageWidth - span) / 2;
    }

    /**
     * Both labels, in this panel's own palette.
     *
     * <p>Vanilla draws them in {@code #404040}. The artwork behind them is {@code (35,31,27)} at the title
     * and {@code (43,37,32)} at the inventory line — a contrast ratio of 1.58 : 1, which is not readable at
     * any GUI scale. Overridden here rather than in a shared helper: every other screen has its own
     * background and its own answer.
     */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, this.title, this.titleLabelX, this.titleLabelY, WizardsPalette.TEXT, false);
        graphics.drawString(font, this.playerInventoryTitle,
                this.inventoryLabelX, this.inventoryLabelY, WizardsPalette.TEXT_DIM, false);
    }

    /** What the bench is waiting for, or {@code null} while it has nothing to say. */
    private @Nullable Component statusMessage() {
        return switch (menu.getStatus()) {
            // With a shaped blank seated the bench knows the cheapest thing that wood can become, so it
            // can name the tier system before the player has found both ingredients.
            case WandmakersBenchMenu.STATUS_MISSING_INPUT -> menu.getRequiredTierScaled() > 0
                    ? Component.translatable("wandcraft.bench.needs_core",
                            menu.getRequiredTierScaled() / 100.0f, menu.getTierScoreScaled() / 100.0f)
                    : Component.translatable("wandcraft.bench.needs_input");
            case WandmakersBenchMenu.STATUS_BLANK_UNSHAPED ->
                    Component.translatable("wandcraft.bench.blank_unshaped");
            case WandmakersBenchMenu.STATUS_NO_RECIPE ->
                    Component.translatable("wandcraft.bench.no_recipe");
            case WandmakersBenchMenu.STATUS_BENCH_TOO_PLAIN ->
                    Component.translatable("wandcraft.bench.tier_too_low",
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        int tier = menu.getTierScoreScaled();
        int bx = x + BAR_INSET_X;
        int by = barTop(y);
        int bw = barWidth();
        if (mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + BAR_H) {
            List<Identifier> enh = menu.getEnhancerBlockIds();
            String line1 = Component.translatable("wandcraft.gui.tier_score", tier / 100.0f).getString();
            String line2 = Component.translatable("wandcraft.gui.enhancers", enh.size()).getString();
            String line3 = Component.translatable("wandcraft.gui.tier_hint").getString();
            int tipW = Math.max(font.width(line1), Math.max(font.width(line2), font.width(line3))) + 8;
            // The bar sits near the bottom of the panel, so mouseY - 34 is fine there — but the box is
            // also drawn unclamped, and on a short window the panel rides high enough to push it off the
            // top and right edges. Clamp both rather than assume where the panel landed.
            int tipX = Math.max(2, Math.min(mouseX + 8, this.width - tipW - 2));
            int tipY = Math.max(2, mouseY - 34);
            graphics.fill(tipX - 2, tipY - 2, tipX + tipW, tipY + 32, 0xE0100010);
            graphics.drawString(font, line1, tipX, tipY, 0xFFFFFFFF, false);
            graphics.drawString(font, line2, tipX, tipY + 10, 0xFFCCCCCC, false);
            graphics.drawString(font, line3, tipX, tipY + 20, WizardsPalette.TEXT_DIM, false);
        }
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
        WandFlexibility[] values = WandFlexibility.values();
        int fx = flexRowLeft(x, values.length);
        int fy = y + FLEX_ROW_Y;
        for (int i = 0; i < values.length; i++) {
            int bx0 = fx + i * FLEX_BUTTON_STRIDE;
            if (mouseX >= bx0 && mouseX < bx0 + FLEX_BUTTON_W && mouseY >= fy && mouseY < fy + FLEX_BUTTON_H) {
                ClientPacketDistributor.sendToServer(new SetFlexibilityPayload(menu.containerId, i));
                return true;
            }
        }
        return super.mouseClicked(event, isDoubleClick);
    }
}
