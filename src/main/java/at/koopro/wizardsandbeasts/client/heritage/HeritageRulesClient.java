package at.koopro.wizardsandbeasts.client.heritage;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.heritage.rules.HeritageRule;
import at.koopro.wizardsandbeasts.heritage.rules.HeritageRules;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.jspecify.annotations.NullMarked;

import java.util.Map;

/**
 * Receives the server's heritage rules. On a remote server they become {@link HeritageRules}' remote layer; on
 * this client's own integrated server they are ignored, because the local layer already is that server's state.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class HeritageRulesClient {

    private HeritageRulesClient() {}

    public static void accept(Map<String, HeritageRule> rules) {
        if (Minecraft.getInstance().hasSingleplayerServer()) {
            return;
        }
        HeritageRules.acceptRemote(rules);
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        HeritageRules.clearRemote();
    }
}
