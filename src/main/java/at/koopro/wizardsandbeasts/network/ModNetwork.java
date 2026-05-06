package at.koopro.wizardsandbeasts.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class ModNetwork {

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        ModNetworkBroom.register(registrar);
        ModNetworkMap.register(registrar);
        ModNetworkSpells.register(registrar);
        ModNetworkVault.register(registrar);
        ModNetworkType.register(registrar);
        ModNetworkForm.register(registrar);
        ModNetworkAbilities.register(registrar);
        ModNetworkBeamDebug.register(registrar);
        ModNetworkSkills.register(registrar);
        ModNetworkTeacher.register(registrar);
        ModNetworkWand.register(registrar);
        ModNetworkBestiary.register(registrar);
        ModNetworkPocket.register(registrar);
    }
}
