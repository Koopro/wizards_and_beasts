package at.koopro.wizardsandbeasts.wand;

import at.koopro.wizardsandbeasts.wand.ollivander.OllivanderPoolLoader;
import at.koopro.wizardsandbeasts.wand.resonance.WandResonanceConfigLoader;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

@EventBusSubscriber(modid = at.koopro.wizardsandbeasts.WizardsAndBeastsMod.MODID)
public final class WandServerLifecycle {

    private WandServerLifecycle() {
    }

    @SubscribeEvent
    public static void onServerStarting(ServerAboutToStartEvent event) {
        WandResonanceConfigLoader.load(event.getServer());
        OllivanderPoolLoader.load(event.getServer());
    }
}
