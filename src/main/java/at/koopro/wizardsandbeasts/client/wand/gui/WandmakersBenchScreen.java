package at.koopro.wizardsandbeasts.client.wand.gui;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette.GuiSkin;
import at.koopro.wizardsandbeasts.client.gui.util.GuiText;
import at.koopro.wizardsandbeasts.client.gui.widget.CyclerWidget;
import at.koopro.wizardsandbeasts.network.wand.SetFlexibilityPayload;
import at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The wandmaker's bench, on its own material.
 *
 * <p>Slot geometry is vanilla's and is not this screen's to move, so unlike its sibling at
 * Ollivander's this stays 176×196. Everything inside that frame changed.
 *
 * <p>The background is generated now — see {@code gui_chrome.a_wandmakers_bench} — which makes the
 * bench <em>light</em> where it used to be a flat {@code #171411}. That is the whole reason the text
 * colours below come from {@link GuiSkin#WORKBENCH} rather than {@code WizardsPalette}: the leather
 * palette's {@code TEXT} on this face is 1.39 : 1 and {@code TEXT_DIM} is 1.34 : 1. Both were
 * correct against the old field and are invisible against this one.
 *
 * <p>The flexibility picker was five hand-drawn rectangles labelled {@code 1} through {@code 5},
 * hit-tested by hand. A wand's flexibility is one of Ollivander's five words and the bench was
 * asking for it as an ordinal.
 */
public class WandmakersBenchScreen extends AbstractContainerScreen<WandmakersBenchMenu> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            WizardsAndBeastsMod.MODID, "textures/gui/wandmakers_bench.png");

    private static final GuiSkin SKIN = GuiSkin.WORKBENCH;

    /**
     * The flexibility row.
     *
     * <p>{@code gui_chrome.a_wandmakers_bench} cuts the well this sits in at y 64–85, two pixels
     * clear on each side. The two files have to agree and neither derives from the other, so
     * changing this means changing that.
     */
    private static final int FLEX_ROW_Y = 66;
    private static final int FLEX_ROW_H = 18;
    private static final int FLEX_INSET_X = 16;

    /**
     * Where the bench says what it is waiting for.
     *
     * <p>One line, and fitted rather than wrapped. The band looks like it runs to the slot grid at
     * y 111, but vanilla draws {@code playerInventoryTitle} at {@code imageHeight - 94} = y 102 —
     * budgeting against the slots instead of the label is how the second line of "Place a shaped
     * blank and a core" ended up printed through the word "Inventory".
     */
    private static final int STATUS_Y = 90;

    /**
     * Tier bar geometry. The bar sits in the strip of bare panel below the hotbar — the artwork's
     * last slot row ends at y 187 and the panel's own border starts at y 193, and the background
     * paints a recessed track across exactly that band.
     */
    private static final int BAR_H = 6;
    private static final int BAR_INSET_X = 16;
    private static final int BAR_BOTTOM_MARGIN = 9;

    /** Full marks on the tier scale, scaled by 100 the way the menu reports it. */
    private static final float TIER_FULL = 300.0f;

    private CyclerWidget<WandFlexibility> flexCycler;

    public WandmakersBenchScreen(WandmakersBenchMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 196;
        this.inventoryLabelY = this.imageHeight - 94;
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
        WandFlexibility current = values.get(
                Math.floorMod(menu.getFlexibilityOrdinal(), values.size()));

        flexCycler = new CyclerWidget<>(values, current,
                flexibility -> flexibility.label().getString(),
                this::chooseFlexibility).skin(SKIN);
        flexCycler.setBounds(leftPos + FLEX_INSET_X, topPos + FLEX_ROW_Y,
                imageWidth - 2 * FLEX_INSET_X, FLEX_ROW_H);
        flexCycler.buttons().forEach(this::addRenderableWidget);
    }

    /**
     * Tells the server which flexibility the bench should shape for.
     *
     * <p>The local cycler is <em>not</em> updated optimistically. The bench's flexibility lives in
     * the menu's synced data, so the value shown has to be the value the server holds — an
     * optimistic one would read as accepted while the bench went on making something else.
     */
    private void chooseFlexibility(WandFlexibility flexibility) {
        ClientPacketDistributor.sendToServer(
                new SetFlexibilityPayload(menu.containerId, flexibility.ordinal()));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // Re-seed when the server's value moves, including when it rejects what we asked for.
        if (flexCycler != null && flexCycler.selected().ordinal() != menu.getFlexibilityOrdinal()) {
            rebuildCycler();
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        McStylePanel.drawTexture(graphics, TEXTURE, leftPos, topPos, this.imageWidth, this.imageHeight,
                this.imageWidth, this.imageHeight);

        int tier = menu.getTierScoreScaled();
        float t = Math.min(1.0f, tier / TIER_FULL);
        // Grey through gold to a violet only a master's bench reaches. Semantic rather than
        // thematic, so it stays out of the skin: it says how far up the ladder this bench is.
        int barColor = t < 0.5f
                ? interpolateColor(0xFF888888, 0xFFd4a020, t * 2.0f)
                : interpolateColor(0xFFd4a020, 0xFF8040c0, (t - 0.5f) * 2.0f);
        int bx = leftPos + BAR_INSET_X;
        int by = barTop(topPos);
        int bw = barWidth();
        // The track itself is painted into the background art; only the fill is drawn here.
        graphics.fill(bx + 1, by + 1, bx + 1 + (int) ((bw - 2) * t), by + BAR_H - 1, barColor);
    }

    /**
     * Top edge of the tier bar. Shared by the drawing pass and the hover test so the two cannot
     * drift: they were separate copies of the same arithmetic, and the bar was drawn straight
     * through the top of the hotbar slots.
     */
    private int barTop(int panelTop) {
        return panelTop + this.imageHeight - BAR_BOTTOM_MARGIN;
    }

    private int barWidth() {
        return this.imageWidth - 2 * BAR_INSET_X;
    }

    /**
     * Both labels, in the material's own ink.
     *
     * <p>Vanilla draws them in {@code #404040}, which was 1.58 : 1 against the old artwork. It is no
     * better against the new one, and neither is the leather palette — see the class note.
     */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, this.title, this.titleLabelX, this.titleLabelY, SKIN.ink(), false);
        graphics.drawString(font, this.playerInventoryTitle,
                this.inventoryLabelX, this.inventoryLabelY, SKIN.muted(), false);
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

        graphics.drawString(font, Component.translatable("wandcraft.bench.flexibility"),
                leftPos + FLEX_INSET_X, topPos + FLEX_ROW_Y - WizardsMetrics.LINE_TIGHT,
                SKIN.muted(), false);
        // The cycler draws only its label plate; its arrows are registered widgets and are already
        // on screen by the time super.render returns.
        flexCycler.renderLabel(graphics);

        renderStatus(graphics);
        renderTierTooltip(graphics, mouseX, mouseY);
    }

    /**
     * Why nothing is coming out, in the band between the picker and the player inventory.
     *
     * <p>An empty output slot used to be the bench's only answer to every failure. It is drawn only
     * while the output <em>is</em> empty: with a wand sitting there the bench has said everything it
     * has to say, and {@code WandItem}'s tooltip carries the rest.
     */
    private void renderStatus(GuiGraphics graphics) {
        if (menu.getBench().getInventory().getAmountAsLong(2) > 0) {
            return;
        }
        Component reason = statusMessage();
        if (reason == null) {
            return;
        }
        GuiText.drawFitted(graphics, font, reason.getString(),
                leftPos + FLEX_INSET_X, topPos + STATUS_Y,
                this.imageWidth - 2 * FLEX_INSET_X, SKIN.muted());
    }

    /**
     * What the tier bar means, as a real tooltip.
     *
     * <p>This was a hand-built box: a {@code #100010} fill, three {@code drawString}s and a manual
     * clamp against both screen edges — a private reimplementation of the thing vanilla already
     * places, wraps and clamps, in a purple that appears nowhere else in the mod.
     */
    private void renderTierTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int bx = leftPos + BAR_INSET_X;
        int by = barTop(topPos);
        int bw = barWidth();
        if (mouseX < bx || mouseX >= bx + bw || mouseY < by || mouseY >= by + BAR_H) {
            return;
        }
        List<Identifier> enhancers = menu.getEnhancerBlockIds();
        graphics.setTooltipForNextFrame(font, List.of(
                Component.translatable("wandcraft.gui.tier_score", menu.getTierScoreScaled() / 100.0f),
                Component.translatable("wandcraft.gui.enhancers", enhancers.size()),
                Component.translatable("wandcraft.gui.tier_hint")
        ), Optional.empty(), mouseX, mouseY);
    }
}
