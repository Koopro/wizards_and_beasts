package at.koopro.wizardsandbeasts.wand.allegiance;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

@EventBusSubscriber(modid = at.koopro.wizardsandbeasts.WizardsAndBeastsMod.MODID)
@Deprecated(forRemoval = false)
public final class AllegianceSystem {
    private AllegianceSystem() {
    }

    @SubscribeEvent
    public static void onLivingEquipmentChange(LivingEquipmentChangeEvent event) {
        WandDisarmAllegianceSystem.onLivingEquipmentChange(event);
    }

    @SubscribeEvent
    public static void onItemPickupPre(ItemEntityPickupEvent.Pre event) {
        WandDisarmAllegianceSystem.onItemPickupPre(event);
    }
}
