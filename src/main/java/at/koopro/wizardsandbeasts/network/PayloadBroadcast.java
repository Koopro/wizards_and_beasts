package at.koopro.wizardsandbeasts.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NullMarked;

/**
 * The two ways this mod pushes a whole-registry snapshot at clients.
 *
 * <p>Nine payloads each carried their own copy of one of these loops. They are here instead, so a
 * new datapack-backed registry gets its sync in one line and cannot get the {@code /reload} case
 * subtly wrong.
 */
@NullMarked
public final class PayloadBroadcast {

    private PayloadBroadcast() {
    }

    /**
     * Sends to whoever the datapack sync is for.
     *
     * <p>On player join {@link OnDatapackSyncEvent#getPlayer()} names the one arriving player. On
     * {@code /reload} it is {@code null} and every player needs the new data, so everyone gets it.
     */
    public static void toSyncTarget(OnDatapackSyncEvent event, CustomPacketPayload payload) {
        ServerPlayer joining = event.getPlayer();
        if (joining != null) {
            PacketDistributor.sendToPlayer(joining, payload);
            return;
        }
        for (ServerPlayer player : event.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /** Sends to every player currently connected. No-op on an empty server. */
    public static void toAll(MinecraftServer server, CustomPacketPayload payload) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
