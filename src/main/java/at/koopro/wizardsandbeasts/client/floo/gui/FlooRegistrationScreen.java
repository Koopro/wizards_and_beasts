package at.koopro.wizardsandbeasts.client.floo.gui;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import at.koopro.wizardsandbeasts.network.floo.FlooRegisterRequestC2SPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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

    private static final int COL_TITLE = 0xFFD4AF37;
    private static final int COL_TEXT = 0xFFE8E8E8;
    private static final int COL_MUTED = 0xFF999999;
    private static final int COL_DIVIDER = 0xFF444466;

    /** Matches {@code FlooAddress.MAX_LENGTH}; the server validates regardless. */
    private static final int ADDRESS_MAX_LENGTH = 48;

    private final BlockPos hearthPos;
    private final String initialAddress;
    private final int feeKnuts;

    private EditBox addressField;
    private Button submitButton;
    private Button visibilityButton;
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

        addressField = new EditBox(font, px + layout.s(12), py + layout.s(44),
                panelW - layout.s(24), layout.s(18),
                Component.translatable("floo.wizards_and_beasts.gui.register.field"));
        addressField.setMaxLength(ADDRESS_MAX_LENGTH);
        addressField.setHint(Component.translatable("floo.wizards_and_beasts.gui.register.field"));
        addressField.setValue(initialAddress);
        addressField.setResponder(text -> refreshSubmit());
        addRenderableWidget(addressField);
        setFocused(addressField);
        addressField.setFocused(true);

        visibilityButton = addRenderableWidget(
                Button.builder(visibilityLabel(), b -> {
                    isPublic = !isPublic;
                    visibilityButton.setMessage(visibilityLabel());
                }).bounds(px + layout.s(12), py + layout.s(70), panelW - layout.s(24), layout.s(18)).build());

        int btnW = layout.s(84);
        int btnY = py + panelH - layout.s(28);
        submitButton = addRenderableWidget(
                Button.builder(Component.translatable("floo.wizards_and_beasts.gui.register.submit"),
                                b -> onSubmit())
                        .bounds(px + panelW / 2 - btnW - layout.s(4), btnY, btnW, layout.s(18))
                        .build());

        addRenderableWidget(
                Button.builder(Component.translatable("floo.wizards_and_beasts.gui.button.cancel"),
                                b -> onClose())
                        .bounds(px + panelW / 2 + layout.s(4), btnY, btnW, layout.s(18))
                        .build());

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

        McStylePanel.drawTexturedPanel(graphics, px, py, panelW, layout.panelH());

        graphics.drawCenteredString(font, this.title, px + panelW / 2, py + layout.s(10), COL_TITLE);
        graphics.fill(px + layout.s(5), py + layout.s(24), px + panelW - layout.s(5),
                py + layout.s(25), COL_DIVIDER);

        graphics.drawString(font, Component.translatable("floo.wizards_and_beasts.gui.register.prompt"),
                px + layout.s(12), py + layout.s(32), COL_TEXT, false);

        Component price = feeKnuts > 0
                ? Component.translatable("floo.wizards_and_beasts.gui.register.fee",
                        at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper.formatFromKnuts(feeKnuts))
                : Component.translatable("floo.wizards_and_beasts.gui.register.fee_free");
        graphics.drawCenteredString(font, price, px + panelW / 2, py + layout.s(96), COL_MUTED);

        super.render(graphics, mouseX, mouseY, partialTick);
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
