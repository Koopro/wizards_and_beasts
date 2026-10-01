package at.koopro.wizardsandbeasts.client.spell.network;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.spell.tuning.SpellTuning;
import at.koopro.wizardsandbeasts.spell.tuning.SpellTuningSnapshot;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Receives the server's spell administration state.
 *
 * <p>On a remote server the state becomes {@link SpellTuning}'s remote layer. On this client's own integrated
 * server it is ignored: the local layer <em>is</em> that server's state, already current, and adopting a copy
 * that lags a packet behind would hand the server thread stale values in the same JVM.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class SpellTuningClient {

    private SpellTuningClient() {}

    public static void accept(SpellTuningSnapshot snapshot) {
        if (Minecraft.getInstance().hasSingleplayerServer()) {
            return;
        }
        SpellTuning.acceptRemote(snapshot);
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SpellTuning.clearRemote();
    }
}
