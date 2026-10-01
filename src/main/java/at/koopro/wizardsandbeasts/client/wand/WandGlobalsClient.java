package at.koopro.wizardsandbeasts.client.wand;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.wand.rules.WandGlobals;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.jspecify.annotations.NullMarked;

/** Receives a remote server's wand rules; ignored on the client's own integrated server. */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class WandGlobalsClient {

    private WandGlobalsClient() {}

    public static void accept(WandGlobals.Values values) {
        if (Minecraft.getInstance().hasSingleplayerServer()) {
            return;
        }
        WandGlobals.acceptRemote(values);
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        WandGlobals.clearRemote();
    }
}
