package at.koopro.wizardsandbeasts.client.spell.network;

import at.koopro.wizardsandbeasts.entity.spell.ProtegoShieldEntity;
import at.koopro.wizardsandbeasts.client.spell.protego.ClientProtegoChargeState;
import at.koopro.wizardsandbeasts.network.spell.ProtegoAnimationS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.ProtegoChargeS2CPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * Client side of the Shield Charm.
 *
 * <p>Spawning is left to vanilla entity tracking. There used to be a {@code ProtegoSpawnS2CPayload}
 * that built a shield on the client by hand as well; it raced the tracker's own add packet and left
 * a second, untracked copy of the entity behind — which now matters, because the shield's integrity,
 * planted flag and ending all ride synched entity data that only the tracked copy receives.
 */
public final class ProtegoClientPacketHandlers {
    private ProtegoClientPacketHandlers() {}

    public static void handleCharge(ProtegoChargeS2CPayload pkt) {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        ClientProtegoChargeState.accept(pkt.tier(), pkt.progress(), pkt.planting(), pkt.capped());
    }

    public static void handleAnimation(ProtegoAnimationS2CPayload pkt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Entity entity = mc.level.getEntity(pkt.entityId());
        if (entity instanceof ProtegoShieldEntity shield) {
            shield.triggerAnim("shield_controller", pkt.animationTrigger());
        }
    }
}
