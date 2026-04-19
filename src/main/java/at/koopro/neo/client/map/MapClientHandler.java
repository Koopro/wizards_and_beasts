package at.koopro.neo.client.map;

import at.koopro.neo.network.MapOpenS2CPacket;
import at.koopro.neo.network.MapSyncPacket;
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

    public static void handleMapSync(MapSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof MaraudersMapScreen screen) {
                screen.updateEntities(pkt.entries());
            }
        });
    }
}
