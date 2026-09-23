package at.koopro.wizardsandbeasts.client.floo.gui;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette.GuiSkin;
import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedButton;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedTextField;
import at.koopro.wizardsandbeasts.network.floo.FlooRegisterRequestC2SPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

/**
 * The Ministry registration form: a name, a visibility, and a price.
 *
 * <p>Visibility is chosen <b>here</b> rather than afterwards by command, because the moment a player
 * names their house is the moment they have an opinion about who should be able to walk into it. A
 * private hearth that has to be made private in a second step is a public hearth for however long
 * that step takes.
 *
 * <p>It defaults to <b>private</b>. The asymmetry is deliberate: a player who wanted public and got
 * private is inconvenienced, and a player who wanted private and got public has had their home
 * published to the whole server. Only one of those is recoverable by noticing.
 */
public class FlooRegistrationScreen extends Screen {

    private static final int PANEL_W = 230;
    private static final int PANEL_H = 150;

    /**
     * Ministry memo stock: this is a form filed with the Floo Network Authority, not the grate
     * itself, so it is cut from the Ministry's paper rather than the hearth's soot.
     */
    private static final GuiSkin SKIN = GuiSkin.MINISTRY_MEMO;
    /** Smallest a nine-sliced control can be drawn: two 8px borders plus a pixel of face. */
    private static final int MIN_SKINNED = 2 * WizardsMetrics.PANEL_SPRITE_BORDER + 2;

    /** Matches {@code FlooAddress.MAX_LENGTH}; the server validates regardless. */
    private static final int ADDRESS_MAX_LENGTH = 48;

    private final BlockPos hearthPos;
    private final String initialAddress;
    private final int feeKnuts;

    private EditBox addressField;
    private ThemedButton submitButton;
    private ThemedButton visibilityButton;
    private boolean isPublic = false;
    private GuiScaleHelper.Layout layout;

    public FlooRegistrationScreen(@NonNull BlockPos hearthPos, @NonNull String currentAddress, int feeKnuts) {
        super(Component.translatable("floo.wizards_and_beasts.gui.register.title"));
        this.hearthPos = hearthPos;
        this.initialAddress = currentAddress;
        this.feeKnuts = feeKnuts;
    }

    @Override
    protected void init() {
        super.init();
        layout = GuiScaleHelper.Layout.panel(width, height, PANEL_W, PANEL_H);
        int px = layout.panelX();
        int py = layout.panelY();
        int panelW = layout.panelW();
        int panelH = layout.panelH();

        // A well pressed into the form, in the memo's ink: vanilla's field is a black box with
        // white text, which cannot sit on a sheet of paper.
        addressField = new ThemedTextField(font, px + layout.s(12), py + layout.s(44),
                panelW - layout.s(24), Math.max(MIN_SKINNED, layout.s(18)),
                Component.translatable("floo.wizards_and_beasts.gui.register.field"))
                .inkHint(Component.translatable("floo.wizards_and_beasts.gui.register.field"))
                .skin(SKIN);
        addressField.setMaxLength(ADDRESS_MAX_LENGTH);
        addressField.setValue(initialAddress);
        addressField.setResponder(text -> refreshSubmit());
        addRenderableWidget(addressField);
        setFocused(addressField);
        addressField.setFocused(true);

        int btnH = Math.max(MIN_SKINNED, layout.s(18));
        visibilityButton = addRenderableWidget(ThemedButton.skinned(
                px + layout.s(12), py + layout.s(70), panelW - layout.s(24), btnH,
                visibilityLabel(), () -> {
                    isPublic = !isPublic;
                    visibilityButton.setMessage(visibilityLabel());
                }, SKIN));

        int btnW = layout.s(84);
        int btnY = py + panelH - layout.s(12) - btnH;
        submitButton = addRenderableWidget(ThemedButton.skinned(
                px + panelW / 2 - btnW - layout.s(4), btnY, btnW, btnH,
                Component.translatable("floo.wizards_and_beasts.gui.register.submit"),
                this::onSubmit, SKIN));

        addRenderableWidget(ThemedButton.skinned(
                px + panelW / 2 + layout.s(4), btnY, btnW, btnH,
                Component.translatable("floo.wizards_and_beasts.gui.button.cancel"),
                this::onClose, SKIN));

        refreshSubmit();
    }

    private Component visibilityLabel() {
        return Component.translatable(isPublic
                ? "floo.wizards_and_beasts.gui.register.public"
                : "floo.wizards_and_beasts.gui.register.private");
    }

    /**
     * Only a blank check.
     *
     * <p>Whether the address is well-formed, unique and affordable are all server questions. Greying
     * the button out on a client guess would mean two validators to keep in step, and the client's
     * copy being wrong in either direction is worse than not having one: too strict and it refuses a
     * name the server would have taken, too loose and it promises one the server will not.
     */
    private void refreshSubmit() {
        if (submitButton != null && addressField != null) {
            submitButton.active = !addressField.getValue().isBlank();
        }
    }

    @Override
    public void render(@NonNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderMenuBackground(graphics);
        int px = layout.panelX();
        int py = layout.panelY();
        int panelW = layout.panelW();

        McStylePanel.drawSkinPanel(graphics, SKIN, px, py, panelW, layout.panelH());

        // Written on the sheet, flat, with the sheet's own rule under it: never a strip painted
        // over the frame, and never a drop shadow under ink.
        drawCentred(graphics, this.title, px + panelW / 2, py + layout.s(10), SKIN.ink());
        McStylePanel.drawSkinDivider(graphics, SKIN, px + layout.s(12), py + layout.s(18),
                panelW - layout.s(24));

        graphics.drawString(font, Component.translatable("floo.wizards_and_beasts.gui.register.prompt"),
                px + layout.s(12), py + layout.s(32), SKIN.ink(), false);

        Component price = feeKnuts > 0
                ? Component.translatable("floo.wizards_and_beasts.gui.register.fee",
                        at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper.formatFromKnuts(feeKnuts))
                : Component.translatable("floo.wizards_and_beasts.gui.register.fee_free");
        drawCentred(graphics, price, px + panelW / 2, py + layout.s(96), SKIN.muted());

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawCentred(GuiGraphics graphics, Component text, int cx, int y, int colour) {
        graphics.drawString(font, text, cx - font.width(text) / 2, y, colour, false);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)
                && submitButton != null && submitButton.active) {
            onSubmit();
            return true;
        }
        return super.keyPressed(event);
    }

    private void onSubmit() {
        String typed = addressField.getValue().trim();
        if (typed.isEmpty()) {
            return;
        }
        ClientPacketDistributor.sendToServer(
                new FlooRegisterRequestC2SPayload(hearthPos, typed, isPublic));
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
