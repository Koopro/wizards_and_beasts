package at.koopro.wizardsandbeasts.client.spell.wheel;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.ModTextures;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.util.GuiText;
import at.koopro.wizardsandbeasts.client.heritage.state.ClientHeritageDataState;
import at.koopro.wizardsandbeasts.client.spell.SpellKeyBindings;
import at.koopro.wizardsandbeasts.client.spell.state.ClientSpellDataState;
import at.koopro.wizardsandbeasts.client.spell.ui.SpellCooldownDisplay;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialRules;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.network.spell.SpellAssignC2SPayload;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Hold-to-open spell wheel: hold {@link SpellKeyBindings#SPELL_WHEEL}, sweep the cursor to a spell,
 * release to arm it.
 *
 * <p>It solves a specific problem rather than replacing the loadout. Casting runs off four slots
 * ({@code PlayerSpellData.LOADOUT_SIZE}) picked with the arrow keys, and swapping what is <em>in</em>
 * a slot meant opening {@code SpellMenuScreen} — fine at a workbench, useless mid-fight. So confirming
 * here sends an ordinary {@link SpellAssignC2SPayload} for the <b>currently active slot</b>: the wheel
 * is a fast way to change what the slot you are already holding contains, and the cast path, the
 * cooldowns and the HUD are all untouched by it.
 *
 * <p>Everything the wheel changes goes through a server-validated payload. The filtering below only
 * decides what to <em>offer</em> — the server re-checks known/ability/heritage on arrival, and if the
 * two disagree the server wins and the wheel was showing something stale.
 *
 * <p>A transient non-pausing {@link Screen} rather than a HUD layer, for the same reason
 * {@code AbilityWheelScreen} is one: hover and a real cursor only work inside a Screen.
 */
@NullMarked
public final class SpellWheelScreen extends Screen {

    /** Grows with the entry count so a wizard with twenty spells does not get twenty overlapping icons. */
    private static final int RADIUS_MIN = 62;
    private static final int RADIUS_PER_ENTRY = 5;
    private static final int RADIUS_MAX = 108;
    private static final int SLOT = 24;
    private static final int ICON_INSET = 3;
    private static final int ICON_SIZE = SLOT - ICON_INSET * 2;
    /** Native size of the HUD spell icons this reuses. */
    private static final int ICON_TEX_SIZE = 92;

    private static final int COLOR_BG = 0xC0000000 | (WizardsPalette.INK & 0x00FFFFFF);
    private static final int COLOR_SLOT = 0xD0000000 | (WizardsPalette.WELL & 0x00FFFFFF);
    private static final int COLOR_SLOT_HOVER = 0xF0000000 | (WizardsPalette.RAIL & 0x00FFFFFF);
    private static final int COLOR_TEXT = WizardsPalette.TEXT;
    /** State, not theme: the armed spell has to stay distinguishable from the hovered one at a glance. */
    private static final int COLOR_ARMED = 0xFFFFD24A;
    private static final int COLOR_COOLDOWN = 0xB0000000;

    /**
     * Ticks the key must stay held for the gesture to count as a hold. Released sooner and it was a
     * tap, so the wheel latches open instead of slamming shut in the same frame — the same
     * tap-or-hold classification the ability wheel uses, and for the same reason.
     */
    private static final int HOLD_THRESHOLD_TICKS = 4;

    private List<String> entries = List.of();
    private int hovered = SpellWheelModel.NONE;

    private int ticksOpen;
    /** Null until classified; TRUE = hold (confirm on release), FALSE = tap (stay open). */
    @Nullable
    private Boolean holdGesture;

    public SpellWheelScreen() {
        super(Component.translatable("screen." + WizardsAndBeastsMod.MODID + ".spell_wheel"));
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
            holdGesture = Boolean.FALSE;
        } else if (ticksOpen >= HOLD_THRESHOLD_TICKS) {
            holdGesture = Boolean.TRUE;
        }
    }

    /**
     * Rebuilt every frame so a spell learned, or a heritage changed, while the wheel is open is
     * reflected immediately. The list is small (a player's known spells) and this runs only while the
     * wheel is actually up, so the per-frame cost is a filtered copy of a few dozen strings.
     */
    private void rebuildEntries() {
        PlayerSpellData data = ClientSpellDataState.get();
        var heritage = ClientHeritageDataState.get().getSelectedHeritage();
        entries = SpellWheelModel.entries(data.getKnownSpells(), id -> {
            Spell spell = Spells.byId(id);
            // canHeritageUseSpell already rejects Obscurial abilities, which are driven by the ability
            // wheel and are refused by SpellAssignC2SPayload — offering one here would be a dead pick.
            return spell != null && ObscurialRules.canHeritageUseSpell(heritage, spell);
        });
    }

    private int radius() {
        return Mth.clamp(RADIUS_MIN + entries.size() * RADIUS_PER_ENTRY, RADIUS_MIN, RADIUS_MAX);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        rebuildEntries();
        int cx = this.width / 2;
        int cy = this.height / 2;
        hovered = SpellWheelModel.hoveredIndex(mouseX - cx, mouseY - cy, entries.size());

        int radius = radius();
        g.fill(cx - radius - SLOT, cy - radius - SLOT, cx + radius + SLOT, cy + radius + SLOT, COLOR_BG);

        Font font = this.font;
        if (entries.isEmpty()) {
            g.drawCenteredString(font,
                    Component.translatable("gui." + WizardsAndBeastsMod.MODID + ".spell_wheel.empty"),
                    cx, cy - 4, COLOR_TEXT);
            maybeCloseOnRelease();
            return;
        }

        PlayerSpellData data = ClientSpellDataState.get();
        String armed = data.getActiveSpellId();
        long gameTick = this.minecraft != null && this.minecraft.level != null
                ? ClientSpellDataState.monotonicTick(this.minecraft.level.getGameTime())
                : 0L;

        int n = entries.size();
        for (int i = 0; i < n; i++) {
            String spellId = entries.get(i);
            double angle = SpellWheelModel.angleOf(i, n);
            int ex = cx + (int) Math.round(Math.cos(angle) * radius);
            int ey = cy + (int) Math.round(Math.sin(angle) * radius);
            int x0 = ex - SLOT / 2;
            int y0 = ey - SLOT / 2;
            int x1 = x0 + SLOT;
            int y1 = y0 + SLOT;

            g.fill(x0, y0, x1, y1, i == hovered ? COLOR_SLOT_HOVER : COLOR_SLOT);
            if (spellId.equals(armed)) {
                drawBorder(g, x0 - 1, y0 - 1, x1 + 1, y1 + 1, COLOR_ARMED);
            }

            g.blit(RenderPipelines.GUI_TEXTURED,
                    ModTextures.resolveWandHudSpellIcon(Minecraft.getInstance().getResourceManager(), spellId),
                    x0 + ICON_INSET, y0 + ICON_INSET, 0.0F, 0.0F,
                    ICON_SIZE, ICON_SIZE, ICON_TEX_SIZE, ICON_TEX_SIZE, ICON_TEX_SIZE, ICON_TEX_SIZE);

            // Cooldown shade, bottom-up by remaining fraction — the same reading as the HUD diamond,
            // so a spell that looks half-recharged there looks half-recharged here.
            long expiry = data.getCooldownExpiry(spellId);
            if (expiry > gameTick) {
                Spell spell = Spells.byId(spellId);
                int base = spell != null ? Math.max(1, spell.getBaseCooldownTicks()) : 20;
                long span = ClientSpellDataState.getCooldownSpanTicks(spellId, base);
                float remaining = SpellCooldownDisplay.remainingFraction(expiry, gameTick, span);
                int shade = Mth.ceil(SLOT * remaining);
                g.fill(x0, y1 - shade, x1, y1, COLOR_COOLDOWN);
            }
        }

        Component centre = hovered >= 0
                ? displayName(entries.get(hovered))
                : Component.translatable("gui." + WizardsAndBeastsMod.MODID + ".spell_wheel.hint");
        g.drawCenteredString(font, centre, cx, cy - 4, COLOR_TEXT);

        maybeCloseOnRelease();
    }

    private static Component displayName(String spellId) {
        Spell spell = Spells.byId(spellId);
        return spell == null
                ? Component.literal(spellId)
                : Component.literal(GuiText.resolve(spell.getDisplayName()));
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
        InputConstants.Key key = SpellKeyBindings.SPELL_WHEEL.getKey();
        if (key.getType() != InputConstants.Type.KEYSYM || key.getValue() == InputConstants.UNKNOWN.getValue()) {
            return false; // mouse-bound or unbound: the wheel latches, and a click confirms
        }
        return InputConstants.isKeyDown(this.minecraft.getWindow(), key.getValue());
    }

    /** Hold gesture only: releasing the key confirms the hovered entry and closes. */
    private void maybeCloseOnRelease() {
        if (!Boolean.TRUE.equals(holdGesture) || isWheelKeyDown()) {
            return;
        }
        confirmHovered();
        onClose();
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        InputConstants.Key key = SpellKeyBindings.SPELL_WHEEL.getKey();
        if (key.getType() == InputConstants.Type.KEYSYM && event.key() == key.getValue()) {
            // Only a latched (tap-opened) wheel closes on a press of its own key. GLFW auto-repeat delivers
            // this same callback while the key is merely *held* — the vanilla keyboard handler routes both
            // PRESS and REPEAT to keyPressed — so during a hold gesture this must do nothing and leave the
            // close to maybeCloseOnRelease, or the wheel slams shut half a second into every hold.
            if (!Boolean.FALSE.equals(holdGesture)) {
                return true;
            }
            SpellWheelController.suppressOpenUntilRelease();
            confirmHovered();
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        rebuildEntries();
        hovered = SpellWheelModel.hoveredIndex(
                event.x() - this.width / 2.0, event.y() - this.height / 2.0, entries.size());
        if (event.button() == 0) {
            confirmHovered();
            onClose();
            return true;
        }
        return super.mouseClicked(event, isDoubleClick);
    }

    /**
     * Arms the hovered spell into the active slot. Nothing hovered is a deliberate cancel — the
     * deadzone exists so a player who opens the wheel and thinks better of it can release in the
     * middle and keep whatever they had.
     */
    private void confirmHovered() {
        if (hovered < 0 || hovered >= entries.size()) {
            return;
        }
        if (!ModuleManager.isEnabled(Module.WANDS_AND_SPELLS)) {
            return;
        }
        int slot = Mth.clamp(ClientSpellDataState.get().getActiveSlot(), 0, PlayerSpellData.LOADOUT_SIZE - 1);
        String spellId = entries.get(hovered);
        if (spellId.equals(ClientSpellDataState.get().getLoadoutSpell(slot))) {
            return; // already armed — no packet for a no-op
        }
        ClientPacketDistributor.sendToServer(new SpellAssignC2SPayload(slot, spellId));
    }
}
