package at.koopro.wizardsandbeasts.spell.client.network;

import at.koopro.wizardsandbeasts.spell.entity.ProtegoShieldEntity;
import at.koopro.wizardsandbeasts.spell.network.ProtegoAnimationS2CPayload;
import at.koopro.wizardsandbeasts.spell.network.ProtegoSpawnS2CPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class ProtegoClientPacketHandlers {
    private ProtegoClientPacketHandlers() {}

    public static void handleSpawn(ProtegoSpawnS2CPayload pkt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Entity existing = mc.level.getEntity(pkt.entityId());
        if (existing != null) {
            return;
        }
        ProtegoShieldEntity shield = new ProtegoShieldEntity(mc.level);
        shield.setId(pkt.entityId());
        shield.configureClientSpawn(pkt.tier(), pkt.casterUUID(), new Vec3(pkt.x(), pkt.y(), pkt.z()));
        mc.level.addEntity(shield);
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
