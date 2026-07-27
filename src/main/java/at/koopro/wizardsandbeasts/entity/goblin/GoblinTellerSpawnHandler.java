package at.koopro.wizardsandbeasts.entity.goblin;

import at.koopro.wizardsandbeasts.registry.ModEntities;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

/**
 * Underground spawn placement for the Gringotts teller.
 *
 * <p>The teller is the only way into the vault screen — deposits, withdrawals and the knut/sickle/galleon
 * exchange all live behind {@link GoblinTellerEntity#mobInteract}. It had no spawn rule and no craftable
 * egg, so in survival wizarding money could be found but never banked or exchanged. Tellers keep to the
 * deep cave biomes, the closest thing to Gringotts' tunnels until the bank structure itself lands.
 *
 * <p>Not gated on {@code Module.CREATURES}: the teller is currency infrastructure, not a beast.
 */
public final class GoblinTellerSpawnHandler {

    private GoblinTellerSpawnHandler() {}

    public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(
                ModEntities.GOBLIN_TELLER.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (entityType, level, spawnReason, pos, random) ->
                        level.getRawBrightness(pos, 0) <= 7
                                && level.getBlockState(pos.below()).isSolidRender(),
                RegisterSpawnPlacementsEvent.Operation.OR);
    }
}
