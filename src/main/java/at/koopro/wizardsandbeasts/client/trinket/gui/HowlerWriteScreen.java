package at.koopro.wizardsandbeasts.client.trinket.gui;

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
    private static final int PANEL_H = 150;
    /** The open red envelope with its notepaper card drawn half out (tools/canon_gui.py). */
    private static final net.minecraft.resources.Identifier ENVELOPE = net.minecraft.resources.Identifier
            .fromNamespaceAndPath(at.koopro.wizardsandbeasts.WizardsAndBeastsMod.MODID, "textures/gui/artefact/howler/envelope.png");
    /** Iron-gall ink on the card. */
    private static final int CARD_INK = 0xFF2A2118;
    private static final int CARD_HINT = 0xFF7A6A52;
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

        // Written straight on the card: no box, just the ink.
        field = new ThemedTextField(font, px + layout.s(36), py + layout.s(52),
                layout.s(168), Math.max(MIN_SKINNED, layout.s(16)),
                Component.translatable(KEY + "field"))
                .bare(CARD_INK, CARD_HINT, CARD_INK)
                .inkHint(Component.translatable(KEY + "hint"));
        field.setMaxLength(HowlerItem.MAX_MESSAGE);
        field.setResponder(text -> refresh());
        addRenderableWidget(field);
        setFocused(field);
        field.setFocused(true);

        // Either side of the broken wax seal, on the envelope.
        int btnH = Math.max(MIN_SKINNED, layout.s(18));
        int btnW = layout.s(76);
        int btnY = py + layout.s(110);
        seal = addRenderableWidget(ThemedButton.skinned(px + layout.s(22), btnY, btnW, btnH,
                Component.translatable(KEY + "seal"), this::onSeal, SKIN));
        addRenderableWidget(ThemedButton.skinned(px + panelW - layout.s(22) - btnW, btnY, btnW, btnH,
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
        graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, ENVELOPE, px, py, 0.0F, 0.0F,
                panelW, layout.panelH(), PANEL_W, PANEL_H, PANEL_W, PANEL_H);
        graphics.drawString(font, title, px + panelW / 2 - font.width(title) / 2, py + layout.s(20), CARD_INK, false);
        graphics.drawString(font, Component.translatable(KEY + "prompt"), px + layout.s(36), py + layout.s(38),
                CARD_INK, false);
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
