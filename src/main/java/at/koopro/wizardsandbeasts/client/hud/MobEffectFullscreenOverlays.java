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
    }
}
