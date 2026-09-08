package at.koopro.wizardsandbeasts.network.armor;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jspecify.annotations.NullMarked;

/** Payload registration for worn gear. Registered from {@code ModNetwork#register}. */
@NullMarked
public final class ModNetworkArmor {

    private ModNetworkArmor() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(
                HoodToggleC2SPayload.TYPE,
                HoodToggleC2SPayload.STREAM_CODEC,
                HoodToggleC2SPayload::handle);
    }
}
