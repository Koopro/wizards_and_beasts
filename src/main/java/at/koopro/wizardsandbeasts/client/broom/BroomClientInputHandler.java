package at.koopro.wizardsandbeasts.client.broom;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.entity.broom.BroomEntity;
import at.koopro.wizardsandbeasts.network.broom.BroomInputC2SPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public class BroomClientInputHandler {
    private static final int KEEPALIVE_INTERVAL_TICKS = 10;
    private static int inputSequence;
    private static int ticksSinceLastSend = KEEPALIVE_INTERVAL_TICKS;
    private static InputSnapshot lastSent = null;

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

        InputSnapshot current = new InputSnapshot(forward, backward, up, down, boosting, yaw, pitch);
        boolean changed = !current.equals(lastSent);
        ticksSinceLastSend++;
        if (!changed && ticksSinceLastSend < KEEPALIVE_INTERVAL_TICKS) {
            return;
        }

        inputSequence++;
        ticksSinceLastSend = 0;
        lastSent = current;
        ClientPacketDistributor.sendToServer(
                new BroomInputC2SPayload(inputSequence, forward, backward, up, down, boosting, yaw, pitch));
    }

    private record InputSnapshot(
            boolean forward,
            boolean backward,
            boolean up,
            boolean down,
            boolean boosting,
            float yaw,
            float pitch) {}
}
