package at.koopro.wizardsandbeasts.client.ability.wheel;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui;
import at.koopro.wizardsandbeasts.client.gui.ui.UiIcons;
import at.koopro.wizardsandbeasts.client.gui.ui.UiSurface;
import at.koopro.wizardsandbeasts.client.gui.ui.UiText;
import at.koopro.wizardsandbeasts.ability.def.AbilityDefinition;
import at.koopro.wizardsandbeasts.ability.def.AbilityDefinitionRegistry;
import at.koopro.wizardsandbeasts.ability.def.AbilityType;
import at.koopro.wizardsandbeasts.client.ability.AbilityFrameworkKeyBindings;
import at.koopro.wizardsandbeasts.client.ability.AbilityWheelController;
import at.koopro.wizardsandbeasts.client.ability.state.ClientAbilitySelectionState;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.network.ability.AbilitySelectionC2SPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Hold-to-open radial ability wheel. Shows only usable ACTIVE/TOGGLE abilities (server-resolved via
 * {@link ClientAbilitySelectionState}, so grant + module filtering — including debug grants — is already
 * applied). Release the wheel key or left-click to confirm the hovered entry; right-click cycles it through
 * the quick slots (1 → 2 → 3 → unbound), each of which has its own one-press keybind.
 * A transient non-pausing overlay Screen (chosen over a bare HUD layer so hover + pin + cursor work
 * correctly); all state changes go through server-validated C2S payloads.
 */
@NullMarked
public final class AbilityWheelScreen extends Screen {

    private static final int RADIUS = 76;
    private static final int SLOT = 24;
    private static final int DEADZONE = 18;
    /** The hub that names the hovered ability; wide enough for a two-word name and a wrapped line. */
    private static final int HUB_W = 104;
    private static final int HUB_H = 56;

    // State, not theme, so deliberately not the gilt the wheel is drawn in: toggled / selected / pinned
    // have to stay distinguishable at a glance, and painting all three gilt would merge them.
    private static final int COLOR_TOGGLE_RING = 0xFF56D364;
    private static final int COLOR_PIN = 0xFF6AB7FF;
    private static final int COLOR_COOLDOWN = 0xB0000000;

    /**
     * Ticks the wheel key must stay held for this to count as a hold. Released sooner and it was a tap, so
     * the wheel latches open instead of slamming shut in the same frame the way hold-only did.
     */
    private static final int HOLD_THRESHOLD_TICKS = 4;

    private final List<AbilityDefinition> entries = new ArrayList<>();
    private int hovered = -1;

    private int ticksOpen;
    /** Null until the gesture is classified; true = hold (confirm on release), false = tap (stay open). */
    @Nullable
    private Boolean holdGesture;
    /** Last id sent by the hover auto-arm, so a stationary cursor does not re-send every frame. */
    @Nullable
    private Identifier armed;

    public AbilityWheelScreen() {
        super(Component.translatable("screen." + WizardsAndBeastsMod.MODID + ".ability_wheel"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        ticksOpen++;
        if (holdGesture != null) {
            return;
        }
        if (!isWheelKeyDown()) {
            holdGesture = Boolean.FALSE; // released early — a tap; latch open
        } else if (ticksOpen >= HOLD_THRESHOLD_TICKS) {
            holdGesture = Boolean.TRUE;  // still held — a hold; confirm when it is released
        }
    }

    private void rebuildEntries() {
        entries.clear();
        for (Identifier id : ClientAbilitySelectionState.usable()) {
            AbilityDefinition def = AbilityDefinitionRegistry.get(id);
            if (def != null && def.isWheelEligible()) {
                entries.add(def);
            }
        }
    }

    private int computeHovered(double mouseX, double mouseY, int cx, int cy) {
        if (entries.isEmpty()) {
            return -1;
        }
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        if (Math.hypot(dx, dy) < DEADZONE) {
            return -1;
        }
        double angle = Math.atan2(dy, dx) + Math.PI / 2.0; // 0 = top, clockwise
        if (angle < 0) {
            angle += Math.PI * 2.0;
        }
        int n = entries.size();
        int index = (int) Math.round(angle / (Math.PI * 2.0 / n)) % n;
        return index < 0 ? index + n : index;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        rebuildEntries();
        int cx = this.width / 2;
        int cy = this.height / 2;
        hovered = computeHovered(mouseX, mouseY, cx, cy);
        autoArmHovered();

        Font font = this.font;
        UiSurface night = UiSurface.NIGHT;
        if (entries.isEmpty()) {
            Ui.panel(g, night, cx - HUB_W / 2, cy - HUB_H / 2, HUB_W, HUB_H);
            Ui.emptyState(g, font, night, new Ui.Rect(cx - HUB_W / 2, cy - HUB_H / 2, HUB_W, HUB_H),
                    Component.translatable("gui." + WizardsAndBeastsMod.MODID + ".ability_wheel.empty"), null);
            maybeCloseOnRelease();
            return;
        }

        long gameTime = this.minecraft != null && this.minecraft.level != null
                ? this.minecraft.level.getGameTime() : 0L;
        Identifier selected = ClientAbilitySelectionState.selected();

        int n = entries.size();
        for (int i = 0; i < n; i++) {
            AbilityDefinition def = entries.get(i);
            double angle = -Math.PI / 2.0 + i * (Math.PI * 2.0 / n);
            int ex = cx + (int) Math.round(Math.cos(angle) * RADIUS);
            int ey = cy + (int) Math.round(Math.sin(angle) * RADIUS);
            boolean isHovered = i == hovered;
            // The hovered socket rises two pixels towards the hub: a hover state that reads without colour.
            int lift = isHovered ? 2 : 0;
            int x0 = ex - SLOT / 2 - (int) Math.round(Math.cos(angle) * lift);
            int y0 = ey - SLOT / 2 - (int) Math.round(Math.sin(angle) * lift);
            int x1 = x0 + SLOT;
            int y1 = y0 + SLOT;

            Ui.nightCard(g, x0, y0, SLOT, SLOT);
            if (isHovered) {
                g.fill(x0 + 2, y0 + 2, x1 - 2, y1 - 2, night.hoverWash());
            }
            if (def.type() == AbilityType.TOGGLE && ClientAbilitySelectionState.isToggled(def.id())) {
                drawBorder(g, x0 + 2, y0 + 2, x1 - 2, y1 - 2, COLOR_TOGGLE_RING);
            }
            if (def.id().equals(selected)) {
                drawBorder(g, x0 - 1, y0 - 1, x1 + 1, y1 + 1, WizardsPalette.GILT_LIGHT);
            }
            UiIcons.icon16(g, font, def.icon(), def.displayName(), x0 + 4, y0 + 4);

            // Cooldown dim: shade the socket from the bottom up by remaining fraction.
            float cd = ClientAbilitySelectionState.cooldownFraction(def.id(), gameTime, def.cooldownTicks());
            if (cd > 0f) {
                int shade = Mth.ceil((SLOT - 4) * cd);
                g.fill(x0 + 2, y1 - 2 - shade, x1 - 2, y1 - 2, COLOR_COOLDOWN);
            }
            // Quick-slot badge: the number of the key that fires this ability directly.
            int quickSlot = ClientAbilitySelectionState.slotOf(def.id());
            if (quickSlot >= 0) {
                String badge = String.valueOf(quickSlot + 1);
                g.fill(x1 - 7, y0 - 2, x1 + 1, y0 + 8, UiSurface.NIGHT_WELL);
                g.drawString(font, badge, x1 - 3 - font.width(badge) / 2, y0 - 1, COLOR_PIN, false);
            }
        }

        // The hub: the hovered ability's name, kind and description, or how to use the wheel.
        int hx = cx - HUB_W / 2;
        int hy = cy - HUB_H / 2;
        Ui.panel(g, night, hx, hy, HUB_W, HUB_H);
        Ui.Rect hub = new Ui.Rect(hx + 6, hy + 6, HUB_W - 12, HUB_H - 12);
        if (hovered >= 0) {
            AbilityDefinition def = entries.get(hovered);
            UiText.drawFit(g, font, night, def.displayName(), hub.x(), hub.y(), hub.w(), night.heading());
            UiText.drawWrapped(g, font, night, UiText.Role.CAPTION,
                    Component.translatable(def.descriptionKey()), hub.x(), hub.y() + 12, hub.w(), 3);
        } else {
            Ui.emptyState(g, font, night, hub,
                    Component.translatable("gui." + WizardsAndBeastsMod.MODID + ".ability_wheel.hint"), null);
        }

        maybeCloseOnRelease();
    }

    private static void drawBorder(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        g.fill(x0, y0, x1, y0 + 1, color);
        g.fill(x0, y1 - 1, x1, y1, color);
        g.fill(x0, y0, x0 + 1, y1, color);
        g.fill(x1 - 1, y0, x1, y1, color);
    }

    /** True while the wheel key is physically held. False for a mouse-bound or unbound key. */
    private boolean isWheelKeyDown() {
        if (this.minecraft == null) {
            return false;
        }
        InputConstants.Key key = AbilityFrameworkKeyBindings.ABILITY_WHEEL.getKey();
        if (key.getType() != InputConstants.Type.KEYSYM || key.getValue() == InputConstants.UNKNOWN.getValue()) {
            return false; // mouse-bound or unbound: rely on click to confirm/close
        }
        return InputConstants.isKeyDown(this.minecraft.getWindow(), key.getValue());
    }

    /** Hold gesture only: releasing the wheel key confirms the hovered entry and closes. */
    private void maybeCloseOnRelease() {
        if (!Boolean.TRUE.equals(holdGesture) || isWheelKeyDown()) {
            return;
        }
        confirmHovered();
        onClose();
    }

    /**
     * Arms whatever the cursor is over, with no click. Uses {@code SELECT} rather than {@code CONFIRM} so
     * sweeping past a TOGGLE cannot flip it (see {@link AbilitySelectionC2SPayload.Action}).
     */
    private void autoArmHovered() {
        if (hovered < 0 || hovered >= entries.size()) {
            return;
        }
        Identifier id = entries.get(hovered).id();
        if (id.equals(armed)) {
            return;
        }
        armed = id;
        send(AbilitySelectionC2SPayload.Action.SELECT, id);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        InputConstants.Key key = AbilityFrameworkKeyBindings.ABILITY_WHEEL.getKey();
        if (key.getType() == InputConstants.Type.KEYSYM && event.key() == key.getValue()) {
            // Only a latched (tap-opened) wheel closes on a press of its own key. GLFW auto-repeat delivers
            // this same callback while the key is merely *held* — the vanilla keyboard handler routes both
            // PRESS and REPEAT to keyPressed — so during a hold gesture this must do nothing and leave the
            // close to maybeCloseOnRelease, or the wheel slams shut half a second into every hold.
            if (!Boolean.FALSE.equals(holdGesture)) {
                return true;
            }
            AbilityWheelController.suppressOpenUntilRelease();
            confirmHovered();
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        rebuildEntries();
        hovered = computeHovered(event.x(), event.y(), this.width / 2, this.height / 2);
        if (event.button() == 0) { // left = confirm
            confirmHovered();
            onClose();
            return true;
        }
        if (event.button() == 1) { // right = cycle hovered through the quick slots
            if (hovered >= 0) {
                send(AbilitySelectionC2SPayload.Action.QUICK_SLOT, entries.get(hovered).id());
            }
            return true;
        }
        return super.mouseClicked(event, isDoubleClick);
    }

    private void confirmHovered() {
        if (hovered >= 0 && hovered < entries.size()) {
            send(AbilitySelectionC2SPayload.Action.CONFIRM, entries.get(hovered).id());
        }
    }

    private static void send(AbilitySelectionC2SPayload.Action action, Identifier id) {
        ClientPacketDistributor.sendToServer(new AbilitySelectionC2SPayload(action, id));
    }
}
