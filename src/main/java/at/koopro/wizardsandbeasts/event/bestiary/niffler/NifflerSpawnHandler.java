package at.koopro.wizardsandbeasts.event.bestiary.niffler;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

public final class NifflerSpawnHandler {

    private NifflerSpawnHandler() {}

    public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        // What the predicate tests, for the Creature Lab (a lambda cannot be read back).
        at.koopro.wizardsandbeasts.creature.rules.SpawnConditionNotes.note("niffler", "module_creatures", "solid_ground", "light_night");
        event.register(
                ModEntities.NIFFLER.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (entityType, level, spawnReason, pos, random) ->
                        ModuleManager.isEnabled(Module.CREATURES)
                                && level.getRawBrightness(pos, 0) <= 7
                                && level.getBlockState(pos.below()).isSolidRender(),
                RegisterSpawnPlacementsEvent.Operation.OR);

        event.register(
                ModEntities.BABY_NIFFLER.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (entityType, level, spawnReason, pos, random) ->
                        ModuleManager.isEnabled(Module.CREATURES)
                                && level.getRawBrightness(pos, 0) <= 7
                                && level.getBlockState(pos.below()).isSolidRender(),
                RegisterSpawnPlacementsEvent.Operation.OR);
    }
}
