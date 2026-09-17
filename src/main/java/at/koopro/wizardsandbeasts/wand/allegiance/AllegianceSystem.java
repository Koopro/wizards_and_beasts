package at.koopro.wizardsandbeasts.wand.allegiance;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

@EventBusSubscriber(modid = at.koopro.wizardsandbeasts.WizardsAndBeastsMod.MODID)
public final class AllegianceSystem {
    private AllegianceSystem() {
    }

    @SubscribeEvent
    public static void onItemPickupPre(ItemEntityPickupEvent.Pre event) {
        WandDisarmAllegianceSystem.onItemPickupPre(event);
    }
}
