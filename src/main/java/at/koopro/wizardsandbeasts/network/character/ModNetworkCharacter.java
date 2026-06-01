package at.koopro.wizardsandbeasts.network.character;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworkCharacter {

    private ModNetworkCharacter() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                OpenCharacterSheetPayload.TYPE,
                OpenCharacterSheetPayload.STREAM_CODEC,
                OpenCharacterSheetPayload::handleClient);
    }
}
