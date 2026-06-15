package at.koopro.wizardsandbeasts.client.trinket.gui;

import at.koopro.wizardsandbeasts.network.trinket.MirrorConnectC2SPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.UUID;

/** "Speak the other's name" — text input that opens a two-way-mirror connection. */
public class MirrorCallScreen extends Screen {

    private final UUID pairId;
    private EditBox nameField;

    public MirrorCallScreen(UUID pairId) {
        super(Component.literal("Two-Way Mirror"));
        this.pairId = pairId;
    }

    @Override
    protected void init() {
        super.init();
        int cx = width / 2;
        int cy = height / 2;
        nameField = new EditBox(font, cx - 100, cy - 6, 200, 20, Component.literal("name"));
        nameField.setMaxLength(40);
        nameField.setHint(Component.literal("Speak their name…"));
        addRenderableWidget(nameField);
        setInitialFocus(nameField);

        addRenderableWidget(Button.builder(Component.literal("Call"), b -> connect())
                .bounds(cx - 100, cy + 22, 96, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(cx + 4, cy + 22, 96, 20).build());
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
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, "§5Hold the mirror and speak a name",
                width / 2, height / 2 - 30, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
