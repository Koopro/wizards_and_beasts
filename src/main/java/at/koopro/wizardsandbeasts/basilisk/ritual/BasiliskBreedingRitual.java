package at.koopro.wizardsandbeasts.basilisk.ritual;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModCreatures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

import java.util.List;

/**
 * Dark-magic breeding ritual: a chicken egg thrown underground, during a thunderstorm, near a toad
 * hatches a basilisk instead of a chick — reuses vanilla's own thrown-egg-hatch mechanic as the
 * trigger (cancelling {@link ProjectileImpactEvent} pre-empts vanilla's {@code ThrownEgg#onHit}
 * entirely, including its chick roll) rather than a bespoke block/ritual UI.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class BasiliskBreedingRitual {

    private static final double TOAD_RADIUS = 2.0;
    private static final int MAX_Y = 40;

    private BasiliskBreedingRitual() {}

    @SubscribeEvent
    public static void onEggImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof ThrownEgg egg)) {
            return;
        }
        if (!(egg.level() instanceof ServerLevel level)) {
            return;
        }
        if (!ModuleManager.isEnabled(Module.CHAMBER_OF_SECRETS)) {
            return;
        }
        Vec3 impactPos = impactLocation(event.getRayTraceResult());
        if (impactPos == null) {
            return;
        }
        BlockPos pos = BlockPos.containing(impactPos);
        if (!level.isThundering() || level.canSeeSky(pos) || pos.getY() >= MAX_Y) {
            return;
        }

        GenericBeastEntity toad = findNearbyToad(level, impactPos);
        if (toad == null) {
            return;
        }

        event.setCanceled(true);
        toad.discard();

        var basiliskType = ModCreatures.ENTITIES.get("basilisk");
        if (basiliskType == null) {
            return;
        }
        Entity spawned = basiliskType.get().create(level, EntitySpawnReason.TRIGGERED);
        if (spawned instanceof GenericBeastEntity basilisk) {
            basilisk.setPos(impactPos.x, impactPos.y, impactPos.z);
            level.addFreshEntity(basilisk);
            level.playSound(null, pos, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0f, 0.6f);
            level.sendParticles(ParticleTypes.SOUL, impactPos.x, impactPos.y + 0.5, impactPos.z, 30, 0.4, 0.4, 0.4, 0.05);
        }
        egg.discard();
    }

    private static Vec3 impactLocation(HitResult result) {
        if (result instanceof BlockHitResult blockHit) {
            return blockHit.getLocation();
        }
        if (result instanceof EntityHitResult entityHit) {
            return entityHit.getLocation();
        }
        return null;
    }

    private static GenericBeastEntity findNearbyToad(ServerLevel level, Vec3 pos) {
        EntityType<?> toadType = ModCreatures.ENTITIES.containsKey("toad") ? ModCreatures.ENTITIES.get("toad").get() : null;
        if (toadType == null) {
            return null;
        }
        List<GenericBeastEntity> nearby = level.getEntitiesOfClass(GenericBeastEntity.class,
                new net.minecraft.world.phys.AABB(pos, pos).inflate(TOAD_RADIUS),
                e -> e.isAlive() && e.getType() == toadType);
        return nearby.isEmpty() ? null : nearby.get(0);
    }
}
