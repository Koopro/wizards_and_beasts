package at.koopro.wizardsandbeasts.entity.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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

    /** Power at/above which the Patronus takes a defined animal form (corporeal); below it is mist. */
    public static final float CORPOREAL_POWER = 40.0f;

    /** True once the Patronus has settled into a corporeal animal; false = a non-corporeal silver mist. */
    private static final EntityDataAccessor<Boolean> DATA_CORPOREAL =
            SynchedEntityData.defineId(PatronusEntity.class, EntityDataSerializers.BOOLEAN);
    /** Resolved form id (e.g. {@code minecraft:wolf}); drives which model the client renders. */
    private static final EntityDataAccessor<String> DATA_FORM_ID =
            SynchedEntityData.defineId(PatronusEntity.class, EntityDataSerializers.STRING);

    /** A non-corporeal mist fades faster than a full corporeal Patronus. */
    private static final int CORPOREAL_LIFESPAN = 600;
    private static final int MIST_LIFESPAN = 300;

    private UUID ownerUuid = new UUID(0L, 0L);
    private float patronusPower;
    private float proficiencyScalar;
    private float orbitAngle;

    public PatronusEntity(EntityType<? extends PatronusEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CORPOREAL, false);
        builder.define(DATA_FORM_ID, "");
    }

    public boolean isCorporeal() {
        return this.entityData.get(DATA_CORPOREAL);
    }

    public String getFormId() {
        return this.entityData.get(DATA_FORM_ID);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 40.0).add(Attributes.MOVEMENT_SPEED, 0.35);
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public static void trySpawn(ServerLevel level, ServerPlayer caster, float patronusPower,
                                float proficiencyScalar, String formId) {
        for (PatronusEntity existing : level.getEntitiesOfClass(PatronusEntity.class, caster.getBoundingBox().inflate(96))) {
            if (caster.getUUID().equals(existing.getOwnerUuid())) {
                existing.discard();
            }
        }
        PatronusEntity e = new PatronusEntity(ModEntities.PATRONUS.get(), level);
        e.ownerUuid = caster.getUUID();
        e.patronusPower = patronusPower;
        e.proficiencyScalar = proficiencyScalar;
        boolean corporeal = patronusPower >= CORPOREAL_POWER;
        e.entityData.set(DATA_CORPOREAL, corporeal);
        e.entityData.set(DATA_FORM_ID, corporeal && formId != null ? formId : "");
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

        // A non-corporeal Patronus is a silver mist: it guards its caster but cannot hunt down a
        // Dementor the way a corporeal form does. It still *exists* nearby, so PatronusDetection
        // keeps holding off the Kiss (lore: even a wisp buys a moment). It just hovers and wards.
        LivingEntity darkMob = isCorporeal() ? findNearestDarkMob(sl, owner, 20.0) : null;
        if (darkMob != null) {
            Vec3 to = darkMob.getEyePosition().subtract(getEyePosition());
            double dist = to.length();
            if (dist > 1e-4) {
                Vec3 dir = to.scale(1.0 / dist);
                setDeltaMovement(dir.scale(Math.min(2.2, 0.35 + patronusPower * 0.012)));
                move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
            }
            if (distanceToSqr(darkMob) < 2.25) {
                darkMob.hurt(sl.damageSources().magic(), 2.0f + patronusPower * 0.03f);
                darkMob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1, false, true, true));
                darkMob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 2, false, true, true));
                Vec3 kb = darkMob.position().subtract(owner.position()).normalize().scale(8.0);
                darkMob.setDeltaMovement(darkMob.getDeltaMovement().add(kb.x, 0.45, kb.z));
                darkMob.hurtMarked = true;
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

        if (tickCount > (isCorporeal() ? CORPOREAL_LIFESPAN : MIST_LIFESPAN)) {
            discard();
        }
    }

    private @Nullable LivingEntity findOwner(ServerLevel sl) {
        net.minecraft.world.entity.Entity e = sl.getEntity(ownerUuid);
        return e instanceof LivingEntity ? (LivingEntity) e : null;
    }

    private static @Nullable LivingEntity findNearestDarkMob(ServerLevel sl, LivingEntity owner, double range) {
        LivingEntity best = null;
        double bestD = range * range;
        for (LivingEntity le : sl.getEntitiesOfClass(LivingEntity.class,
                owner.getBoundingBox().inflate(range), LivingEntity::isAlive)) {
            // Dementors and any dark-aligned/undead creature (takes harm from healing) are fair game.
            if (le == owner || !(le.getType().is(DEMENTORS_TAG) || le.isInvertedHealAndHarm())) {
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

