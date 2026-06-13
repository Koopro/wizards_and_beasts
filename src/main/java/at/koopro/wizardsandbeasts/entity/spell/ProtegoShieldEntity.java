package at.koopro.wizardsandbeasts.entity.spell;

import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.network.spell.ProtegoAnimationS2CPayload;
import at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import at.koopro.wizardsandbeasts.registry.ModParticles;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellFamilies;
import at.koopro.wizardsandbeasts.spell.core.SpellFamily;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.protego.ProtegoWardManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.animation.state.AnimationTest;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.UUID;

public class ProtegoShieldEntity extends Entity implements GeoEntity {
    private static final EntityDataAccessor<Integer> DATA_TIER =
            SynchedEntityData.defineId(ProtegoShieldEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_DEFLECTIONS_REMAINING =
            SynchedEntityData.defineId(ProtegoShieldEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_CASTER_UUID =
            SynchedEntityData.defineId(ProtegoShieldEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DATA_IS_SHATTERING =
            SynchedEntityData.defineId(ProtegoShieldEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<BlockPos> DATA_ANCHOR_POS =
            SynchedEntityData.defineId(ProtegoShieldEntity.class, EntityDataSerializers.BLOCK_POS);

    private static final RawAnimation IDLE_DISC = RawAnimation.begin().thenLoop("idle_disc");
    private static final RawAnimation IDLE_DOME = RawAnimation.begin().thenLoop("idle_dome");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    private int ticksAlive;
    private int shatterTicks;
    private int maxLifetime = 80;

    public ProtegoShieldEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
    }

    public ProtegoShieldEntity(Level level) {
        this(ModEntities.PROTEGO_SHIELD.get(), level);
    }

    public ProtegoShieldEntity(Level level, ServerPlayer caster, int tier) {
        this(ModEntities.PROTEGO_SHIELD.get(), level);
        int clampedTier = Mth.clamp(tier, 0, 3);
        this.entityData.set(DATA_TIER, clampedTier);
        this.entityData.set(DATA_DEFLECTIONS_REMAINING, maxDeflectionsFor(clampedTier));
        this.entityData.set(DATA_CASTER_UUID, caster.getUUID().toString());
        this.entityData.set(DATA_ANCHOR_POS, caster.blockPosition());
        this.maxLifetime = computeMaxLifetime(clampedTier, Spells.PROTEGO.getProficiencyScalar(caster));
        this.setPos(caster.position());
        if (clampedTier >= 2) {
            this.setPos(Vec3.atCenterOf(caster.blockPosition()));
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_TIER, 0);
        builder.define(DATA_DEFLECTIONS_REMAINING, 1);
        builder.define(DATA_CASTER_UUID, "");
        builder.define(DATA_IS_SHATTERING, false);
        builder.define(DATA_ANCHOR_POS, BlockPos.ZERO);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        ticksAlive++;
        ServerLevel serverLevel = (ServerLevel) level();

        if (isShattering()) {
            shatterTicks++;
            if (shatterTicks >= 20) {
                discard();
            }
            return;
        }
        if (ticksAlive > maxLifetime) {
            beginShatter();
            return;
        }

        if (!updatePositionFromTier(serverLevel)) {
            beginShatter();
            return;
        }

        runInterception(serverLevel);
    }

    private boolean updatePositionFromTier(ServerLevel serverLevel) {
        int tier = getTier();
        if (tier >= 2) {
            setPos(Vec3.atCenterOf(getAnchorPos()));
            return true;
        }
        ServerPlayer caster = getCaster(serverLevel);
        if (caster == null || !caster.isAlive() || caster.getEffect(ModEffects.PROTEGO_SHIELD) == null) {
            return false;
        }
        if (tier == 0) {
            Vec3 front = caster.getEyePosition().add(caster.getLookAngle().scale(0.9));
            setPos(front);
            setYRot(caster.getYRot());
            setXRot(caster.getXRot());
            return true;
        }
        setPos(caster.position().add(0.0, 0.5, 0.0));
        return true;
    }

    private void runInterception(ServerLevel level) {
        double radius = interceptRadius(getTier());
        AABB area = AABB.ofSize(position(), radius * 2.0, radius * 2.0, radius * 2.0);
        List<SpellProjectileEntity> candidates =
                level.getEntitiesOfClass(SpellProjectileEntity.class, area, Entity::isAlive);
        for (SpellProjectileEntity projectile : candidates) {
            if (isShattering()) {
                return;
            }
            if (projectile.position().distanceToSqr(position()) > radius * radius) {
                continue;
            }
            UUID casterId = getCasterUuid();
            if (casterId != null && casterId.equals(projectile.getCasterUuid())) {
                continue;
            }
            Spell spell = projectile.getCachedOrResolveSpell();
            if (spell == null) {
                continue;
            }
            if (spell.isUnblockable()) {
                level.sendParticles(ModParticles.AK_BYPASS_FLASH.get(),
                        projectile.getX(), projectile.getY(), projectile.getZ(),
                        8, 0.12, 0.12, 0.12, 0.01);
                continue;
            }
            if (getTier() == 3 && SpellFamilies.of(spell) == SpellFamily.DARK) {
                projectile.discard();
                level.sendParticles(ModParticles.PROTEGO_SHATTER.get(),
                        projectile.getX(), projectile.getY(), projectile.getZ(),
                        14, 0.2, 0.2, 0.2, 0.03);
                level.playSound(null, blockPosition(), ModSounds.PROTEGO_HORRIBILIS_ABSORB.get(),
                        SoundSource.PLAYERS, 0.85f, 0.92f);
                ProtegoAnimationS2CPayload.sendToTracking(this, "absorb");
                continue;
            }
            deflect(level, projectile);
        }
    }

    private void deflect(ServerLevel level, SpellProjectileEntity projectile) {
        Vec3 motion = projectile.getDeltaMovement();
        if (motion.lengthSqr() < 1.0e-6) {
            return;
        }
        Vec3 normal = position().subtract(projectile.position()).normalize();
        Vec3 reflected = motion.subtract(normal.scale(2.0 * motion.dot(normal)));
        projectile.setDeltaMovement(reflected);
        ServerPlayer caster = getCaster(level);
        if (caster != null) {
            projectile.setOwner(caster);
        }
        int tint = getTier() == 3 ? 0xFF6A0DAD : 0xFF4169E1;
        SpellTintParticleOptions options = new SpellTintParticleOptions(ModParticles.PROTEGO_DEFLECT.get(), tint);
        level.sendParticles(options, projectile.getX(), projectile.getY(), projectile.getZ(),
                16, 0.15, 0.15, 0.15, 0.02);
        level.playSound(null, projectile.blockPosition(), ModSounds.PROTEGO_BLOCK.get(),
                SoundSource.PLAYERS, 0.8f, 1.0f + (getTier() * 0.1f));
        ProtegoAnimationS2CPayload.sendToTracking(this, "deflect");

        int remaining = Math.max(0, getDeflectionsRemaining() - 1);
        this.entityData.set(DATA_DEFLECTIONS_REMAINING, remaining);
        if (remaining <= 0) {
            beginShatter();
        }
    }

    public void beginShatter() {
        if (isShattering()) {
            return;
        }
        this.entityData.set(DATA_IS_SHATTERING, true);
        this.entityData.set(DATA_DEFLECTIONS_REMAINING, 0);
        this.shatterTicks = 0;
        if (level() instanceof ServerLevel serverLevel) {
            double spread = Math.max(1.0, interceptRadius(getTier()) * 0.35);
            serverLevel.sendParticles(ModParticles.PROTEGO_SHATTER.get(),
                    getX(), getY(), getZ(), 40, spread, spread, spread, 0.01);
            serverLevel.playSound(null, blockPosition(), ModSounds.PROTEGO_SHATTER.get(),
                    SoundSource.PLAYERS, 1.2f, 0.8f + (getTier() * 0.05f));
            ServerPlayer caster = getCaster(serverLevel);
            if (caster != null && caster.getEffect(ModEffects.PROTEGO_SHIELD) != null) {
                caster.removeEffect(ModEffects.PROTEGO_SHIELD);
                ProtegoWardManager.remove(caster.getUUID());
            }
            ProtegoAnimationS2CPayload.sendToTracking(this, "shatter");
        }
    }

    public void configureClientSpawn(int tier, UUID casterUuid, Vec3 pos) {
        this.entityData.set(DATA_TIER, Mth.clamp(tier, 0, 3));
        this.entityData.set(DATA_CASTER_UUID, casterUuid == null ? "" : casterUuid.toString());
        this.entityData.set(DATA_ANCHOR_POS, BlockPos.containing(pos));
        this.setPos(pos);
    }

    public int getTier() {
        return this.entityData.get(DATA_TIER);
    }

    public boolean isShattering() {
        return this.entityData.get(DATA_IS_SHATTERING);
    }

    public int getDeflectionsRemaining() {
        return this.entityData.get(DATA_DEFLECTIONS_REMAINING);
    }

    public BlockPos getAnchorPos() {
        return this.entityData.get(DATA_ANCHOR_POS);
    }

    public @Nullable UUID getCasterUuid() {
        String raw = this.entityData.get(DATA_CASTER_UUID);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private @Nullable ServerPlayer getCaster(ServerLevel level) {
        UUID casterId = getCasterUuid();
        return casterId == null ? null : level.getServer().getPlayerList().getPlayer(casterId);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return false;
    }

    @Override
    public boolean canBeCollidedWith(Entity entity) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void move(MoverType type, Vec3 movement) {
        if (!isShattering()) {
            return;
        }
        super.move(type, movement);
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0;
    }

    @Override
    public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(@NonNull ValueInput input) {
        // TRANSIENT: not saved
    }

    @Override
    protected void addAdditionalSaveData(@NonNull ValueOutput output) {
        // TRANSIENT: not saved
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<ProtegoShieldEntity>("shield_controller", 2, this::shieldAnimController)
                .triggerableAnim("deflect", RawAnimation.begin().thenPlay("deflect"))
                .triggerableAnim("shatter", RawAnimation.begin().thenPlay("shatter"))
                .triggerableAnim("absorb", RawAnimation.begin().thenPlay("horribilis_absorb")));
    }

    private PlayState shieldAnimController(AnimationTest<ProtegoShieldEntity> test) {
        return test.setAndContinue(getTier() == 0 ? IDLE_DISC : IDLE_DOME);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    private static int maxDeflectionsFor(int tier) {
        return switch (tier) {
            case 0 -> 1;
            case 1 -> 3;
            default -> 5;
        };
    }

    private static int computeMaxLifetime(int tier, float proficiency) {
        return switch (tier) {
            case 0 -> 60 + (int) (proficiency * 120f);
            case 1 -> 100 + (int) (proficiency * 200f);
            default -> 200 + (int) (proficiency * 400f);
        };
    }

    private static double interceptRadius(int tier) {
        return switch (tier) {
            case 0 -> 1.5;
            case 1 -> 2.2;
            case 2, 3 -> 5.8;
            default -> 1.5;
        };
    }
}
