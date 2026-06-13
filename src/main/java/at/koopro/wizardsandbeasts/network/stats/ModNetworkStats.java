package at.koopro.wizardsandbeasts.network.stats;

import at.koopro.wizardsandbeasts.client.network.ClientPayloadHandlers;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworkStats {

    private ModNetworkStats() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                PlayerStatsSyncPayload.TYPE,
                PlayerStatsSyncPayload.STREAM_CODEC,
                ClientPayloadHandlers::handlePlayerStatsSync);
    }
}
