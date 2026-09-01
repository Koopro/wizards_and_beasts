package at.koopro.wizardsandbeasts.map;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.map.discovery.MapDiscoveryRule;
import at.koopro.wizardsandbeasts.map.discovery.MapDiscoveryRules;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * The two things in the wider world the map has to be told about.
 *
 * <p>Kept out of {@link MapSurveyor} because neither is a survey: they are notifications that
 * something the map already recorded is now wrong, and folding them into the tick pass would mean
 * the tick pass carried handlers for events it does not fire on.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class MapWorldEvents {

    private MapWorldEvents() {
    }

    /**
     * Marks the grave.
     *
     * <p>{@code LivingDeathEvent} rather than the drop or respawn events: this has to read the
     * position the player died at, and by respawn time that is gone.
     */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!ModuleManager.isEnabled(Module.ARTEFACTS)) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player
                && player.level() instanceof ServerLevel level) {
            MapMarkerService.recordDeath(player, level);
        }
    }

    /**
     * Re-opens the landmark question for a chunk whose palette just changed.
     *
     * <p>A landmark is a count of blocks, so the count is only ever wrong right after someone has
     * changed it. Watching placement and breakage is exact and costs a tag test on blocks that are
     * almost never landmark stone; the alternative — periodically re-scanning explored chunks — is
     * a background sweep of the whole world to catch something that fires a few dozen times a
     * build.
     */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        noteLandmarkChange(event.getLevel(), event.getPlacedBlock(), event.getPos());
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        noteLandmarkChange(event.getLevel(), event.getState(), event.getPos());
    }

    private static void noteLandmarkChange(LevelAccessor level, BlockState state, BlockPos pos) {
        if (!ModuleManager.isEnabled(Module.ARTEFACTS) || MapDiscoveryRules.blocks().isEmpty()) {
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        for (MapDiscoveryRule.FromBlocks rule : MapDiscoveryRules.blocks()) {
            if (state.is(rule.blocks())) {
                MapSurveyor.queueLandmarkRescan(
                        serverLevel.dimension().identifier(), ChunkPos.asLong(pos));
                return;
            }
        }
    }
}
