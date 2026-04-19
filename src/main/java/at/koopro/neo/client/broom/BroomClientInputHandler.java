package at.koopro.neo.client.broom;

import at.koopro.neo.Neo;
import at.koopro.neo.entity.BroomEntity;
import at.koopro.neo.network.BroomInputPacket;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

@EventBusSubscriber(modid = Neo.MODID, value = Dist.CLIENT)
public class BroomClientInputHandler {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (!(mc.player.getVehicle() instanceof BroomEntity broom)) return;

        boolean forward  = mc.options.keyUp.isDown();
        boolean backward = mc.options.keyDown.isDown();
        boolean up       = mc.options.keyJump.isDown();
        boolean down     = mc.options.keyShift.isDown();
        boolean boosting = mc.options.keySprint.isDown();
        float yaw   = mc.player.getYRot();
        float pitch = mc.player.getXRot();

        broom.setInput(forward, backward, up, down, boosting, yaw, pitch);

        ClientPacketDistributor.sendToServer(
                new BroomInputPacket(forward, backward, up, down, boosting, yaw, pitch));
    }
}
