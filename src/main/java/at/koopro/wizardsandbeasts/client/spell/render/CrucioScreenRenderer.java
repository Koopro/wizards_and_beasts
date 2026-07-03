package at.koopro.wizardsandbeasts.client.spell.render;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.spell.state.ClientSignatureSpellState;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

/**
 * Cruciatus desaturation overlay + brief screen shake (also used for Imperio caster feedback).
 */
public final class CrucioScreenRenderer {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "crucio_screen");

    private CrucioScreenRenderer() {}

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        if (at.koopro.wizardsandbeasts.Config.reduceScreenEffects) return;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();

        int shake = ClientSignatureSpellState.getAndDecayScreenShakeTicks();
        int ox = 0;
        int oy = 0;
        if (shake > 0) {
            ox = (int) ((mc.level.random.nextFloat() - 0.5f) * 4.0f);
            oy = (int) ((mc.level.random.nextFloat() - 0.5f) * 4.0f);
        }

        if (player.hasEffect(ModEffects.CRUCIATUS_PAIN)) {
            MobEffectInstance inst = player.getEffect(ModEffects.CRUCIATUS_PAIN);
            int amp = inst != null ? inst.getAmplifier() : 0;
            double pulsePhase = Math.sin(System.currentTimeMillis() * 0.01) * 0.5 + 0.5;
            float alpha = 0.3f + amp * 0.15f + (float) pulsePhase * 0.1f;
            int a = Mth.clamp((int) (alpha * 255), 0, 255);
            int grey = 0x808080;
            graphics.fill(ox, oy, w + ox, h + oy, (a << 24) | grey);
        } else if (shake > 0) {
            // Imperio / caster feedback: sub-pixel jitter substitute (no PoseStack on this GuiGraphics path).
            graphics.fill(ox, oy, w + ox, h + oy, 0x08FFFFFF);
        }
    }
}
