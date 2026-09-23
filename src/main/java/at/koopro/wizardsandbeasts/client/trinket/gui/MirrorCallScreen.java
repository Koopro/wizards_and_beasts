package at.koopro.wizardsandbeasts.client.trinket.gui;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedButton;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedTextField;
import at.koopro.wizardsandbeasts.network.trinket.MirrorConnectC2SPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.UUID;

/**
 * "Speak the other's name" — text input that opens a two-way-mirror connection.
 *
 * <p>A prompt, not the mirror itself, so it is a parchment modal like every other menu. The glass
 * is {@link MirrorViewScreen}, which opens once the other side answers.
 */
public class MirrorCallScreen extends Screen {

    private static final int PANEL_W = WizardsMetrics.PANEL_MODAL_W;
    private static final int PANEL_H = WizardsMetrics.PANEL_MODAL_H;
    /** Clearance from the sheet's edge: its double rule runs 4-6px in. */
    private static final int EDGE = WizardsMetrics.SPACE_L;
    private static final int TITLE_Y = 10;
    private static final int TITLE_RULE_Y = 18;
    private static final int PROMPT_Y = 36;
    private static final int FIELD_Y = 54;
    private static final int FIELD_H = 18;
    private static final int BUTTON_H = 20;
    /** Where the borderless field's text sits inside its well. */

    private final UUID pairId;
    private ThemedTextField nameField;
    private int panelX;
    private int panelY;

    public MirrorCallScreen(UUID pairId) {
        super(Component.literal("Two-Way Mirror"));
        this.pairId = pairId;
    }

    @Override
    protected void init() {
        super.init();
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        int innerW = PANEL_W - 2 * EDGE;

        nameField = new ThemedTextField(font, panelX + EDGE, panelY + FIELD_Y, innerW, FIELD_H,
                Component.literal("name")).inkHint(Component.literal("Speak their name…"));
        nameField.setMaxLength(40);
        addRenderableWidget(nameField);
        setInitialFocus(nameField);

        int buttonW = (innerW - WizardsMetrics.SPACE_M) / 2;
        int buttonY = panelY + PANEL_H - EDGE - BUTTON_H;
        addRenderableWidget(new ThemedButton(panelX + EDGE, buttonY, buttonW, BUTTON_H,
                Component.literal("Call"), this::connect));
        addRenderableWidget(new ThemedButton(panelX + PANEL_W - EDGE - buttonW, buttonY, buttonW,
                BUTTON_H, Component.literal("Cancel"), this::onClose));
    }

    private void connect() {
        String name = nameField.getValue().trim();
        if (name.isEmpty()) {
            return;
        }
        ClientPacketDistributor.sendToServer(new MirrorConnectC2SPayload(pairId, name));
        // The server replies with a mirror_open (success) or a chat note (failure); close this prompt.
        onClose();
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER && nameField.isFocused()) {
            connect();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        McStylePanel.drawThemedPanel(graphics, panelX, panelY, PANEL_W, PANEL_H);
        graphics.drawString(font, this.title, panelX + EDGE, panelY + TITLE_Y,
                WizardsPalette.PAGE_INK, false);
        McStylePanel.drawDivider(graphics, panelX + EDGE, panelY + TITLE_RULE_Y, PANEL_W - 2 * EDGE);
        graphics.drawString(font, "Hold the mirror and speak a name", panelX + EDGE,
                panelY + PROMPT_Y, WizardsPalette.PAGE_INK_2, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
