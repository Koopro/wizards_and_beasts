package at.koopro.wizardsandbeasts.client.ability.wheel;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.ability.def.AbilityDefinition;
import at.koopro.wizardsandbeasts.ability.def.AbilityDefinitionRegistry;
import at.koopro.wizardsandbeasts.ability.def.AbilityType;
import at.koopro.wizardsandbeasts.client.ability.AbilityFrameworkKeyBindings;
import at.koopro.wizardsandbeasts.client.ability.state.ClientAbilitySelectionState;
import at.koopro.wizardsandbeasts.network.ability.AbilitySelectionC2SPayload;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
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

    private static final int RADIUS = 62;
    private static final int SLOT = 22;
    private static final int DEADZONE = 18;

    private static final int COLOR_BG = 0xC0101018;
    private static final int COLOR_SLOT = 0xD0303040;
    private static final int COLOR_SLOT_HOVER = 0xF0585870;
    private static final int COLOR_TOGGLE_RING = 0xFF56D364;
    private static final int COLOR_SELECTED = 0xFFFFD24A;
    private static final int COLOR_PIN = 0xFF6AB7FF;
    private static final int COLOR_TEXT = 0xFFE8E8E8;
    private static final int COLOR_COOLDOWN = 0xB0000000;

    private final List<AbilityDefinition> entries = new ArrayList<>();
    private int hovered = -1;

    public AbilityWheelScreen() {
        super(Component.translatable("screen." + WizardsAndBeastsMod.MODID + ".ability_wheel"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
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

        // Faint backing disc (square approximation — no texture dependency).
        g.fill(cx - RADIUS - SLOT, cy - RADIUS - SLOT, cx + RADIUS + SLOT, cy + RADIUS + SLOT, COLOR_BG);

        Font font = this.font;
        if (entries.isEmpty()) {
            Component none = Component.translatable("gui." + WizardsAndBeastsMod.MODID + ".ability_wheel.empty");
            g.drawCenteredString(font, none, cx, cy - 4, COLOR_TEXT);
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
            int x0 = ex - SLOT / 2;
            int y0 = ey - SLOT / 2;
            int x1 = ex + SLOT / 2;
            int y1 = ey + SLOT / 2;

            boolean isHovered = i == hovered;
            g.fill(x0, y0, x1, y1, isHovered ? COLOR_SLOT_HOVER : COLOR_SLOT);

            // Toggle-on entries get a distinguishing active-state ring.
            if (def.type() == AbilityType.TOGGLE && ClientAbilitySelectionState.isToggled(def.id())) {
                drawBorder(g, x0, y0, x1, y1, COLOR_TOGGLE_RING);
            }
            // Armed selection + pin markers.
            if (def.id().equals(selected)) {
                drawBorder(g, x0 - 1, y0 - 1, x1 + 1, y1 + 1, COLOR_SELECTED);
            }
            // Quick-slot badge: the number of the key that fires this ability directly.
            int quickSlot = ClientAbilitySelectionState.slotOf(def.id());
            if (quickSlot >= 0) {
                g.drawString(font, String.valueOf(quickSlot + 1), x1 - 6, y0 - 1, COLOR_PIN, false);
            }

            // Placeholder icon (missing-texture is acceptable per spec).
            g.blit(RenderPipelines.GUI_TEXTURED, def.icon(), x0 + 3, y0 + 3, 0.0F, 0.0F, 16, 16, 16, 16);

            // Cooldown dim: shade the slot from the bottom up by remaining fraction.
            float cd = ClientAbilitySelectionState.cooldownFraction(def.id(), gameTime, def.cooldownTicks());
            if (cd > 0f) {
                int shade = Mth.ceil((y1 - y0) * cd);
                g.fill(x0, y1 - shade, x1, y1, COLOR_COOLDOWN);
            }
        }

        // Center label: hovered ability name, or a prompt.
        Component center = hovered >= 0
                ? entries.get(hovered).displayName()
                : Component.translatable("gui." + WizardsAndBeastsMod.MODID + ".ability_wheel.hint");
        g.drawCenteredString(font, center, cx, cy - 4, COLOR_TEXT);

        maybeCloseOnRelease();
    }

    private static void drawBorder(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        g.fill(x0, y0, x1, y0 + 1, color);
        g.fill(x0, y1 - 1, x1, y1, color);
        g.fill(x0, y0, x0 + 1, y1, color);
        g.fill(x1 - 1, y0, x1, y1, color);
    }

    /** Closes and confirms when the (keyboard-bound) wheel key is physically released. */
    private void maybeCloseOnRelease() {
        if (this.minecraft == null) {
            return;
        }
        InputConstants.Key key = AbilityFrameworkKeyBindings.ABILITY_WHEEL.getKey();
        if (key.getType() != InputConstants.Type.KEYSYM || key.getValue() == InputConstants.UNKNOWN.getValue()) {
            return; // mouse-bound or unbound: rely on click to confirm/close
        }
        Window window = this.minecraft.getWindow();
        if (!InputConstants.isKeyDown(window, key.getValue())) {
            confirmHovered();
            onClose();
        }
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
