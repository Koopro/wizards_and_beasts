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
        registrar.playToClient(
                PlayerGestureS2CPayload.TYPE,
                PlayerGestureS2CPayload.STREAM_CODEC,
                ClientPayloadHandlers::handlePlayerGesture);
        registrar.playToServer(
                WandInspectC2SPayload.TYPE,
                WandInspectC2SPayload.STREAM_CODEC,
                WandInspectC2SPayload::handle);
    }
}
