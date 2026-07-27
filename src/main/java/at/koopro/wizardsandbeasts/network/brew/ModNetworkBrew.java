package at.koopro.wizardsandbeasts.network.brew;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jspecify.annotations.NullMarked;

/**
 * Payload registration for brewing content: the S2C snapshot of brews and brewing recipes. Registered
 * from {@code ModNetwork#register}, matching every other domain registrar.
 */
@NullMarked
public final class ModNetworkBrew {

    private ModNetworkBrew() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                BrewDataSyncPayload.TYPE,
                BrewDataSyncPayload.STREAM_CODEC,
                BrewDataSyncPayload::handle);
    }
}
