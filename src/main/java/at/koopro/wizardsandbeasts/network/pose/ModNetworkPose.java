package at.koopro.wizardsandbeasts.network.pose;

import at.koopro.wizardsandbeasts.client.network.ClientPayloadHandlers;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class ModNetworkPose {

    private ModNetworkPose() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                PoseOverrideSyncS2CPayload.TYPE,
                PoseOverrideSyncS2CPayload.STREAM_CODEC,
                ClientPayloadHandlers::handlePoseOverrideSync);
    }
}
