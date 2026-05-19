package at.koopro.wizardsandbeasts.client.map;

import at.koopro.wizardsandbeasts.network.map.MapOpenS2CPayload;
import at.koopro.wizardsandbeasts.network.map.MapSyncS2CPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class MapClientHandler {

    public static void handleMapOpen(MapOpenS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new MaraudersMapScreen(
                    pkt.center(), pkt.radius(), pkt.dimension()));
        });
    }

    public static void handleMapSync(MapSyncS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof MaraudersMapScreen screen) {
                screen.updateEntities(pkt.entries());
            }
        });
    }
}
