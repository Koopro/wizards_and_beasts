package at.koopro.wizardsandbeasts.client.wand.gui;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.network.wand.ChooseTrialWandPayload;
import at.koopro.wizardsandbeasts.network.wand.SelectTrialWandPayload;
import at.koopro.wizardsandbeasts.wand.WandCastLines;
import at.koopro.wizardsandbeasts.wand.WandLoreNames;
import at.koopro.wizardsandbeasts.wand.cast.WandStatsResolver;
import at.koopro.wizardsandbeasts.wand.gui.OllivanderTrialMenu;
import at.koopro.wizardsandbeasts.wand.ollivander.OllivanderPoolEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class OllivanderTrialScreen extends AbstractContainerScreen<OllivanderTrialMenu> {

    private static final int CARD_W = 70;
    private static final int CARD_H = 120;
    private static final int CARD_STRIDE = 78;
    /** Card width less the 4px inset on each side — the room a label actually has. */
    private static final int CARD_TEXT_W = 62;

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
        HolderLookup.@Nullable Provider registries = registries();
        for (int i = 0; i < 3; i++) {
            int cx = cardLeft(x, i);
            int cy = cardTop(y);
            boolean sel = menu.getSelectedIndex() == i;
            McStylePanel.drawPanel(graphics, cx, cy, CARD_W, CARD_H,
                    sel ? WizardsPalette.SELECT : WizardsPalette.WELL,
                    sel ? WizardsPalette.BRASS : WizardsPalette.RAIL,
                    WizardsPalette.INK);

            OllivanderPoolEntry e = trials.get(i);
            graphics.drawString(font, fit(WandLoreNames.wood(registries, e.woodKey())), cx + 4, cy + 6,
                    WizardsPalette.BRASS_HI, false);
            graphics.drawString(font, fit(WandLoreNames.core(registries, e.coreKey())), cx + 4, cy + 18,
                    WizardsPalette.TEXT, false);
            graphics.drawString(font, e.flexibility(), cx + 4, cy + 30,
                    WizardsPalette.TEXT_DIM, false);

            float score = menu.getResonanceScore(i);
            graphics.drawString(font, Component.translatable("wandcraft.gui.resonance_fmt", score),
                    cx + 4, cy + 44, WizardsPalette.BRASS, false);

            int barW = CARD_TEXT_W;
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
                for (var line : font.split(Component.translatable("wandcraft.gui.wand_refuses", thresh), CARD_TEXT_W)) {
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

    /**
     * Hovering a card says what that wand would do to a spell.
     *
     * <p>The trial cards carry a resonance bar, which answers "will this wand have me" and nothing
     * else. Two wands can answer a wizard equally well and cast nothing alike — that is the entire
     * point of ten woods and ten cores — and until this existed the only way to compare them was to
     * accept one and read its tooltip, by which time the choice was spent. Ollivander's is the one
     * screen in the mod where a wizard picks between wands, so it is the one screen where the numbers
     * have to be legible.
     *
     * <p>Resolved from {@link OllivanderTrialMenu#createTrialStack} at the trial length — the same
     * stack the resonance score beside it was computed from — through the same
     * {@link WandStatsResolver#resolve} call the cast path makes, so a card cannot promise something
     * the wand will not do. The gifted wand's length is rolled on acceptance rather than fixed here,
     * so the length contribution can still move by a few points either way; the closing line says so
     * rather than letting the wizard find out afterwards.
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int x = (this.width - this.imageWidth) / 2;
        int cy = cardTop((this.height - this.imageHeight) / 2);
        if (mouseY < cy || mouseY >= cy + CARD_H) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            int cx = cardLeft(x, i);
            if (mouseX >= cx && mouseX < cx + CARD_W) {
                graphics.setTooltipForNextFrame(font, cardSummary(i), Optional.empty(), mouseX, mouseY);
                return;
            }
        }
    }

    /** Wood, core and flexibility in full, then what the three of them come to. */
    private List<Component> cardSummary(int index) {
        OllivanderPoolEntry e = menu.getTrials().get(index);
        HolderLookup.@Nullable Provider registries = registries();

        List<Component> lines = new ArrayList<>();
        lines.add(WandLoreNames.wood(registries, e.woodKey()).copy().withStyle(ChatFormatting.GOLD));
        lines.add(WandLoreNames.core(registries, e.coreKey()).copy().withStyle(ChatFormatting.LIGHT_PURPLE));
        lines.add(Component.translatable("wandcraft.tooltip.flexibility", e.flexibility())
                .withStyle(ChatFormatting.GRAY));

        List<Component> cast = WandCastLines.build(
                WandStatsResolver.resolve(menu.createTrialStack(index, false), registries));
        lines.add(Component.empty());
        if (cast.isEmpty()) {
            // Reachable two ways: a wand whose contributions cancel, and a client with no registries
            // yet. Silence would read as a broken screen rather than as the plain wand it describes.
            lines.add(Component.translatable("wandcraft.gui.trial_cast_plain")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            lines.add(Component.translatable("wandcraft.gui.trial_cast_header")
                    .withStyle(ChatFormatting.GRAY));
            lines.addAll(cast);
        }
        lines.add(Component.translatable("wandcraft.gui.trial_length_note")
                .withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    /**
     * The client's registries, or {@code null} before a world is attached. Both {@link WandLoreNames}
     * and {@link WandStatsResolver} read that as "I have nothing" and degrade to a readable id and a
     * neutral wand rather than throwing, so this screen does not need to.
     */
    private HolderLookup.@Nullable Provider registries() {
        return minecraft == null || minecraft.level == null ? null : minecraft.level.registryAccess();
    }

    private static int cardLeft(int screenLeft, int index) {
        return screenLeft + 20 + index * CARD_STRIDE;
    }

    private static int cardTop(int screenTop) {
        return screenTop + 40;
    }

    /**
     * Clipped to the pixels a card has, not to a character count. The old version cut at 12
     * characters, which is narrower than the card for "Elder" and wider than it for
     * "Thunderbird Tail Feather" — and it was fed the raw id path, so what it actually printed on a
     * Thestral card was {@code thestral_ta…}.
     */
    private String fit(Component name) {
        String s = name.getString();
        if (font.width(s) <= CARD_TEXT_W) {
            return s;
        }
        return font.plainSubstrByWidth(s, CARD_TEXT_W - font.width("…")) + "…";
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
            int cx = cardLeft(x, i);
            int cy = cardTop(y);
            if (mouseX >= cx && mouseX < cx + CARD_W && mouseY >= cy && mouseY < cy + CARD_H) {
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
