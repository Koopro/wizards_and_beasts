package at.koopro.wizardsandbeasts.event.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.item.wand.ExpelliarmusDropTag;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.TriState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class ExpelliarmusPickupHandler {

    private ExpelliarmusPickupHandler() {}

    @SubscribeEvent
    public static void onPickupPre(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        var tag = event.getItemEntity().getItem().get(ModDataComponents.EXPELLIARMUS_DROP.get());
        if (tag == null) {
            return;
        }
        if (!player.getUUID().equals(tag.victimUuid())) {
            return;
        }
        if (!player.hasEffect(ModEffects.EXPELLIARMUS_DISARMED)) {
            return;
        }
        event.setCanPickup(TriState.FALSE);
    }
}
