package at.koopro.wizardsandbeasts.entity.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Ephemeral Patronus — server-driven motion, no persistent save.
 */
public class PatronusEntity extends Mob {

    public static final TagKey<EntityType<?>> DEMENTORS_TAG =
            TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "dementors"));

    private UUID ownerUuid = new UUID(0L, 0L);
    private float patronusPower;
    private float proficiencyScalar;
    private float orbitAngle;

    public PatronusEntity(EntityType<? extends PatronusEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 40.0).add(Attributes.MOVEMENT_SPEED, 0.35);
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public static void trySpawn(ServerLevel level, ServerPlayer caster, float patronusPower, float proficiencyScalar) {
        for (PatronusEntity existing : level.getEntitiesOfClass(PatronusEntity.class, caster.getBoundingBox().inflate(96))) {
            if (caster.getUUID().equals(existing.getOwnerUuid())) {
                existing.discard();
            }
        }
        PatronusEntity e = new PatronusEntity(ModEntities.PATRONUS.get(), level);
        e.ownerUuid = caster.getUUID();
        e.patronusPower = patronusPower;
        e.proficiencyScalar = proficiencyScalar;
        e.setPos(caster.getX(), caster.getY() + 1.0, caster.getZ());
        level.addFreshEntity(e);
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            if (tickCount % 5 == 0) {
                level().addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD,
                        getX(), getY(), getZ(), 0.0, 0.02, 0.0);
            }
            return;
        }
        if (!(level() instanceof ServerLevel)) {
            discard();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        LivingEntity owner = findOwner(sl);
        if (owner == null || !owner.isAlive()) {
            discard();
            return;
        }

        LivingEntity dementor = findNearestDementor(sl, owner, 20.0);
        if (dementor != null) {
            Vec3 to = dementor.getEyePosition().subtract(getEyePosition());
            double dist = to.length();
            if (dist > 1e-4) {
                Vec3 dir = to.scale(1.0 / dist);
                setDeltaMovement(dir.scale(Math.min(2.2, 0.35 + patronusPower * 0.012)));
                move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
            }
            if (distanceToSqr(dementor) < 2.25) {
                dementor.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1, false, true, true));
                dementor.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 2, false, true, true));
                Vec3 kb = dementor.position().subtract(owner.position()).normalize().scale(8.0);
                dementor.setDeltaMovement(dementor.getDeltaMovement().add(kb.x, 0.45, kb.z));
                dementor.hurtMarked = true;
            }
        } else {
            orbitAngle += 0.8 + proficiencyScalar * 0.4;
            double radius = 3.0 + patronusPower * 0.05;
            double ox = owner.getX() + Math.cos(orbitAngle) * radius;
            double oz = owner.getZ() + Math.sin(orbitAngle) * radius;
            setPos(ox, owner.getY() + 1.2 + Math.sin(tickCount * 0.08) * 0.15, oz);
            setDeltaMovement(Vec3.ZERO);

            for (LivingEntity hostile : sl.getEntitiesOfClass(LivingEntity.class,
                    new AABB(owner.position(), owner.position()).inflate(6.0),
                    le -> le != owner && le instanceof Monster)) {
                if (tickCount % 20 == 0) {
                    hostile.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0, false, true, true));
                }
            }
        }

        if (tickCount > 600) {
            discard();
        }
    }

    private @Nullable LivingEntity findOwner(ServerLevel sl) {
        net.minecraft.world.entity.Entity e = sl.getEntity(ownerUuid);
        return e instanceof LivingEntity ? (LivingEntity) e : null;
    }

    private static @Nullable LivingEntity findNearestDementor(ServerLevel sl, LivingEntity owner, double range) {
        LivingEntity best = null;
        double bestD = range * range;
        for (LivingEntity le : sl.getEntitiesOfClass(LivingEntity.class,
                owner.getBoundingBox().inflate(range), LivingEntity::isAlive)) {
            if (!le.getType().is(DEMENTORS_TAG)) {
                continue;
            }
            double d = owner.distanceToSqr(le);
            if (d < bestD) {
                bestD = d;
                best = le;
            }
        }
        return best;
    }
}

