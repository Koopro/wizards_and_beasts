package at.koopro.wizardsandbeasts.network.module;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jspecify.annotations.NullMarked;

/**
 * Payload registration for module state: the S2C snapshot and the C2S change request. Registered from
 * {@code ModNetwork#register}, matching every other domain registrar.
 */
@NullMarked
public final class ModNetworkModules {

    private ModNetworkModules() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                ModuleStateSyncPayload.TYPE,
                ModuleStateSyncPayload.STREAM_CODEC,
                ModuleStateSyncPayload::handle);
        registrar.playToServer(
                ModuleUpdateRequestPayload.TYPE,
                ModuleUpdateRequestPayload.STREAM_CODEC,
                ModuleUpdateRequestPayload::handle);
    }
}
