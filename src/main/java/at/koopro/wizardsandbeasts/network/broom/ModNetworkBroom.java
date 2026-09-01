package at.koopro.wizardsandbeasts.network.broom;

import at.koopro.wizardsandbeasts.client.broom.ClientBroomDefinitions;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworkBroom {

    private ModNetworkBroom() {}

    public static void register(PayloadRegistrar registrar) {
        // The definition table itself. The entity syncs only its DEFINITION_ID, which is a key into
        // this map -- and the map is loaded by a server reload listener, so without this the client
        // side of a dedicated server has no table to look the key up in.
        registrar.playToClient(
                BroomDefinitionsSyncS2CPayload.TYPE,
                BroomDefinitionsSyncS2CPayload.STREAM_CODEC,
                ClientBroomDefinitions::handle);
        registrar.playToServer(
                BroomInputC2SPayload.TYPE,
                BroomInputC2SPayload.STREAM_CODEC,
                BroomInputC2SPayload::handle);
    }
}
