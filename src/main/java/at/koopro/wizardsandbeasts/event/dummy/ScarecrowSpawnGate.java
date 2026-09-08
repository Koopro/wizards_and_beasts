package at.koopro.wizardsandbeasts.event.dummy;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import org.jspecify.annotations.NullMarked;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.entity.dummy.DuellingDummyEntity;

/**
 * A duelling dummy wearing a carved pumpkin keeps hostile spawns out of the yard around it.
 *
 * <p>{@link FinalizeSpawnEvent} rather than a per-tick sweep that kills what has already appeared:
 * a mob that spawns and is then deleted has already made its sound, and on a busy server the sweep
 * would run whether or not a scarecrow existed. Here the cost is paid only by spawns that are
 * actually near one.
 *
 * <p>Only natural spawning is gated. A spawn egg, a spawner or a command is somebody asking for
 * that mob on purpose, and a decoration in the same chunk is no reason to refuse them.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class ScarecrowSpawnGate {

    private ScarecrowSpawnGate() {}

    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!Config.dummyScarecrow || !(event.getEntity() instanceof Enemy)) {
            return;
        }
        EntitySpawnReason reason = event.getSpawnType();
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) {
            return;
        }
        if (!(event.getLevel().getLevel() instanceof ServerLevel level)) {
            return;
        }
        Vec3 where = new Vec3(event.getX(), event.getY(), event.getZ());
        double radius = Config.dummyScareRadius;
        AABB search = AABB.ofSize(where, radius * 2, radius * 2, radius * 2);
        for (DuellingDummyEntity dummy : level.getEntitiesOfClass(DuellingDummyEntity.class, search)) {
            if (dummy.suppressesSpawnsAt(where)) {
                event.setSpawnCancelled(true);
                return;
            }
        }
    }
}
