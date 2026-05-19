package at.koopro.wizardsandbeasts.client.apparition;

import at.koopro.wizardsandbeasts.client.ability.state.ClientAbilityCache;
import at.koopro.wizardsandbeasts.client.apparition.state.ClientApparitionWardState;
import at.koopro.wizardsandbeasts.client.spell.SpellKeyBindings;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.network.apparition.ApparitionRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.joml.Vector3f;

public final class ApparitionClientController {
    private static final int CHARGE_TICKS = 20;

    private static int heldTicks;
    private static boolean wasHeld;
    private static Vec3 targetPos;
    private static BlockPos targetBlockPos;

    private ApparitionClientController() {
    }

    public static void onClientTick(Minecraft mc) {
        if (!ModuleManager.isEnabled(Module.PLAYER_ABILITIES)) {
            clear();
            return;
        }
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null) {
            clear();
            return;
        }
        boolean held = SpellKeyBindings.APPARATE.isDown();
        if (held) {
            heldTicks++;
            if (heldTicks >= CHARGE_TICKS) {
                updateTarget(player);
            }
        } else {
            if (wasHeld && heldTicks >= CHARGE_TICKS && targetPos != null && targetBlockPos != null) {
                if (ClientAbilityCache.get().apparitionUnlocked()) {
                    ClientPacketDistributor.sendToServer(new ApparitionRequestPayload(targetBlockPos, targetPos));
                }
            }
            clear();
        }
        wasHeld = held;
    }

    public static void onRenderLevel(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        if (heldTicks < CHARGE_TICKS || targetPos == null) {
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

    private static void updateTarget(LocalPlayer player) {
        HitResult hit = player.pick(32.0, 0.0f, false);
        if (hit instanceof BlockHitResult && hit.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hit;
            targetPos = blockHit.getLocation();
            targetBlockPos = blockHit.getBlockPos();
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        targetPos = eye.add(look.scale(32.0));
        targetBlockPos = BlockPos.containing(targetPos);
    }

    private static void clear() {
        heldTicks = 0;
        wasHeld = false;
        targetPos = null;
        targetBlockPos = null;
    }
}
