package at.koopro.wizardsandbeasts.client.hud;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/**
 * Full-screen tint layers for select mob effects (client-only).
 */
public final class MobEffectFullscreenOverlays {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "mob_effect_fullscreen");

    private MobEffectFullscreenOverlays() {}

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        if (at.koopro.wizardsandbeasts.Config.reduceScreenEffects) return;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        int tick = player.tickCount;

        if (player.hasEffect(ModEffects.OBSCURO)) {
            graphics.fill(0, 0, w, h, 0xF0000000);
            return;
        }

        if (player.hasEffect(ModEffects.FURNUNCULUS)) {
            int edge = Math.min(48, w / 8);
            int c = (0x33 << 24) | 0x663300;
            graphics.fill(0, 0, w, edge, c);
            graphics.fill(0, h - edge, w, h, c);
            graphics.fill(0, 0, edge, h, c);
            graphics.fill(w - edge, 0, w, h, c);
        }

        if (player.hasEffect(ModEffects.CONFUNDO)) {
            int phase = (tick / 10) % 2;
            int tint = phase == 0 ? 0x18FF0000 : 0x180000FF;
            graphics.fill(0, 0, w, h, tint);
        }

        var chillEffect = player.getEffect(ModEffects.DEMENTOR_CHILL);
        if (chillEffect != null) {
            int amp = chillEffect.getAmplifier(); // 0–3
            // Dark blue-grey vignette scaled by amplifier (alpha 0x20..0x50)
            int alpha = 0x20 + amp * 0x10;
            int color = (alpha << 24) | 0x1A2A4A;
            int edge = Math.min(80 + amp * 24, w / 3);
            graphics.fill(0, 0, w, edge, color);
            graphics.fill(0, h - edge, w, h, color);
            graphics.fill(0, edge, edge, h - edge, color);
            graphics.fill(w - edge, edge, w, h - edge, color);
        }

        if (player.hasEffect(ModEffects.SOUL_DRAINED)) {
            // Pale grey full-screen wash
            int pulse = (int)(Math.sin(tick * 0.03) * 8 + 24);
            graphics.fill(0, 0, w, h, (pulse << 24) | 0xE0E0E0);
        }

        var gazeLock = player.getEffect(ModEffects.BASILISK_GAZE_LOCK);
        if (gazeLock != null) {
            boolean meetsGaze = gazeLock.getAmplifier() >= 1;
            float progress = 1.0f - Math.min(1.0f, Math.max(0.0f,
                    gazeLock.getDuration() / (float) at.koopro.wizardsandbeasts.effect.BasiliskGazeLockEffect.WINDUP_TICKS));
            int edge = (int) (60 + progress * 120);
            int pulse = (int) (Math.sin(tick * 0.6) * 0x20 + 0x60 + progress * 0x60);
            int color = meetsGaze ? ((pulse << 24) | 0x8B0000) : ((pulse << 24) | 0x707070);
            graphics.fill(0, 0, w, edge, color);
            graphics.fill(0, h - edge, w, h, color);
            graphics.fill(0, edge, edge, h - edge, color);
            graphics.fill(w - edge, edge, w, h - edge, color);
        }
    }
}
