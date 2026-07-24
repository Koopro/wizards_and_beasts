package at.koopro.wizardsandbeasts.event.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.spell.lib.ColloportusLockStore;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class ColloportusSealHandler {

    private ColloportusSealHandler() {
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        // Barricade: a sealed block cannot be broken while the Colloportus lock holds. It is undone by
        // Alohomora (at proficiency) or when the lock expires — not by brute force.
        if (ColloportusLockStore.isLocked(level, event.getPos())) {
            event.setCanceled(true);
        }
    }
}
