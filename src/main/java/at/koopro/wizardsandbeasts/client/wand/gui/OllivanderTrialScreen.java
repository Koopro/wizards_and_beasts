package at.koopro.wizardsandbeasts.client.wand.gui;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette.GuiSkin;
import at.koopro.wizardsandbeasts.client.gui.util.GuiText;
import at.koopro.wizardsandbeasts.client.gui.util.UiContrast;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedButton;
import at.koopro.wizardsandbeasts.network.wand.ChooseTrialWandPayload;
import at.koopro.wizardsandbeasts.network.wand.SelectTrialWandPayload;
import at.koopro.wizardsandbeasts.wand.WandCastLines;
import at.koopro.wizardsandbeasts.wand.WandLoreNames;
import at.koopro.wizardsandbeasts.wand.cast.WandStatsResolver;
import at.koopro.wizardsandbeasts.wand.gui.OllivanderTrialMenu;
import at.koopro.wizardsandbeasts.wand.ollivander.OllivanderPoolEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Ollivander's: three wands on a tray, and what each of them would do.
 *
 * <p>Cut on the {@code workbench} material — worn wood, shellac and brass calipers — which was
 * generated for this screen and had no consumer until now.
 *
 * <h2>What was wrong with the old one</h2>
 *
 * <p><strong>The click target was split by a line nobody could see.</strong> Each 70×120 card handled
 * its own mouse events, and {@code mouseY < cy + 58} meant "select"; anything below meant "accept
 * this wand, permanently". One rectangle, two outcomes, no visible boundary, on a screen a player
 * meets once. A row selects now, and taking the wand is a button.
 *
 * <p><strong>The comparison was in a tooltip.</strong> This is the one screen in the mod where a
 * wizard chooses between wands, and the only thing on it was a resonance bar — which answers "will
 * this wand have me" and nothing else. Two wands can answer equally well and cast nothing alike;
 * that is the entire point of ten woods and ten cores. Those numbers are on the panel now.
 *
 * <p><strong>Three cards, 62 usable pixels each.</strong> Wood, core, flexibility, a score, a bar and
 * a two-line refusal, in a column narrower than the word "Thunderbird". The tray is a list and the
 * detail is a pane, so each gets the width its content needs.
 *
 * <h2>Colour</h2>
 *
 * <p>Nothing here is drawn in {@code WizardsPalette}'s inks. This material's face is light, and the
 * leather palette is built for the dark HUD: {@code BRASS_HI}, which the old screen used for every
 * label, is 1.35 : 1 on it. Body text is {@link GuiSkin#ink()}, and the cast contributions — whose
 * tooltip vocabulary is {@code GREEN} at 1.58 : 1 and {@code DARK_RED} at 2.40 : 1 — go through
 * {@link UiContrast}, which keeps the hue and moves only the luminance, so good still reads green.
 */
public class OllivanderTrialScreen extends AbstractContainerScreen<OllivanderTrialMenu> {

    private static final GuiSkin SKIN = GuiSkin.WORKBENCH;

    /** How many wands Ollivander puts on the tray. Fixed by {@link OllivanderTrialMenu}. */
    private static final int TRIALS = 3;

    private static final int FRAME = WizardsMetrics.PANEL_SPRITE_BORDER;
    private static final int PAD = WizardsMetrics.SPACE_M;

    private static final int HEADER_H = 44;
    /**
     * The footer band.
     *
     * <p>40, not 30. The button sits at {@code imageHeight - FRAME - PAD - TAKE_H} = 206 and a
     * 30px footer puts the divider at 210 — through the middle of it. Anything here must clear the
     * control it belongs to.
     */
    private static final int FOOTER_H = 40;
    private static final int TRAY_W = 136;
    private static final int GUTTER = WizardsMetrics.SPACE_L;

    private static final int ROW_H = 42;
    private static final int ROW_GAP = WizardsMetrics.SPACE_M;
    private static final int BAR_H = 6;

    private static final int TAKE_W = 120;
    private static final int TAKE_H = 18;

    /** Beneficial and harmful cast contributions, lifted onto this screen's own ground. */
    private final int goodInk;
    private final int badInk;

    private ThemedButton takeButton;

    public OllivanderTrialScreen(OllivanderTrialMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = WizardsMetrics.PANEL_STANDARD_W;
        this.imageHeight = WizardsMetrics.PANEL_STANDARD_H;
        this.goodInk = UiContrast.readableOn(0x55FF55, SKIN.base(), UiContrast.AA_TEXT);
        this.badInk = UiContrast.readableOn(0xAA0000, SKIN.base(), UiContrast.AA_TEXT);
    }

    @Override
    protected void init() {
        super.init();
        takeButton = ThemedButton.skinned(
                leftPos + (imageWidth - TAKE_W) / 2,
                topPos + imageHeight - FRAME - PAD - TAKE_H,
                TAKE_W, TAKE_H,
                Component.translatable("wandcraft.gui.choose_wand"),
                this::takeSelected, SKIN);
        addRenderableWidget(takeButton);
    }

    /**
     * Sends the acceptance the button stands for.
     *
     * <p>Guarded again here rather than relying on {@code active}: the button's enabled state is
     * refreshed once a frame from a score the server owns, and a click landing in the same tick as a
     * score change would otherwise send an acceptance the bench has already stopped offering. The
     * server re-checks regardless — this only avoids a pointless round trip.
     */
    private void takeSelected() {
        int index = menu.getSelectedIndex();
        if (answers(index)) {
            ClientPacketDistributor.sendToServer(new ChooseTrialWandPayload(menu.containerId, index));
        }
    }

    /** Whether trial {@code index} resonates enough to be taken. */
    private boolean answers(int index) {
        return index >= 0 && index < TRIALS
                && menu.getResonanceScore(index) >= menu.getMatchThreshold();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        McStylePanel.drawSkinPanel(graphics, SKIN, leftPos, topPos, imageWidth, imageHeight);
        McStylePanel.drawSkinSeal(graphics, SKIN,
                leftPos + imageWidth - FRAME - McStylePanel.SEAL_SIZE, topPos + FRAME);
        McStylePanel.drawSkinDivider(graphics, SKIN, leftPos + FRAME,
                topPos + HEADER_H - WizardsMetrics.DIVIDER_H, imageWidth - 2 * FRAME);
        McStylePanel.drawSkinDivider(graphics, SKIN, leftPos + FRAME,
                topPos + imageHeight - FOOTER_H, imageWidth - 2 * FRAME);

        McStylePanel.drawSkinInset(graphics, SKIN, detailX(), bodyTop(), detailW(), bodyH());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The button's availability follows the selection, which the server owns and can change
        // between frames. Set before super.render so the frame that draws it agrees with it.
        takeButton.active = answers(menu.getSelectedIndex());

        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawString(font, this.title, leftPos + FRAME + PAD, topPos + FRAME + 2,
                SKIN.ink(), false);
        graphics.drawString(font, Component.translatable("wandcraft.gui.trial_epigraph"),
                leftPos + FRAME + PAD, topPos + FRAME + 2 + WizardsMetrics.LINE_SECTION,
                SKIN.muted(), false);

        renderTray(graphics);
        renderDetail(graphics);
    }

    /** The three wands, as rows. */
    private void renderTray(GuiGraphics graphics) {
        HolderLookup.@Nullable Provider registries = registries();
        List<OllivanderPoolEntry> trials = menu.getTrials();
        float threshold = menu.getMatchThreshold();

        for (int i = 0; i < TRIALS; i++) {
            int rx = trayX();
            int ry = rowTop(i);
            boolean selected = menu.getSelectedIndex() == i;

            McStylePanel.drawSkinInset(graphics, SKIN, rx, ry, TRAY_W, ROW_H);
            McStylePanel.drawSkinRow(graphics, SKIN, rx + 2, ry + 2, TRAY_W - 4, ROW_H - 4, selected);

            OllivanderPoolEntry entry = trials.get(i);
            int tx = rx + WizardsMetrics.SPACE_M;
            int textW = TRAY_W - 2 * WizardsMetrics.SPACE_M;

            // Fitted rather than clipped: "Thunderbird Tail Feather" and "Yew" want very different
            // amounts of room, and the old screen cut both at twelve characters.
            GuiText.drawFitted(graphics, font,
                    WandLoreNames.wood(registries, entry.woodKey()).getString(),
                    tx, ry + WizardsMetrics.SPACE_S, textW, SKIN.ink());
            GuiText.drawFitted(graphics, font,
                    WandLoreNames.core(registries, entry.coreKey()).getString(),
                    tx, ry + WizardsMetrics.SPACE_S + WizardsMetrics.LINE_TIGHT, textW, SKIN.muted());

            renderResonanceBar(graphics, tx, ry + ROW_H - WizardsMetrics.SPACE_S - BAR_H, textW,
                    menu.getResonanceScore(i), threshold);
        }
    }

    /**
     * How far this wand has come towards answering, and where the threshold sits.
     *
     * <p>The notch is the point. A bare fill says "this much"; it does not say whether this much is
     * enough, and enough is the only question the bar is being asked. Below the mark the fill stays
     * in the material's muted tone and only a wand that answers gets the brass.
     */
    private void renderResonanceBar(GuiGraphics graphics, int x, int y, int w,
                                    float score, float threshold) {
        graphics.fill(x, y, x + w, y + BAR_H, SKIN.frame());
        int fill = (int) ((w - 2) * Math.min(1.0f, Math.max(0.0f, score)));
        boolean answers = score >= threshold;
        graphics.fill(x + 1, y + 1, x + 1 + fill, y + BAR_H - 1,
                answers ? SKIN.accent() : SKIN.muted());

        int notch = x + 1 + (int) ((w - 2) * Math.min(1.0f, Math.max(0.0f, threshold)));
        graphics.fill(notch, y - 1, notch + 1, y + BAR_H + 1, SKIN.ink());
    }

    /** Everything about the selected wand that a bar cannot say. */
    private void renderDetail(GuiGraphics graphics) {
        int index = menu.getSelectedIndex();
        int x = detailX() + WizardsMetrics.SPACE_M;
        int w = detailW() - 2 * WizardsMetrics.SPACE_M;
        int y = bodyTop() + WizardsMetrics.SPACE_M;

        if (index < 0 || index >= TRIALS) {
            // Nothing chosen yet. Saying so beats an empty pane that reads as a screen still loading.
            for (var line : font.split(
                    Component.translatable("wandcraft.gui.trial_pick_one"), w)) {
                graphics.drawString(font, line, x, y, SKIN.muted(), false);
                y += WizardsMetrics.LINE_TIGHT;
            }
            return;
        }

        HolderLookup.@Nullable Provider registries = registries();
        OllivanderPoolEntry entry = menu.getTrials().get(index);

        GuiText.drawFitted(graphics, font,
                WandLoreNames.wood(registries, entry.woodKey()).getString(), x, y, w, SKIN.ink());
        y += WizardsMetrics.LINE_BODY;
        GuiText.drawFitted(graphics, font,
                WandLoreNames.core(registries, entry.coreKey()).getString(), x, y, w, SKIN.ink());
        y += WizardsMetrics.LINE_BODY;
        graphics.drawString(font, Component.translatable("wandcraft.tooltip.flexibility",
                entry.flexibility()), x, y, SKIN.muted(), false);
        y += WizardsMetrics.LINE_BODY;

        McStylePanel.drawSkinDivider(graphics, SKIN, x, y, w);
        y += WizardsMetrics.DIVIDER_H + WizardsMetrics.SPACE_XS;

        float score = menu.getResonanceScore(index);
        float threshold = menu.getMatchThreshold();
        boolean answers = score >= threshold;
        graphics.drawString(font,
                Component.translatable("wandcraft.gui.resonance_of", score, threshold),
                x, y, answers ? goodInk : badInk, false);
        y += WizardsMetrics.LINE_BODY;

        if (!answers) {
            // Beside the number it is about. This used to be drawn in the footer, where it landed on
            // top of the very button it was explaining -- and before that, inside whichever card had
            // refused, which is where a player looks for it only if they already know a card can.
            for (var line : font.split(
                    Component.translatable("wandcraft.gui.wand_refuses", threshold), w)) {
                graphics.drawString(font, line, x, y, badInk, false);
                y += WizardsMetrics.LINE_TIGHT;
            }
        }
        y += WizardsMetrics.SPACE_S;

        // The length note is pinned to the foot of the pane and drawn first, because it is the one
        // line here that must always be visible: it is the caveat on every number above it.
        var noteLines = font.split(Component.translatable("wandcraft.gui.trial_length_note"), w);
        int noteY = bodyTop() + bodyH() - WizardsMetrics.SPACE_M
                - noteLines.size() * WizardsMetrics.LINE_TIGHT;
        int ny = noteY;
        for (var line : noteLines) {
            graphics.drawString(font, line, x, ny, SKIN.muted(), false);
            ny += WizardsMetrics.LINE_TIGHT;
        }

        // The same resolve() the cast path makes, on the same stack the score beside it came from,
        // so a pane cannot promise something the wand will not do.
        List<WandCastLines.Line> cast = WandCastLines.lines(
                WandStatsResolver.resolve(menu.createTrialStack(index, false), registries));
        if (cast.isEmpty()) {
            // Reachable two ways: a wand whose contributions cancel, and a client with no registries
            // yet. Silence would read as a broken pane rather than as the plain wand it describes.
            graphics.drawString(font, Component.translatable("wandcraft.gui.trial_cast_plain"),
                    x, y, SKIN.muted(), false);
            return;
        }

        graphics.drawString(font, Component.translatable("wandcraft.gui.trial_cast_header"),
                x, y, SKIN.muted(), false);
        y += WizardsMetrics.LINE_BODY;

        // Bounded against the pinned note rather than trusted to fit. The worst case is eight rows
        // -- damage, cooldown, range, misfire and one per SpellCategory -- which is taller than the
        // pane, and a list that runs past its own frame is how the old cards looked cramped.
        int limit = noteY - WizardsMetrics.SPACE_S;
        for (WandCastLines.Line line : cast) {
            if (y + WizardsMetrics.LINE_TIGHT > limit) {
                graphics.drawString(font, Component.translatable("wandcraft.gui.trial_cast_more"),
                        x + WizardsMetrics.SPACE_S, y, SKIN.muted(), false);
                break;
            }
            // Unstyled from `lines()`, coloured for this ground rather than for a tooltip's.
            graphics.drawString(font, line.text(), x + WizardsMetrics.SPACE_S, y,
                    line.beneficial() ? goodInk : badInk, false);
            y += WizardsMetrics.LINE_TIGHT;
        }
    }

    // ── Geometry, shared by the drawing pass and the hit test ──────────────
    //
    // The two used to be separate copies of the same arithmetic, which is how a card ended up with
    // an invisible boundary at cy+58 that only one of them knew about.

    private int trayX() {
        return leftPos + FRAME + PAD;
    }

    private int bodyTop() {
        return topPos + HEADER_H;
    }

    private int bodyH() {
        return imageHeight - HEADER_H - FOOTER_H - WizardsMetrics.SPACE_S;
    }

    private int detailX() {
        return trayX() + TRAY_W + GUTTER;
    }

    private int detailW() {
        return leftPos + imageWidth - FRAME - PAD - detailX();
    }

    private int rowTop(int index) {
        return bodyTop() + index * (ROW_H + ROW_GAP);
    }

    /**
     * The client's registries, or {@code null} before a world is attached. Both {@link WandLoreNames}
     * and {@link WandStatsResolver} read that as "I have nothing" and degrade to a readable id and a
     * neutral wand rather than throwing, so this screen does not need to.
     */
    private HolderLookup.@Nullable Provider registries() {
        return minecraft == null || minecraft.level == null ? null : minecraft.level.registryAccess();
    }

    /** No slots and no player inventory, so vanilla's two labels have nothing to name. */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (event.button() != 0) {
            return super.mouseClicked(event, isDoubleClick);
        }
        double mouseX = event.x();
        double mouseY = event.y();
        if (mouseX >= trayX() && mouseX < trayX() + TRAY_W) {
            for (int i = 0; i < TRIALS; i++) {
                int ry = rowTop(i);
                if (mouseY >= ry && mouseY < ry + ROW_H) {
                    // Selecting is all a row does. Taking the wand is the button, and there is no
                    // longer any part of this screen where a click can commit by accident.
                    ClientPacketDistributor.sendToServer(
                            new SelectTrialWandPayload(menu.containerId, i));
                    return true;
                }
            }
        }
        return super.mouseClicked(event, isDoubleClick);
    }
}
