package at.koopro.wizardsandbeasts.client.heritage.gui;

import at.koopro.wizardsandbeasts.client.gui.ScreenLayoutScaler;
import at.koopro.wizardsandbeasts.client.gui.WizardsAndBeastsUiTokens;
import at.koopro.wizardsandbeasts.network.heritage.HeritageSelectC2SPayload;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import javax.annotation.Nullable;

public class HeritageSelectionScreen extends Screen {

    private enum Phase { HERITAGE_LIST, VARIANT_LIST, CONFIRMATION }

    private static final int PANEL_W = WizardsAndBeastsUiTokens.HeritageSelection.PANEL_WIDTH;
    private static final int PANEL_H = WizardsAndBeastsUiTokens.HeritageSelection.PANEL_HEIGHT;

    private Phase phase = Phase.HERITAGE_LIST;
    @Nullable private Heritage selectedHeritage;
    @Nullable private HeritageVariant selectedHeritageVariant;
    private int typeIndex = 0;
    private ScreenLayoutScaler layout;

    public HeritageSelectionScreen() {
        super(Component.literal("Choose Your Hogwarts Heritage"));
    }

    @Override
    protected void init() {
        super.init();
        layout = ScreenLayoutScaler.forScreen(width, height, PANEL_W, PANEL_H);
        rebuild();
    }

    private void rebuild() {
        clearWidgets();

        int px = layout.panelX();
        int py = layout.panelY();

        switch (phase) {
            case HERITAGE_LIST -> buildTypeList(px, py);
            case VARIANT_LIST -> buildSubtypeList(px, py);
            case CONFIRMATION -> buildConfirmation(px, py);
        }
    }

    // ── Type List ────────────────────────────────────────────────────────

    private void buildTypeList(int px, int py) {
        Heritage[] types = Heritage.values();
        if (selectedHeritage == null) {
            selectedHeritage = types[typeIndex];
        }
        typeIndex = indexOfType(selectedHeritage);

        int arrowY = py + layout.s(WizardsAndBeastsUiTokens.HeritageSelection.ARROW_Y_OFFSET);
        int arrowW = layout.s(WizardsAndBeastsUiTokens.HeritageSelection.ARROW_BUTTON_W);
        int arrowH = layout.s(WizardsAndBeastsUiTokens.HeritageSelection.ARROW_BUTTON_H);
        int arrowGap = layout.s(WizardsAndBeastsUiTokens.HeritageSelection.ARROW_GAP);
        int leftX = px - arrowW - arrowGap;
        int rightX = px + layout.panelW() + arrowGap;

        addRenderableWidget(Button.builder(
                Component.literal("<"),
                btn -> cycleType(-1)
        ).bounds(leftX, arrowY, arrowW, arrowH).build());

        addRenderableWidget(Button.builder(
                Component.literal(">"),
                btn -> cycleType(1)
        ).bounds(rightX, arrowY, arrowW, arrowH).build());

        Button selectButton = Button.builder(
                Component.literal("Choose"),
                btn -> {
                    if (selectedHeritage == null || !selectedHeritage.isAlphaAvailable()) {
                        return;
                    }
                    selectedHeritageVariant = null;
                    if (selectedHeritage != null && selectedHeritage.getSubtypes().size() == 1) {
                        HeritageVariant onlyVariant = selectedHeritage.getSubtypes().get(0);
                        ClientPacketDistributor.sendToServer(
                                new HeritageSelectC2SPayload(selectedHeritage.getId(), onlyVariant.getId()));
                        onClose();
                    } else {
                        phase = Phase.VARIANT_LIST;
                        rebuild();
                    }
                }
        ).bounds(px + (layout.panelW() - layout.s(WizardsAndBeastsUiTokens.HeritageSelection.SELECT_BUTTON_W)) / 2,
                py + layout.panelH() + layout.s(WizardsAndBeastsUiTokens.HeritageSelection.SELECT_BUTTON_BOTTOM_GAP),
                layout.s(WizardsAndBeastsUiTokens.HeritageSelection.SELECT_BUTTON_W),
                layout.s(WizardsAndBeastsUiTokens.HeritageSelection.SELECT_BUTTON_H)).build();
        selectButton.active = selectedHeritage != null && selectedHeritage.isAlphaAvailable();
        addRenderableWidget(selectButton);
    }

    private int indexOfType(@Nullable Heritage type) {
        Heritage[] types = Heritage.values();
        if (type == null) return 0;
        for (int i = 0; i < types.length; i++) {
            if (types[i] == type) return i;
        }
        return 0;
    }

    private void cycleType(int dir) {
        Heritage[] types = Heritage.values();
        typeIndex = (typeIndex + dir + types.length) % types.length;
        selectedHeritage = types[typeIndex];
        selectedHeritageVariant = null;
        rebuild();
    }

    // ── Subtype List ─────────────────────────────────────────────────────

    private void buildSubtypeList(int px, int py) {
        if (selectedHeritage == null) return;

        int listX = px + layout.s(WizardsAndBeastsUiTokens.HeritageSelection.LIST_X);
        int listY = py + layout.s(WizardsAndBeastsUiTokens.HeritageSelection.SUBTYPE_LIST_Y);
        int btnW = layout.panelW() - layout.s(WizardsAndBeastsUiTokens.HeritageSelection.PANEL_SIDE_PADDING);
        int btnH = layout.s(WizardsAndBeastsUiTokens.HeritageSelection.LIST_BUTTON_HEIGHT);

        var subtypes = selectedHeritage.getSubtypes();
        for (int i = 0; i < subtypes.size(); i++) {
            final HeritageVariant sub = subtypes.get(i);
            int y = listY + i * (btnH + layout.s(WizardsAndBeastsUiTokens.HeritageSelection.LIST_BUTTON_GAP));

            addRenderableWidget(Button.builder(
                    Component.literal(sub.getDisplayName()),
                    btn -> {
                        selectedHeritageVariant = sub;
                        phase = Phase.CONFIRMATION;
                        rebuild();
                    }
            ).bounds(listX, y, btnW, btnH).build());
        }

        // Back button
        addRenderableWidget(Button.builder(
                Component.literal("Return"),
                btn -> {
                    selectedHeritage = null;
                    phase = Phase.HERITAGE_LIST;
                    rebuild();
                }
        ).bounds(px + layout.s(WizardsAndBeastsUiTokens.HeritageSelection.LIST_X),
                py + layout.panelH() - layout.s(WizardsAndBeastsUiTokens.HeritageSelection.BOTTOM_BUTTON_OFFSET),
                layout.s(WizardsAndBeastsUiTokens.HeritageSelection.BACK_BUTTON_WIDTH),
                layout.s(WizardsAndBeastsUiTokens.HeritageSelection.BACK_BUTTON_HEIGHT)).build());
    }

    // ── Confirmation ─────────────────────────────────────────────────────

    private void buildConfirmation(int px, int py) {
        if (selectedHeritage == null || selectedHeritageVariant == null) return;

        // Confirm
        addRenderableWidget(Button.builder(
                Component.literal("Confirm Oath"),
                btn -> {
                    ClientPacketDistributor.sendToServer(
                            new HeritageSelectC2SPayload(selectedHeritage.getId(), selectedHeritageVariant.getId()));
                    onClose();
                }
        ).bounds(px + layout.panelW() / 2 + layout.s(WizardsAndBeastsUiTokens.HeritageSelection.CONFIRM_BUTTON_GAP),
                py + layout.panelH() - layout.s(WizardsAndBeastsUiTokens.HeritageSelection.BOTTOM_BUTTON_OFFSET),
                layout.s(WizardsAndBeastsUiTokens.HeritageSelection.CONFIRM_BUTTON_WIDTH),
                layout.s(WizardsAndBeastsUiTokens.HeritageSelection.CONFIRM_BUTTON_HEIGHT)).build());

        // Back
        addRenderableWidget(Button.builder(
                Component.literal("Return"),
                btn -> {
                    selectedHeritageVariant = null;
                    phase = Phase.VARIANT_LIST;
                    rebuild();
                }
        ).bounds(px + layout.panelW() / 2 - (layout.s(WizardsAndBeastsUiTokens.HeritageSelection.CONFIRM_BUTTON_WIDTH) + layout.s(WizardsAndBeastsUiTokens.HeritageSelection.CONFIRM_BUTTON_GAP)),
                py + layout.panelH() - layout.s(WizardsAndBeastsUiTokens.HeritageSelection.BOTTOM_BUTTON_OFFSET),
                layout.s(WizardsAndBeastsUiTokens.HeritageSelection.CONFIRM_BUTTON_WIDTH),
                layout.s(WizardsAndBeastsUiTokens.HeritageSelection.CONFIRM_BUTTON_HEIGHT)).build());
    }

    // ── Rendering ────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderMenuBackground(g);
        int px = layout.panelX();
        int py = layout.panelY();

        switch (phase) {
            case HERITAGE_LIST -> HeritageSelectionRenderHelper.renderTypeCard(g, font, px, py, layout.panelW(), layout.panelH(), selectedHeritage, layout);
            case VARIANT_LIST -> HeritageSelectionRenderHelper.renderSubtypeList(g, font, px, py, layout.panelW(), selectedHeritage, layout);
            case CONFIRMATION -> HeritageSelectionRenderHelper.renderConfirmation(g, font, px, py, layout.panelW(), layout.panelH(), selectedHeritage, selectedHeritageVariant, layout);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    // ── Input handling ───────────────────────────────────────────────────

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) { // ESC
            if (phase == Phase.CONFIRMATION) {
                selectedHeritageVariant = null;
                phase = Phase.VARIANT_LIST;
                rebuild();
                return true;
            } else if (phase == Phase.VARIANT_LIST) {
                selectedHeritage = null;
                phase = Phase.HERITAGE_LIST;
                rebuild();
                return true;
            }
            // Block ESC in HERITAGE_LIST — cannot dismiss
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
