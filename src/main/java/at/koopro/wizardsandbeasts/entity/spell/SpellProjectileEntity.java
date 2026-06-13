package at.koopro.wizardsandbeasts.entity.spell;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.spell.core.SpellIds;
import at.koopro.wizardsandbeasts.spell.expelliarmus.ExpelliarmusDisarmHandler;
import at.koopro.wizardsandbeasts.event.spell.SpellCombatControlHandler;
import at.koopro.wizardsandbeasts.network.spell.SpellImpactBurstS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions;
import at.koopro.wizardsandbeasts.registry.ModParticles;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellFamilies;
import at.koopro.wizardsandbeasts.spell.proficiency.SpellProficiencyTracker;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.lib.SpellHelper;
import at.koopro.wizardsandbeasts.spell.effect.SpellEffectContext;
import at.koopro.wizardsandbeasts.spell.effect.SpellEffectRunner;
import at.koopro.wizardsandbeasts.spell.core.SpellProperties;
import at.koopro.wizardsandbeasts.spell.proficiency.SpellScalingProfile;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public class SpellProjectileEntity extends ThrowableProjectile {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final int BASE_PARTICLE_RATE = 1;

    private static final EntityDataAccessor<String> DATA_SPELL_ID =
            SynchedEntityData.defineId(SpellProjectileEntity.class, EntityDataSerializers.STRING);

    private String spellId = "";
    private Spell cachedSpell;
    private UUID casterUuid;
    private SpellScalingProfile scalingProfile = SpellScalingProfile.DEFAULT;

    public SpellProjectileEntity(EntityType<? extends ThrowableProjectile> type, Level level) {
        super(type, level);
    }

    public SpellProjectileEntity(Level level, LivingEntity shooter, String spellId) {
        super(ModEntities.SPELL_PROJECTILE.get(), level);
        this.spellId = spellId;
        this.entityData.set(DATA_SPELL_ID, spellId);
        this.cachedSpell = Spells.byId(spellId);
        this.casterUuid = shooter.getUUID();
        setPos(shooter.getX(), shooter.getEyeY() - 0.1, shooter.getZ());
        setOwner(shooter);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SPELL_ID, "");
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (level().isClientSide()) return;

        Entity hit = result.getEntity();
        if (cachedSpell == null) { discard(); return; }
        if (casterUuid != null && hit.getUUID().equals(casterUuid)) {
            discard();
            return;
        }

        SpellProperties props = cachedSpell.getProperties();

        // Calculate proficiency-adjusted damage
        float damage = cachedSpell.getBaseDamage();
        Entity owner = getOwner();
        if (owner instanceof ServerPlayer player) {
            damage = cachedSpell.getDamageForCaster(player);
        }
        if (damage > 0 && hit instanceof LivingEntity living) {
            living.hurt(level().damageSources().magic(), damage * scalingProfile.damageMult());
        }

        if (props != null && hit instanceof LivingEntity living) {
            cachedSpell.applyTargetEffects(living, scalingProfile.durationMult());

            // Data-driven effect components (Step 3): run the spell's authored effect list against the
            // hit target. No-op for spells without an effects list (all Java spells today).
            if (owner instanceof ServerPlayer casterPlayer && level() instanceof ServerLevel effectLevel) {
                SpellEffectRunner.run(cachedSpell, SpellEffectContext.ofTarget(casterPlayer, living, effectLevel)
                        .withScaling(scalingProfile.damageMult(), scalingProfile.durationMult(),
                                scalingProfile.controlMult()));
            }

            // Disarm (Expelliarmus)
            if (props.disarms()) {
                ServerLevel projectileLevel = (ServerLevel) level();
                if (SpellIds.matches(cachedSpell.getId(), "expelliarmus")
                        && owner instanceof ServerPlayer attacker) {
                    ExpelliarmusDisarmHandler.apply(projectileLevel, living, attacker, cachedSpell.getProficiencyScalar(attacker));
                } else {
                    SpellCombatControlHandler.applyExpelliarmusDisarm(
                            projectileLevel, living, owner instanceof ServerPlayer player ? player : null);
                }
                BlockPos pk = BlockPos.containing(living.position());
                projectileLevel.playSound(null, pk, ModSounds.SPELL_IMPACT_EXPELLIARMUS.get(),
                        SoundSource.PLAYERS, 0.48f, 1.0f + projectileLevel.random.nextFloat() * 0.14f);
            }

            if (props.stuns()) {
                ServerLevel projectileLevel = (ServerLevel) level();
                SpellCombatControlHandler.applyStupefyStun(living);
                projectileLevel.playSound(null, BlockPos.containing(living.position()), ModSounds.SPELL_IMPACT_STUPEFY.get(),
                        SoundSource.PLAYERS, 0.42f, 1.03f + projectileLevel.random.nextFloat() * 0.08f);
            }

            if (props.ignites()) {
                SpellHelper.ignite(living, props.getIgniteDurationSeconds());
            }
        }

        if (level() instanceof ServerLevel && cachedSpell != null) {
            SpellImpactBurstS2CPayload.sendToTracking(this, hit.getBoundingBox().getCenter(),
                    SpellFamilies.of(cachedSpell), cachedSpell.getColor(), 18, 0.35f);
        }

        if (owner instanceof ServerPlayer player) {
            SpellProficiencyTracker.recordSuccessfulHit(player, cachedSpell.getId());
        }

        if (props != null && props.getKnockbackStrength() != 0) {
            float baseKnockback = cachedSpell.getBaseKnockback() != 0.0f ? cachedSpell.getBaseKnockback() : props.getKnockbackStrength();
            SpellHelper.applyKnockback(hit, getDeltaMovement(), baseKnockback * scalingProfile.controlMult());
        }

        // Direct hit already applied spell damage; skip center explosion on living targets to avoid double-dip.
        if (props != null && props.explodes() && !(hit instanceof LivingEntity)) {
            SpellHelper.createExplosion(level(), null, hit.position(),
                    props.getExplosionPower(), props.explosionBreaksBlocks());
        }

        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!level().isClientSide()) {
            boolean successfulHit = false;
            if (cachedSpell != null && cachedSpell.getProperties() != null && cachedSpell.getProperties().explodes()) {
                SpellProperties props = cachedSpell.getProperties();
                if (level() instanceof ServerLevel serverLevel && isConfringo(cachedSpell.getId())) {
                    SpellHelper.applyConfringoBlockImpact(serverLevel, result, cachedSpell,
                            props.getExplosionPower(), props.explosionBreaksBlocks());
                } else {
                    SpellHelper.createExplosion(level(), null,
                            net.minecraft.world.phys.Vec3.atCenterOf(result.getBlockPos()),
                            props.getExplosionPower(), props.explosionBreaksBlocks());
                }
                successfulHit = true;
            } else if (level() instanceof ServerLevel serverLevel) {
                if (cachedSpell != null && cachedSpell.getProperties() != null
                        && cachedSpell.getProperties().ignites()) {
                    SpellHelper.tryIgniteAdjacentToBlockHit(serverLevel, result);
                    successfulHit = true;
                }
                if (cachedSpell != null && SpellIds.matches(cachedSpell.getId(), "stupefy")) {
                    SpellHelper.applyStupefyImpactPulse(serverLevel, getOwner(), result.getLocation(), cachedSpell);
                    successfulHit = true;
                }
                if (cachedSpell != null && isDiffindo(cachedSpell.getId())) {
                    if (SpellHelper.tryDiffindoCutBlock(serverLevel, result.getBlockPos(), cachedSpell)) {
                        successfulHit = true;
                    }
                }
                if (cachedSpell != null && cachedSpell.getProperties() != null
                        && isPushSpell(cachedSpell.getId())) {
                    float force = Math.max(1.6f, cachedSpell.getProperties().getKnockbackStrength() * 0.8f);
                    int pushed = SpellHelper.pushNearbyLightweightEntities(
                            serverLevel, getOwner(), result.getLocation(), getDeltaMovement(), force, 2.6);
                    if (pushed > 0) {
                        SpellHelper.playSpellImpact(serverLevel, result.getLocation(), cachedSpell.getColor());
                        successfulHit = true;
                    }
                }
                if (cachedSpell != null) {
                    SpellImpactBurstS2CPayload.sendToTracking(this, result.getLocation(),
                            SpellFamilies.of(cachedSpell), cachedSpell.getColor(), 14, 0.28f);
                }
            }
            Entity owner = getOwner();
            if (successfulHit && cachedSpell != null && owner instanceof ServerPlayer player) {
                SpellProficiencyTracker.recordSuccessfulHit(player, cachedSpell.getId());
            }
            discard();
        }
    }

    private static boolean isDiffindo(String spellId) {
        return "diffindo".equals(spellId) || "wizards_and_beasts:diffindo".equals(spellId);
    }

    private static boolean isPushSpell(String spellId) {
        return "depulso".equals(spellId)
                || "flipendo".equals(spellId)
                || "expelliarmus".equals(spellId)
                || "wizards_and_beasts:depulso".equals(spellId)
                || "wizards_and_beasts:flipendo".equals(spellId)
                || "wizards_and_beasts:expelliarmus".equals(spellId);
    }

    private static boolean isConfringo(String spellId) {
        return "confringo".equals(spellId) || "wizards_and_beasts:confringo".equals(spellId);
    }

    private void trySpellClash(ServerLevel level) {
        if (cachedSpell == null || !SpellIds.matches(cachedSpell.getId(), "expelliarmus")) {
            return;
        }
        List<SpellProjectileEntity> nearby = level.getEntitiesOfClass(SpellProjectileEntity.class,
                getBoundingBox().inflate(0.35), o -> o != this && o.isAlive());
        for (SpellProjectileEntity other : nearby) {
            if (other.cachedSpell == null) {
                continue;
            }
            if (SpellIds.matches(other.cachedSpell.getId(), "avada_kedavra")) {
                continue;
            }
            net.minecraft.world.phys.Vec3 mid = position().add(other.position()).scale(0.5);
            for (int i = 0; i < 12; i++) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                        mid.x, mid.y, mid.z, 1, 0.15, 0.15, 0.15, 0.02);
            }
            var clashTint = new SpellTintParticleOptions(ModParticles.SPELL_CLASH.get(), 0xFFEEDD);
            for (int i = 0; i < 8; i++) {
                level.sendParticles(clashTint, mid.x, mid.y, mid.z, 1, 0.12, 0.12, 0.12, 0.0);
            }
            level.playSound(null, BlockPos.containing(mid), ModSounds.SPELL_CLASH.get(), SoundSource.PLAYERS, 0.55f, 1.1f);
            other.discard();
            discard();
            return;
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (!level().isClientSide() && level() instanceof ServerLevel sl) {
            trySpellClash(sl);
        }

        if (level().isClientSide()) {
            int interval = projectileTrailInterval();
            if (interval > 0 && tickCount % interval == 0) {
                Spell sp = cachedSpell;
                if (sp == null && spellId != null && !spellId.isEmpty()) {
                    sp = Spells.byId(spellId);
                    cachedSpell = sp;
                }
                if (sp != null) {
                    int particleRate = Math.max(1, Math.round(BASE_PARTICLE_RATE * scalingProfile.damageMult()));
                    for (int i = 0; i < particleRate; i++) {
                        level().addParticle(ModParticles.tinted(SpellFamilies.of(sp), sp.getColor()),
                                getX(), getY(), getZ(), 0.0, 0.0, 0.0);
                    }
                }
            }
        }

        if (tickCount > 100) {
            discard();
        }
    }

    private int projectileTrailInterval() {
        return switch (Config.perfProfile) {
            case LOW -> 3;
            case MEDIUM -> 2;
            case HIGH -> 1;
        };
    }

    public void setScalingProfile(SpellScalingProfile scalingProfile) {
        this.scalingProfile = scalingProfile == null ? SpellScalingProfile.DEFAULT : scalingProfile;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        spellId = input.read("SpellId", com.mojang.serialization.Codec.STRING).orElse("");
        this.entityData.set(DATA_SPELL_ID, spellId);
        cachedSpell = Spells.byId(spellId);
        String uuid = input.read("CasterUUID", com.mojang.serialization.Codec.STRING).orElse("");
        if (!uuid.isEmpty()) {
            try {
                casterUuid = UUID.fromString(uuid);
            } catch (IllegalArgumentException e) {
                LOGGER.warn("[WizardsAndBeasts] Discarding malformed CasterUUID '{}' in spell projectile save data", uuid);
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.store("SpellId", com.mojang.serialization.Codec.STRING, spellId);
        if (casterUuid != null) {
            output.store("CasterUUID", com.mojang.serialization.Codec.STRING, casterUuid.toString());
        }
    }

    public String getSpellId() {
        return this.entityData.get(DATA_SPELL_ID);
    }

    @Nullable
    public Spell getCachedOrResolveSpell() {
        if (cachedSpell == null && spellId != null && !spellId.isEmpty()) {
            cachedSpell = Spells.byId(spellId);
        }
        return cachedSpell;
    }

    @Nullable
    public UUID getCasterUuid() {
        return casterUuid;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.01;
    }
}
