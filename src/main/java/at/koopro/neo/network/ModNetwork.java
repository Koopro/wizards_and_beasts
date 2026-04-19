package at.koopro.neo.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class ModNetwork {

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        ModNetworkBroom.register(registrar);
        ModNetworkMap.register(registrar);
        ModNetworkSpells.register(registrar);
        ModNetworkVault.register(registrar);
        ModNetworkWizType.register(registrar);
        ModNetworkForm.register(registrar);
        ModNetworkBeamDebug.register(registrar);
        ModNetworkSkills.register(registrar);
    }
}
