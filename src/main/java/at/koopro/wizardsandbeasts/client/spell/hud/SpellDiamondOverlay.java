package at.koopro.wizardsandbeasts.client.spell.hud;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.hud.WandHudSprites;
import at.koopro.wizardsandbeasts.client.spell.state.ClientSpellDataState;
import at.koopro.wizardsandbeasts.client.spell.state.ClientSpellRejectFeedback;
import at.koopro.wizardsandbeasts.client.spell.ui.SpellCooldownDisplay;
import at.koopro.wizardsandbeasts.client.spell.ui.SpellHudUiModel;
import at.koopro.wizardsandbeasts.client.ui.UiStateProjection;
import at.koopro.wizardsandbeasts.spell.cast.SpellCastService;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class SpellDiamondOverlay {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "spell_diamond");
    private static final int HUD_TEX_SIZE = 256;
    private static final int HUD_ON_SCREEN_SIZE = 96;
    private static final int ICON_TEX_SIZE = 92;
    private static final int EDGE_MARGIN = 10;

    /** Dark semi-transparent overlay color for cooldown and GCD rendering. */
    private static final int COOLDOWN_OVERLAY_COLOR = 0xCC202020;

    /** Active spell name, drawn just above the plate. */
    private static final int SPELL_NAME_COLOR = 0xFFE8D2B4;
    /** Reject line, one row above the name. Warm red so a refusal never reads as the spell name. */
    private static final int REJECT_LINE_RGB = 0xFF7A6A;
    /** Widest a HUD caption may run before it is ellipsised, relative to the plate. */
    private static final int CAPTION_MAX_EXTRA_WIDTH = 24;

    private static final int[][] SLOT_CENTERS = {
            {128, 80},
            {176, 128},
            {128, 176},
            {80, 128}
    };

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        if (!at.koopro.wizardsandbeasts.Config.showSpellHudOverlay) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        SpellHudUiModel spellHudUi = UiStateProjection.spellHud(mc);
        if (!spellHudUi.canRenderSpellHud()) return;
        var data = spellHudUi.spellData();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int hudX = screenWidth - HUD_ON_SCREEN_SIZE - EDGE_MARGIN;
        int hudY = screenHeight - HUD_ON_SCREEN_SIZE - EDGE_MARGIN;

        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, WandHudSprites.PLATE,
                hudX, hudY, HUD_ON_SCREEN_SIZE, HUD_ON_SCREEN_SIZE);

        for (int i = 0; i < 4; i++) {
            String spellId = data.getLoadoutSpell(i);
            if (spellId != null) {
                int iconSize = scalePx(ICON_TEX_SIZE);
                int iconX = hudX + scalePx(SLOT_CENTERS[i][0]) - iconSize / 2;
                int iconY = hudY + scalePx(SLOT_CENTERS[i][1]) - iconSize / 2;
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                        WandHudSprites.spellIcon(spellId), iconX, iconY, iconSize, iconSize);
            }
        }

        // Per-slot cooldown sweeps: drawn above spell icons, below gold trim.
        // Uses diamond-shaped scanline fill so overlay never bleeds outside the slot polygon.
        if (ModuleManager.isEnabled(Module.WANDS_AND_SPELLS)) {
            long rawTick = mc.level.getGameTime();
            long gameTick = ClientSpellDataState.monotonicTick(rawTick);
            // Drop the partial-tick interpolation while the clock is clamped (server stalled and snapped
            // gameTime backward) so the sweep holds steady instead of springing back to full.
            float partial = rawTick >= gameTick ? delta.getGameTimeDeltaPartialTick(false) : 0f;
            int iconSize = scalePx(ICON_TEX_SIZE);
            for (int i = 0; i < 4; i++) {
                String spellId = data.getLoadoutSpell(i);
                if (spellId == null) continue;
                long expiryTick = data.getCooldownExpiry(spellId);
                if (expiryTick <= gameTick) continue;
                Spell spell = Spells.byId(spellId);
                int baseCooldown = spell != null ? Math.max(1, spell.getBaseCooldownTicks()) : 20;
                // Span, not baseCooldown — see SpellCooldownDisplay for why, and for the clamp.
                long spanTicks = ClientSpellDataState.getCooldownSpanTicks(spellId, baseCooldown);
                float remaining = SpellCooldownDisplay.remainingFraction(expiryTick, gameTick + partial, spanTicks);
                int cx = hudX + scalePx(SLOT_CENTERS[i][0]);
                int cy = hudY + scalePx(SLOT_CENTERS[i][1]);
                renderDiamondSweep(graphics, cx, cy, iconSize / 2, remaining);
            }
        }

        int activeSlot = Mth.clamp(data.getActiveSlot(), 0, 3);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, WandHudSprites.TRIM,
                hudX, hudY, HUD_ON_SCREEN_SIZE, HUD_ON_SCREEN_SIZE);

        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, WandHudSprites.selectedTrim(activeSlot),
                hudX, hudY, HUD_ON_SCREEN_SIZE, HUD_ON_SCREEN_SIZE);

        // GCD ring: clockwise sweep around the outer diamond perimeter, drawn above gold trim.
        // Appears for GLOBAL_COOLDOWN_TICKS (5 ticks = 0.25s) after every successful cast.
        if (ModuleManager.isEnabled(Module.WANDS_AND_SPELLS)) {
            long gcdEndTick = data.getGlobalCooldownEndTick();
            // Same monotonic clock the per-slot sweeps use. This read the raw game tick, so during a
            // server stall the ring and the four sweeps drawn inches away disagreed about what time it
            // was — the sweeps held while the ring sprang. Five ticks is short, but the inconsistency
            // is the kind that reads as a rendering glitch.
            long rawGcdTick = mc.level.getGameTime();
            long gcdTick = ClientSpellDataState.monotonicTick(rawGcdTick);
            if (gcdEndTick > gcdTick) {
                float partial = rawGcdTick >= gcdTick ? delta.getGameTimeDeltaPartialTick(false) : 0f;
                float gcdRemaining = Mth.clamp(
                        (gcdEndTick - (gcdTick + partial)) / (float) SpellCastService.GLOBAL_COOLDOWN_TICKS,
                        0f, 1f);
                int iconSize = scalePx(ICON_TEX_SIZE);
                // Outer diamond half-size = distance from plate center to outer slot edge
                int ringHalfSize = scalePx(SLOT_CENTERS[1][0]) + iconSize / 2 - HUD_ON_SCREEN_SIZE / 2;
                renderGcdRing(graphics,
                        hudX + HUD_ON_SCREEN_SIZE / 2,
                        hudY + HUD_ON_SCREEN_SIZE / 2,
                        ringHalfSize, gcdRemaining);
            }
        }

        // Seconds readout: integer seconds above the icon, visible above trim.
        // Displayed when remaining cooldown > 1s.
        if (ModuleManager.isEnabled(Module.WANDS_AND_SPELLS)) {
            long rawTick = mc.level.getGameTime();
            long gameTick = ClientSpellDataState.monotonicTick(rawTick);
            float partial = rawTick >= gameTick ? delta.getGameTimeDeltaPartialTick(false) : 0f;
            for (int i = 0; i < 4; i++) {
                String spellId = data.getLoadoutSpell(i);
                if (spellId == null) continue;
                long expiryTick = data.getCooldownExpiry(spellId);
                float now = gameTick + partial;
                if (SpellCooldownDisplay.showsSecondsReadout(expiryTick, now)) {
                    String text = String.valueOf(SpellCooldownDisplay.secondsRemaining(expiryTick, now));
                    int tw = mc.font.width(text);
                    int cx = hudX + scalePx(SLOT_CENTERS[i][0]);
                    int cy = hudY + scalePx(SLOT_CENTERS[i][1]);
                    graphics.drawString(mc.font, text,
                            cx - tw / 2,
                            cy - mc.font.lineHeight / 2,
                            0xFFFFFFFF, true);
                }
            }
        }

        String activeSpellId = data.getLoadoutSpell(activeSlot);
        if (activeSpellId != null) {
            Spell activeSpell = Spells.byId(activeSpellId);
            if (activeSpell != null) {
                drawCaption(graphics, mc.font,
                        at.koopro.wizardsandbeasts.client.gui.util.GuiText.resolve(activeSpell.getDisplayName()),
                        hudX, hudY - 11, screenWidth, SPELL_NAME_COLOR);
            }
            if (ModuleManager.isEnabled(Module.PROFICIENCY)) {
                renderProficiencyPips(graphics, hudX, hudY, data.getSpellProficiency(activeSpellId));
            }
        }

        renderRejectLine(graphics, mc, hudX, hudY, screenWidth);
    }

    /**
     * The short-lived reason for the last refused cast, one row above the active spell name.
     *
     * <p>Drawn here rather than on the action bar because this is where the player is already
     * looking when a cast fails — the slot that just refused is directly below it. It appears only
     * when {@link ClientSpellRejectFeedback} routed the line to the HUD; when the HUD is hidden that
     * class puts the same sentence on the action bar instead, so a refusal is shown once and never
     * twice.
     */
    private static void renderRejectLine(GuiGraphics graphics, Minecraft mc,
                                         int hudX, int hudY, int screenWidth) {
        Component reason = ClientSpellRejectFeedback.hudMessage();
        if (reason == null) {
            return;
        }
        int alpha = Math.round(ClientSpellRejectFeedback.hudAlpha() * 255f);
        if (alpha <= 0) {
            return;
        }
        // getString() rather than the Component: the line has to be measured and possibly ellipsised
        // to the plate width, and drawString(String) is the overload that pairs with clampTextToWidth.
        drawCaption(graphics, mc.font, reason.getString(), hudX, hudY - 22, screenWidth,
                (alpha << 24) | REJECT_LINE_RGB);
    }

    /**
     * Draws one line centred over the plate, ellipsised to fit and nudged back on-screen if centring
     * would push it past either edge (the plate sits in the corner, so it often would).
     */
    private static void drawCaption(GuiGraphics graphics, Font font, String text,
                                    int hudX, int y, int screenWidth, int argb) {
        String clamped = clampTextToWidth(font, text, HUD_ON_SCREEN_SIZE + CAPTION_MAX_EXTRA_WIDTH);
        int width = font.width(clamped);
        int x = Mth.clamp(
                hudX + HUD_ON_SCREEN_SIZE / 2 - width / 2,
                EDGE_MARGIN,
                Math.max(EDGE_MARGIN, screenWidth - EDGE_MARGIN - width));
        graphics.drawString(font, clamped, x, y, argb, true);
    }

    private static void renderProficiencyPips(GuiGraphics graphics, int hudX, int hudY, float proficiency) {
        int pipSize = 4;
        int gap = 2;
        int totalWidth = 28;
        int startX = hudX + (HUD_ON_SCREEN_SIZE / 2) - (totalWidth / 2);
        int y = hudY + HUD_ON_SCREEN_SIZE + 2;
        int filled = proficiency >= 1.0f ? 5 : Math.max(0, Math.min(4, (int) Math.floor(proficiency * 5.0f)));
        for (int i = 0; i < 5; i++) {
            int x = startX + (i * (pipSize + gap));
            int color = i < filled ? 0xFFD700 : 0x444444;
            graphics.fill(x, y, x + pipSize, y + pipSize, color);
        }
    }

    /**
     * Draws the remaining-cooldown dark overlay on a single slot diamond.
     *
     * Uses a scanline fill approach: for each row of the diamond polygon, the
     * "dark" horizontal span is determined by the angular sweep boundary so that
     * the overlay never bleeds outside the diamond shape.
     *
     * remaining=1 → full dark (just cast); remaining=0 → no overlay (cooldown done).
     * Sweep clears clockwise from the top vertex.
     */
    private static void renderDiamondSweep(GuiGraphics graphics, int cx, int cy, int halfSize, float remaining) {
        if (remaining <= 0f || halfSize <= 0) return;
        float clearAngle = (1f - remaining) * Mth.TWO_PI; // radians already cleared from top, CW

        for (int dy = -halfSize; dy <= halfSize; dy++) {
            int rowWidth = halfSize - Math.abs(dy);
            if (rowWidth <= 0) continue;
            int y = cy + dy;
            fillSweepRow(graphics, cx, y, dy, -rowWidth, rowWidth, clearAngle);
        }
    }

    /**
     * Draws the 2-pixel-thick GCD ring around the outer diamond perimeter.
     *
     * The ring is circumscribed around the full 4-slot diamond plate.
     * Same clockwise-from-top sweep as the per-slot overlay.
     */
    private static void renderGcdRing(GuiGraphics graphics, int cx, int cy, int halfSize, float remaining) {
        if (remaining <= 0f || halfSize <= 0) return;
        float clearAngle = (1f - remaining) * Mth.TWO_PI;
        int thickness = 2;

        for (int dy = -halfSize; dy <= halfSize; dy++) {
            int outerW = halfSize - Math.abs(dy);
            int innerW = halfSize - thickness - Math.abs(dy);
            if (outerW <= 0) continue;
            int y = cy + dy;
            if (innerW < 0) {
                // Near top/bottom vertices: row narrower than ring thickness, fill fully
                fillSweepRow(graphics, cx, y, dy, -outerW, outerW, clearAngle);
            } else {
                // Left band
                fillSweepRow(graphics, cx, y, dy, -outerW, -(innerW + 1), clearAngle);
                // Right band
                if (innerW + 1 <= outerW) {
                    fillSweepRow(graphics, cx, y, dy, innerW + 1, outerW, clearAngle);
                }
            }
        }
    }

    /**
     * Fills the "dark" pixels in the range [cx+dxFrom, cx+dxTo] for screen row y,
     * where "dark" = angular position >= clearAngle (clockwise from top).
     *
     * Groups consecutive dark pixels into single fill() calls to minimise draw calls.
     */
    private static void fillSweepRow(GuiGraphics graphics, int cx, int y, int dy,
                                     int dxFrom, int dxTo, float clearAngle) {
        boolean inDark = false;
        int darkStart = 0;
        for (int dx = dxFrom; dx <= dxTo; dx++) {
            // Angle measured clockwise from top (north) in screen coords (y-down)
            float angle = (float) Math.atan2(dx, -dy);
            if (angle < 0) angle += Mth.TWO_PI;
            boolean dark = angle >= clearAngle;
            if (dark && !inDark) {
                darkStart = dx;
                inDark = true;
            } else if (!dark && inDark) {
                graphics.fill(cx + darkStart, y, cx + dx, y + 1, COOLDOWN_OVERLAY_COLOR);
                inDark = false;
            }
        }
        if (inDark) {
            graphics.fill(cx + darkStart, y, cx + dxTo + 1, y + 1, COOLDOWN_OVERLAY_COLOR);
        }
    }

    private static int scalePx(int sourcePx) {
        return Math.max(1, Math.round((sourcePx / (float) HUD_TEX_SIZE) * HUD_ON_SCREEN_SIZE));
    }

    private static String clampTextToWidth(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(8, maxWidth - font.width("..."))) + "...";
    }
}
