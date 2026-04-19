package at.koopro.neo.client.gui;

import at.koopro.neo.network.TypeSelectC2SPacket;
import at.koopro.neo.type.WizSubtype;
import at.koopro.neo.type.WizType;
import at.koopro.neo.util.GuiUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import javax.annotation.Nullable;

public class TypeSelectionScreen extends Screen {

    private enum Phase { TYPE_LIST, SUBTYPE_LIST, CONFIRMATION }

    private static final int PANEL_W = 360;
    private static final int PANEL_H = 280;

    private Phase phase = Phase.TYPE_LIST;
    @Nullable private WizType selectedType;
    @Nullable private WizSubtype selectedSubtype;

    public TypeSelectionScreen() {
        super(Component.literal("Choose Your Heritage"));
    }

    @Override
    protected void init() {
        super.init();
        rebuild();
    }

    private void rebuild() {
        clearWidgets();

        int px = (width - PANEL_W) / 2;
        int py = (height - PANEL_H) / 2;

        switch (phase) {
            case TYPE_LIST -> buildTypeList(px, py);
            case SUBTYPE_LIST -> buildSubtypeList(px, py);
            case CONFIRMATION -> buildConfirmation(px, py);
        }
    }

    // ── Type List ────────────────────────────────────────────────────────

    private void buildTypeList(int px, int py) {
        int listX = px + 10;
        int listY = py + 30;
        int btnW = PANEL_W - 20;
        int btnH = 22;

        WizType[] types = WizType.values();
        for (int i = 0; i < types.length; i++) {
            final WizType type = types[i];
            int y = listY + i * (btnH + 2);

            addRenderableWidget(Button.builder(
                    Component.literal(type.getDisplayName()),
                    btn -> {
                        selectedType = type;
                        selectedSubtype = null;
                        phase = Phase.SUBTYPE_LIST;
                        rebuild();
                    }
            ).bounds(listX, y, btnW, btnH).build());
        }
    }

    // ── Subtype List ─────────────────────────────────────────────────────

    private void buildSubtypeList(int px, int py) {
        if (selectedType == null) return;

        int listX = px + 10;
        int listY = py + 100;
        int btnW = PANEL_W - 20;
        int btnH = 22;

        var subtypes = selectedType.getSubtypes();
        for (int i = 0; i < subtypes.size(); i++) {
            final WizSubtype sub = subtypes.get(i);
            int y = listY + i * (btnH + 2);

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
                Component.literal("Back"),
                btn -> {
                    selectedType = null;
                    phase = Phase.TYPE_LIST;
                    rebuild();
                }
        ).bounds(px + 10, py + PANEL_H - 30, 60, 20).build());
    }

    // ── Confirmation ─────────────────────────────────────────────────────

    private void buildConfirmation(int px, int py) {
        if (selectedType == null || selectedSubtype == null) return;

        // Confirm
        addRenderableWidget(Button.builder(
                Component.literal("Confirm"),
                btn -> {
                    ClientPacketDistributor.sendToServer(
                            new TypeSelectC2SPacket(selectedType.getId(), selectedSubtype.getId()));
                    onClose();
                }
        ).bounds(px + PANEL_W / 2 + 5, py + PANEL_H - 30, 80, 20).build());

        // Back
        addRenderableWidget(Button.builder(
                Component.literal("Back"),
                btn -> {
                    selectedSubtype = null;
                    phase = Phase.SUBTYPE_LIST;
                    rebuild();
                }
        ).bounds(px + PANEL_W / 2 - 85, py + PANEL_H - 30, 80, 20).build());
    }

    // ── Rendering ────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int px = (width - PANEL_W) / 2;
        int py = (height - PANEL_H) / 2;

        g.fill(px, py, px + PANEL_W, py + PANEL_H, TypeSelectionRenderHelper.COL_BG);

        GuiUtils.drawBorder(g, px, py, PANEL_W, PANEL_H, TypeSelectionRenderHelper.COL_BORDER);

        switch (phase) {
            case TYPE_LIST -> TypeSelectionRenderHelper.renderTypeList(g, font, px, py, PANEL_W, PANEL_H, mouseX, mouseY);
            case SUBTYPE_LIST -> TypeSelectionRenderHelper.renderSubtypeList(g, font, px, py, PANEL_W, selectedType);
            case CONFIRMATION -> TypeSelectionRenderHelper.renderConfirmation(g, font, px, py, PANEL_W, PANEL_H, selectedType, selectedSubtype);
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
