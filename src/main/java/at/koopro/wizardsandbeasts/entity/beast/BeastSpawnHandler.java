package at.koopro.wizardsandbeasts.entity.beast;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Surface spawn placement for the wizarding beasts. Actual biome inclusion (weight,
 * pack size, which biomes) is data-driven via {@code neoforge:add_spawns} biome
 * modifiers under {@code data/wizards_and_beasts/neoforge/biome_modifier/}.
 *
 * <p>Placement here only governs <em>where on a column</em> a chosen spawn may land
 * and a light/solidity gate. All beasts are gated behind {@link Module#CREATURES}.
 */
public final class BeastSpawnHandler {

    private BeastSpawnHandler() {}

    public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        // Daytime surface dwellers.
        daySpawn(event, ModEntities.BOWTRUCKLE);
        daySpawn(event, ModEntities.CORNISH_PIXIE);
        daySpawn(event, ModEntities.AUGUREY);
        daySpawn(event, ModEntities.PHOENIX);
        daySpawn(event, ModEntities.STREELER);
        // Nocturnal / dim dwellers.
        nightSpawn(event, ModEntities.MOONCALF);
        nightSpawn(event, ModEntities.THESTRAL);
    }

    private static void daySpawn(RegisterSpawnPlacementsEvent event,
                                 DeferredHolder<EntityType<?>, ? extends EntityType<? extends PathfinderMob>> holder) {
        register(event, holder, true);
    }

    private static void nightSpawn(RegisterSpawnPlacementsEvent event,
                                   DeferredHolder<EntityType<?>, ? extends EntityType<? extends PathfinderMob>> holder) {
        register(event, holder, false);
    }

    private static void register(RegisterSpawnPlacementsEvent event,
                                 DeferredHolder<EntityType<?>, ? extends EntityType<? extends PathfinderMob>> holder,
                                 boolean daytime) {
        event.register(
                holder.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (entityType, level, spawnReason, pos, random) -> {
                    if (!ModuleManager.isEnabled(Module.CREATURES)) {
                        return false;
                    }
                    if (!level.getBlockState(pos.below()).isSolidRender()) {
                        return false;
                    }
                    int light = level.getRawBrightness(pos, 0);
                    return daytime ? light >= 7 : light <= 7;
                },
                RegisterSpawnPlacementsEvent.Operation.OR);
    }
}
