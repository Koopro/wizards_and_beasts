package at.koopro.wizardsandbeasts.client.trunk.gui;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.widget.CyclerWidget;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedButton;
import at.koopro.wizardsandbeasts.network.trunk.PocketConfigC2SPayload;
import at.koopro.wizardsandbeasts.trunk.PocketBiomes;
import at.koopro.wizardsandbeasts.trunk.gui.PocketConfiguratorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The expansion focus: how big the pocket is, and what it is made of.
 *
 * <p>Two settings and one commit, which is a modal, so it takes {@link WizardsMetrics}'
 * {@code PANEL_MODAL} rather than a size of its own. The menu has <strong>no slots</strong>, so
 * despite being an {@link AbstractContainerScreen} it is not bound to vanilla's 176px grid and is
 * free to be the shape its content wants.
 *
 * <p>It used to draw two flat {@code fill()} rectangles for a frame and hang four vanilla buttons
 * on them labelled {@code "-"}, {@code "+"}, {@code "◄"} and {@code "►"}, typed into
 * {@code Component.literal}. The arrows are a {@link CyclerWidget} now — the same pair drawn from
 * the controls atlas, with real hover and disabled states. The steppers keep a glyph, because
 * {@code −} and {@code +} are symbols rather than English and a locale gains nothing by
 * translating them, but they carry a translatable tooltip: a narrator reading "minus" is the case
 * the literal actually broke.
 *
 * <p>Radius gained a track. It is an integer from {@value #MIN_RADIUS} to {@value #MAX_RADIUS} and
 * the old screen printed it as a bare number, which says what the value is and nothing about how
 * much room is left in either direction.
 */
public class PocketConfiguratorScreen extends AbstractContainerScreen<PocketConfiguratorMenu> {

    /** Smallest and largest pocket the focus will accept. Mirrors the server-side clamp. */
    private static final int MIN_RADIUS = 8;
    private static final int MAX_RADIUS = 32;

    /** Stepper buttons are square and at the themed minimum a nine-slice frame can hold. */
    private static final int STEP_W = 18;
    private static final int ROW_H = 18;
    private static final int APPLY_W = 84;

    private int localRadius;
    private int localBiomeIndex;

    private CyclerWidget<String> biomeCycler;

    public PocketConfiguratorScreen(PocketConfiguratorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = WizardsMetrics.PANEL_MODAL_W;
        this.imageHeight = WizardsMetrics.PANEL_MODAL_H;
    }

    @Override
    protected void init() {
        super.init();
        localRadius = Mth.clamp(menu.getRadius(), MIN_RADIUS, MAX_RADIUS);
        localBiomeIndex = menu.getBiomeIndex();
        rebuild();
    }

    /**
     * Rebuilds every control.
     *
     * <p>{@link CyclerWidget} owns an index rather than a live binding, so a screen that changes the
     * selection out from under it has to hand it a new one — the same rebuild-on-change the heritage
     * gate does. Cheap here: five widgets.
     */
    private void rebuild() {
        clearWidgets();
        int left = leftPos;
        int top = topPos;
        int inner = imageWidth - 2 * WizardsMetrics.SPACE_XL;
        int contentX = left + WizardsMetrics.SPACE_XL;

        int radiusY = top + radiusRowY();
        addRenderableWidget(stepper(contentX, radiusY, "−", "decrease", -1));
        addRenderableWidget(stepper(contentX + inner - STEP_W, radiusY, "+", "increase", 1));

        biomeCycler = new CyclerWidget<>(PocketBiomes.SELECTABLE,
                PocketBiomes.SELECTABLE.get(localBiomeIndex),
                PocketBiomes::displayName,
                key -> {
                    localBiomeIndex = PocketBiomes.SELECTABLE.indexOf(key);
                    rebuild();
                });
        biomeCycler.setBounds(contentX, top + biomeRowY(), inner, ROW_H);
        biomeCycler.buttons().forEach(this::addRenderableWidget);

        addRenderableWidget(new ThemedButton(
                left + (imageWidth - APPLY_W) / 2, top + imageHeight - WizardsMetrics.SPACE_XXL - 2,
                APPLY_W, ROW_H,
                Component.translatable("gui.wizards_and_beasts.pocket_configurator.apply"),
                this::applyAndClose).tone(McStylePanel.ButtonTone.CONFIRM));
    }

    /** A radius stepper: a symbol to look at, a translated phrase for the narrator. */
    private ThemedButton stepper(int x, int y, String glyph, String key, int delta) {
        ThemedButton button = new ThemedButton(x, y, STEP_W, ROW_H, Component.literal(glyph),
                () -> setRadius(localRadius + delta));
        button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("gui.wizards_and_beasts.pocket_configurator." + key)));
        return button;
    }

    private void setRadius(int value) {
        localRadius = Mth.clamp(value, MIN_RADIUS, MAX_RADIUS);
    }

    private void applyAndClose() {
        String biomeKey = PocketBiomes.SELECTABLE.get(localBiomeIndex);
        ClientPacketDistributor.sendToServer(
                new PocketConfigC2SPayload(menu.getConfigurator().getBlockPos(), localRadius, biomeKey));
        onClose();
    }

    // Row origins, in panel-relative pixels, on the 4pt scale. Shared by init and render so the
    // label and the control it belongs to cannot drift apart.
    private static int radiusRowY() {
        return WizardsMetrics.LINE_TITLE + WizardsMetrics.SPACE_XL + WizardsMetrics.LINE_BODY;
    }

    private static int biomeRowY() {
        return radiusRowY() + ROW_H + WizardsMetrics.SPACE_XL + WizardsMetrics.LINE_BODY;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        McStylePanel.drawThemedPanel(g, leftPos, topPos, imageWidth, imageHeight);
        McStylePanel.drawDivider(g, leftPos + WizardsMetrics.SPACE_M,
                topPos + WizardsMetrics.LINE_TITLE - WizardsMetrics.DIVIDER_H / 2,
                imageWidth - 2 * WizardsMetrics.SPACE_M);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);

        int contentX = leftPos + WizardsMetrics.SPACE_XL;
        int inner = imageWidth - 2 * WizardsMetrics.SPACE_XL;

        g.drawCenteredString(font,
                Component.translatable("gui.wizards_and_beasts.pocket_configurator.title"),
                leftPos + imageWidth / 2, topPos + WizardsMetrics.SPACE_M, WizardsPalette.BRASS_HI);

        int radiusY = topPos + radiusRowY();
        g.drawString(font, Component.translatable("gui.wizards_and_beasts.pocket_configurator.size"),
                contentX, radiusY - WizardsMetrics.LINE_BODY, WizardsPalette.TEXT_DIM, false);
        renderRadiusTrack(g, contentX + STEP_W + WizardsMetrics.SPACE_M, radiusY,
                inner - 2 * (STEP_W + WizardsMetrics.SPACE_M));

        int biomeY = topPos + biomeRowY();
        g.drawString(font, Component.translatable("gui.wizards_and_beasts.pocket_configurator.biome"),
                contentX, biomeY - WizardsMetrics.LINE_BODY, WizardsPalette.TEXT_DIM, false);
        // The cycler draws only its label panel; its two arrows are registered widgets and have
        // already been drawn by super.render above.
        biomeCycler.renderLabel(g);
    }

    /**
     * The radius, as a filled track with the value written on it.
     *
     * <p>Drawn on the recessed sprite rather than a {@code fill()} pair, so the bar sits in the
     * panel the way every other well in the mod does. The number stays: a track says "near the
     * top of its range" and a player setting a pocket to match a build wants the integer.
     */
    private void renderRadiusTrack(GuiGraphics g, int x, int y, int w) {
        McStylePanel.drawThemedInset(g, x, y, w, ROW_H);
        int span = MAX_RADIUS - MIN_RADIUS;
        int fillW = (w - 2 * WizardsMetrics.SPACE_S) * (localRadius - MIN_RADIUS) / span;
        if (fillW > 0) {
            g.fill(x + WizardsMetrics.SPACE_S, y + WizardsMetrics.SPACE_S,
                    x + WizardsMetrics.SPACE_S + fillW, y + ROW_H - WizardsMetrics.SPACE_S,
                    WizardsPalette.BRASS);
        }
        g.drawCenteredString(font,
                Component.translatable("gui.wizards_and_beasts.pocket_configurator.radius", localRadius),
                x + w / 2, y + (ROW_H - font.lineHeight) / 2 + 1, WizardsPalette.BRASS_HI);
    }

    /** Vanilla's two labels are wrong here: there is no inventory, and the title is drawn centred. */
    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
    }
}
