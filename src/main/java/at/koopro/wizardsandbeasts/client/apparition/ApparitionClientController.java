package at.koopro.wizardsandbeasts.client.apparition;

import at.koopro.wizardsandbeasts.ability.AbilityIds;
import at.koopro.wizardsandbeasts.client.ability.state.ClientAbilityCache;
import at.koopro.wizardsandbeasts.client.ability.state.ClientAbilityChargeState;
import at.koopro.wizardsandbeasts.client.apparition.state.ClientApparitionWardState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;

/**
 * Apparition destination preview. Input moved to the ability wheel
 * ({@code AbilityWheelController} drives the hold-to-charge and the destination pick); this class is now
 * render-only and reads the framework's charge state, so the charge-up ring — gold when the destination is
 * viable, red when warded or unlicensed — survives the migration unchanged.
 */
public final class ApparitionClientController {

    private ApparitionClientController() {
    }

    public static void onRenderLevel(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        if (!ClientAbilityChargeState.isCharging(AbilityIds.APPARITION)
                || !ClientAbilityChargeState.isCharged()) {
            return;
        }
        Vec3 targetPos = ClientAbilityChargeState.targetPosition();
        if (targetPos == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        boolean warded = ClientApparitionWardState.isWarded(mc.level.dimension().identifier(), targetPos);
        boolean unlocked = ClientAbilityCache.get().apparitionUnlocked();
        Vector3f color = (unlocked && !warded) ? new Vector3f(1.0f, 0.85f, 0.2f) : new Vector3f(1.0f, 0.2f, 0.2f);
        for (int i = 0; i < 24; i++) {
            float a = (float) (Math.PI * 2.0 * (i / 24.0));
            Vec3 p = targetPos.add(Math.cos(a) * 0.7, 0.08, Math.sin(a) * 0.7);
            int rgb = ((int) (color.x() * 255.0f) << 16) | ((int) (color.y() * 255.0f) << 8) | (int) (color.z() * 255.0f);
            mc.level.addParticle(new DustParticleOptions(rgb, 1.0f), p.x, p.y, p.z, 0.0, 0.015, 0.0);
        }
    }
}
