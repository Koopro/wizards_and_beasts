package at.koopro.wizardsandbeasts.client.legilimency;

import at.koopro.wizardsandbeasts.client.spell.SpellKeyBindings;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.network.legilimency.LegilimencyRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public final class LegilimencyClientController {
    private static final int HOLD_TICKS = 30;
    private static int heldTicks;
    private static boolean sentThisHold;

    private LegilimencyClientController() {
    }

    public static void onClientTick(Minecraft mc) {
        if (!ModuleManager.isEnabled(Module.PLAYER_ABILITIES)) {
            heldTicks = 0;
            sentThisHold = false;
            return;
        }
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null) {
            heldTicks = 0;
            sentThisHold = false;
            return;
        }
        if (!SpellKeyBindings.LEGILIMENCY.isDown()) {
            heldTicks = 0;
            sentThisHold = false;
            return;
        }
        heldTicks++;
        if (sentThisHold || heldTicks < HOLD_TICKS) {
            return;
        }
        HitResult hit = player.pick(8.0, 0.0f, false);
        if (hit instanceof EntityHitResult) {
            Entity target = ((EntityHitResult) hit).getEntity();
            ClientPacketDistributor.sendToServer(new LegilimencyRequestPayload(target.getId()));
            sentThisHold = true;
        }
    }
}
