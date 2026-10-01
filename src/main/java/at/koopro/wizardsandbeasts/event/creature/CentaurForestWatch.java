package at.koopro.wizardsandbeasts.event.creature;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.creature.ability.ForestKeeper;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Tells nearby centaurs when someone fells a tree in their forest ({@link ForestKeeper}). */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class CentaurForestWatch {

    private CentaurForestWatch() {}

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getState().is(BlockTags.LOGS)
                && ModuleManager.isEnabled(Module.CREATURES)) {
            ForestKeeper.treeFelled(level, event.getPos(), event.getPlayer());
        }
    }
}
