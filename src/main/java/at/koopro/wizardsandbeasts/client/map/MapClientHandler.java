package at.koopro.wizardsandbeasts.client.map;

import at.koopro.wizardsandbeasts.network.MapOpenS2CPacket;
import at.koopro.wizardsandbeasts.network.MapSyncS2CPacket;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class MapClientHandler {

    public static void handleMapOpen(MapOpenS2CPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new MaraudersMapScreen(
                    pkt.center(), pkt.radius(), pkt.dimension()));
        });
    }

    public static void handleMapSync(MapSyncS2CPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof MaraudersMapScreen screen) {
                screen.updateEntities(pkt.entries());
            }
        });
    }
}
