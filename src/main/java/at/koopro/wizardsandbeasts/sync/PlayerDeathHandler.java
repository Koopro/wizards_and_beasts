package at.koopro.wizardsandbeasts.sync;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.wand.cast.WandCastingAllegianceSystem;
import at.koopro.wizardsandbeasts.util.WandHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class PlayerDeathHandler {
    private PlayerDeathHandler() {}

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer deadPlayer)) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) {
            return;
        }

        long gameTick = ((net.minecraft.server.level.ServerLevel) deadPlayer.level()).getGameTime();
        for (ItemEntity drop : event.getDrops()) {
            if (drop == null) continue;
            ItemStack stack = drop.getItem();
            if (!WandHelper.isWand(stack)) continue;
            WandCastingAllegianceSystem.transferTo(stack, killer.getUUID(), 0.3f, gameTick);
            drop.setItem(stack);
        }
    }
}
