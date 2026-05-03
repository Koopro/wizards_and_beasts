package at.koopro.wizardsandbeasts.client.gui;

import at.koopro.wizardsandbeasts.network.TypeSelectC2SPacket;
import at.koopro.wizardsandbeasts.type.WizSubtype;
import at.koopro.wizardsandbeasts.type.WizType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import javax.annotation.Nullable;

public class TypeSelectionScreen extends Screen {

    private enum Phase { TYPE_LIST, SUBTYPE_LIST, CONFIRMATION }

    private static final int PANEL_W = WizardsAndBeastsUiTokens.TypeSelection.PANEL_WIDTH;
    private static final int PANEL_H = WizardsAndBeastsUiTokens.TypeSelection.PANEL_HEIGHT;

    private Phase phase = Phase.TYPE_LIST;
    @Nullable private WizType selectedType;
    @Nullable private WizSubtype selectedSubtype;
    private int typeIndex = 0;
    private ScreenLayoutScaler layout;

    public TypeSelectionScreen() {
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
            case TYPE_LIST -> buildTypeList(px, py);
            case SUBTYPE_LIST -> buildSubtypeList(px, py);
            case CONFIRMATION -> buildConfirmation(px, py);
        }
    }

    // ── Type List ────────────────────────────────────────────────────────

    private void buildTypeList(int px, int py) {
        WizType[] types = WizType.values();
        if (selectedType == null) {
            selectedType = types[typeIndex];
        }
        typeIndex = indexOfType(selectedType);

        int arrowY = py + layout.s(WizardsAndBeastsUiTokens.TypeSelection.ARROW_Y_OFFSET);
        int arrowW = layout.s(WizardsAndBeastsUiTokens.TypeSelection.ARROW_BUTTON_W);
        int arrowH = layout.s(WizardsAndBeastsUiTokens.TypeSelection.ARROW_BUTTON_H);
        int arrowGap = layout.s(WizardsAndBeastsUiTokens.TypeSelection.ARROW_GAP);
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
                    if (selectedType == null || !selectedType.isAlphaAvailable()) {
                        return;
                    }
                    selectedSubtype = null;
                    if (selectedType != null && selectedType.getSubtypes().size() == 1) {
                        WizSubtype onlySubtype = selectedType.getSubtypes().get(0);
                        ClientPacketDistributor.sendToServer(
                                new TypeSelectC2SPacket(selectedType.getId(), onlySubtype.getId()));
                        onClose();
                    } else {
                        phase = Phase.SUBTYPE_LIST;
                        rebuild();
                    }
                }
        ).bounds(px + (layout.panelW() - layout.s(WizardsAndBeastsUiTokens.TypeSelection.SELECT_BUTTON_W)) / 2,
                py + layout.panelH() + layout.s(WizardsAndBeastsUiTokens.TypeSelection.SELECT_BUTTON_BOTTOM_GAP),
                layout.s(WizardsAndBeastsUiTokens.TypeSelection.SELECT_BUTTON_W),
                layout.s(WizardsAndBeastsUiTokens.TypeSelection.SELECT_BUTTON_H)).build();
        selectButton.active = selectedType != null && selectedType.isAlphaAvailable();
        addRenderableWidget(selectButton);
    }

    private int indexOfType(@Nullable WizType type) {
        WizType[] types = WizType.values();
        if (type == null) return 0;
        for (int i = 0; i < types.length; i++) {
            if (types[i] == type) return i;
        }
        return 0;
    }

    private void cycleType(int dir) {
        WizType[] types = WizType.values();
        typeIndex = (typeIndex + dir + types.length) % types.length;
        selectedType = types[typeIndex];
        selectedSubtype = null;
        rebuild();
    }

    // ── Subtype List ─────────────────────────────────────────────────────

    private void buildSubtypeList(int px, int py) {
        if (selectedType == null) return;

        int listX = px + layout.s(WizardsAndBeastsUiTokens.TypeSelection.LIST_X);
        int listY = py + layout.s(WizardsAndBeastsUiTokens.TypeSelection.SUBTYPE_LIST_Y);
        int btnW = layout.panelW() - layout.s(WizardsAndBeastsUiTokens.TypeSelection.PANEL_SIDE_PADDING);
        int btnH = layout.s(WizardsAndBeastsUiTokens.TypeSelection.LIST_BUTTON_HEIGHT);

        var subtypes = selectedType.getSubtypes();
        for (int i = 0; i < subtypes.size(); i++) {
            final WizSubtype sub = subtypes.get(i);
            int y = listY + i * (btnH + layout.s(WizardsAndBeastsUiTokens.TypeSelection.LIST_BUTTON_GAP));

            addRenderableWidget(Button.builder(
                    Component.literal(sub.getDisplayName()),
                    btn -> {
                        selectedSubtype = sub;
                        phase = Phase.CONFIRMATION;
                        rebuild();
                    }
            ).bounds(listX, y, btnW, btnH).build());
        }

        // Back button
        addRenderableWidget(Button.builder(
                Component.literal("Return"),
                btn -> {
                    selectedType = null;
                    phase = Phase.TYPE_LIST;
                    rebuild();
                }
        ).bounds(px + layout.s(WizardsAndBeastsUiTokens.TypeSelection.LIST_X),
                py + layout.panelH() - layout.s(WizardsAndBeastsUiTokens.TypeSelection.BOTTOM_BUTTON_OFFSET),
                layout.s(WizardsAndBeastsUiTokens.TypeSelection.BACK_BUTTON_WIDTH),
                layout.s(WizardsAndBeastsUiTokens.TypeSelection.BACK_BUTTON_HEIGHT)).build());
    }

    // ── Confirmation ─────────────────────────────────────────────────────

    private void buildConfirmation(int px, int py) {
        if (selectedType == null || selectedSubtype == null) return;

        // Confirm
        addRenderableWidget(Button.builder(
                Component.literal("Confirm Oath"),
                btn -> {
                    ClientPacketDistributor.sendToServer(
                            new TypeSelectC2SPacket(selectedType.getId(), selectedSubtype.getId()));
                    onClose();
                }
        ).bounds(px + layout.panelW() / 2 + layout.s(WizardsAndBeastsUiTokens.TypeSelection.CONFIRM_BUTTON_GAP),
                py + layout.panelH() - layout.s(WizardsAndBeastsUiTokens.TypeSelection.BOTTOM_BUTTON_OFFSET),
                layout.s(WizardsAndBeastsUiTokens.TypeSelection.CONFIRM_BUTTON_WIDTH),
                layout.s(WizardsAndBeastsUiTokens.TypeSelection.CONFIRM_BUTTON_HEIGHT)).build());

        // Back
        addRenderableWidget(Button.builder(
                Component.literal("Return"),
                btn -> {
                    selectedSubtype = null;
                    phase = Phase.SUBTYPE_LIST;
                    rebuild();
                }
        ).bounds(px + layout.panelW() / 2 - (layout.s(WizardsAndBeastsUiTokens.TypeSelection.CONFIRM_BUTTON_WIDTH) + layout.s(WizardsAndBeastsUiTokens.TypeSelection.CONFIRM_BUTTON_GAP)),
                py + layout.panelH() - layout.s(WizardsAndBeastsUiTokens.TypeSelection.BOTTOM_BUTTON_OFFSET),
                layout.s(WizardsAndBeastsUiTokens.TypeSelection.CONFIRM_BUTTON_WIDTH),
                layout.s(WizardsAndBeastsUiTokens.TypeSelection.CONFIRM_BUTTON_HEIGHT)).build());
    }

    // ── Rendering ────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderMenuBackground(g);
        int px = layout.panelX();
        int py = layout.panelY();

        switch (phase) {
            case TYPE_LIST -> TypeSelectionRenderHelper.renderTypeCard(g, font, px, py, layout.panelW(), layout.panelH(), selectedType, layout);
            case SUBTYPE_LIST -> TypeSelectionRenderHelper.renderSubtypeList(g, font, px, py, layout.panelW(), selectedType, layout);
            case CONFIRMATION -> TypeSelectionRenderHelper.renderConfirmation(g, font, px, py, layout.panelW(), layout.panelH(), selectedType, selectedSubtype, layout);
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
                selectedSubtype = null;
                phase = Phase.SUBTYPE_LIST;
                rebuild();
                return true;
            } else if (phase == Phase.SUBTYPE_LIST) {
                selectedType = null;
                phase = Phase.TYPE_LIST;
                rebuild();
                return true;
            }
            // Block ESC in TYPE_LIST — cannot dismiss
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
