package at.koopro.wizardsandbeasts.client.network;

import at.koopro.wizardsandbeasts.entity.ProtegoShieldEntity;
import at.koopro.wizardsandbeasts.network.ProtegoAnimationS2CPacket;
import at.koopro.wizardsandbeasts.network.ProtegoSpawnS2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class ProtegoClientPacketHandlers {
    private ProtegoClientPacketHandlers() {}

    public static void handleSpawn(ProtegoSpawnS2CPacket pkt) {
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

    public static void handleAnimation(ProtegoAnimationS2CPacket pkt) {
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
