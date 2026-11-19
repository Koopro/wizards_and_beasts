package at.koopro.wizardsandbeasts.client.trinket.gui;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette.GuiSkin;
import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedButton;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedTextField;
import at.koopro.wizardsandbeasts.item.trinket.HowlerItem;
import at.koopro.wizardsandbeasts.network.trinket.HowlerSealC2SPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

/**
 * Writing a Howler: one line, sealed with the writer's name. The letter does the shouting later.
 */
public class HowlerWriteScreen extends Screen {

    private static final int PANEL_W = 240;
    private static final int PANEL_H = 120;
    private static final GuiSkin SKIN = GuiSkin.HOWLER;
    private static final int MIN_SKINNED = 2 * WizardsMetrics.PANEL_SPRITE_BORDER + 2;
    private static final String KEY = "gui.wizards_and_beasts.howler.";

    private EditBox field;
    private ThemedButton seal;
    private GuiScaleHelper.Layout layout;

    public HowlerWriteScreen() {
        super(Component.translatable(KEY + "title"));
    }

    @Override
    protected void init() {
        super.init();
        layout = GuiScaleHelper.Layout.panel(width, height, PANEL_W, PANEL_H);
        int px = layout.panelX();
        int py = layout.panelY();
        int panelW = layout.panelW();
        int panelH = layout.panelH();

        field = new ThemedTextField(font, px + layout.s(12), py + layout.s(44),
                panelW - layout.s(24), Math.max(MIN_SKINNED, layout.s(18)),
                Component.translatable(KEY + "field"))
                .inkHint(Component.translatable(KEY + "hint"))
                .skin(SKIN);
        field.setMaxLength(HowlerItem.MAX_MESSAGE);
        field.setResponder(text -> refresh());
        addRenderableWidget(field);
        setFocused(field);
        field.setFocused(true);

        int btnH = Math.max(MIN_SKINNED, layout.s(18));
        int btnW = layout.s(84);
        int btnY = py + panelH - layout.s(12) - btnH;
        seal = addRenderableWidget(ThemedButton.skinned(px + panelW / 2 - btnW - layout.s(4), btnY, btnW, btnH,
                Component.translatable(KEY + "seal"), this::onSeal, SKIN));
        addRenderableWidget(ThemedButton.skinned(px + panelW / 2 + layout.s(4), btnY, btnW, btnH,
                Component.translatable("gui.cancel"), this::onClose, SKIN));
        refresh();
    }

    private void refresh() {
        if (seal != null && field != null) {
            seal.active = !field.getValue().isBlank();
        }
    }

    @Override
    public void render(@NonNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderMenuBackground(graphics);
        int px = layout.panelX();
        int py = layout.panelY();
        int panelW = layout.panelW();
        McStylePanel.drawSkinPanel(graphics, SKIN, px, py, panelW, layout.panelH());
        graphics.drawString(font, title, px + panelW / 2 - font.width(title) / 2, py + layout.s(10), SKIN.ink(), false);
        McStylePanel.drawSkinDivider(graphics, SKIN, px + layout.s(12), py + layout.s(18), panelW - layout.s(24));
        graphics.drawString(font, Component.translatable(KEY + "prompt"), px + layout.s(12), py + layout.s(32),
                SKIN.ink(), false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && seal != null && seal.active) {
            onSeal();
            return true;
        }
        return super.keyPressed(event);
    }

    private void onSeal() {
        String text = field.getValue().trim();
        if (!text.isEmpty()) {
            ClientPacketDistributor.sendToServer(new HowlerSealC2SPayload(text));
        }
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
