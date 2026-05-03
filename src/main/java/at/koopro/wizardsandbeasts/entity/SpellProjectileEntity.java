package at.koopro.wizardsandbeasts.entity;

import at.koopro.wizardsandbeasts.event.SpellCombatControlHandler;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import at.koopro.wizardsandbeasts.spell.Spell;
import at.koopro.wizardsandbeasts.spell.SpellHelper;
import at.koopro.wizardsandbeasts.spell.SpellProficiencyTracker;
import at.koopro.wizardsandbeasts.spell.Spells;
import at.koopro.wizardsandbeasts.spell.SpellProperties;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import java.util.UUID;

public class SpellProjectileEntity extends ThrowableProjectile {

    private static final EntityDataAccessor<String> DATA_SPELL_ID =
            SynchedEntityData.defineId(SpellProjectileEntity.class, EntityDataSerializers.STRING);

    private String spellId = "";
    private Spell cachedSpell;
    private UUID casterUuid;

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
            living.hurt(level().damageSources().magic(), damage);
        }

        if (props != null && hit instanceof LivingEntity living) {
            cachedSpell.applyTargetEffects(living);

            // Disarm (Expelliarmus)
            if (props.disarms()) {
                SpellCombatControlHandler.applyExpelliarmusDisarm(
                        (ServerLevel) level(),
                        living,
                        owner instanceof ServerPlayer player ? player : null);
            }

            if (props.stuns()) {
                SpellCombatControlHandler.applyStupefyStun((ServerLevel) level(), living);
            }

            if (props.ignites()) {
                SpellHelper.ignite(living, props.getIgniteDurationSeconds());
            }
        }

        if (level() instanceof ServerLevel serverLevel) {
            SpellHelper.spawnBurst(serverLevel, hit.getBoundingBox().getCenter(),
                    cachedSpell.getColor(), 18, 0.35);
        }

        if (owner instanceof ServerPlayer player) {
            SpellProficiencyTracker.recordSuccessfulHit(player, cachedSpell.getId());
        }

        if (props != null && props.getKnockbackStrength() != 0) {
            SpellHelper.applyKnockback(hit, getDeltaMovement(), props.getKnockbackStrength());
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
                if (cachedSpell != null && isStupefy(cachedSpell.getId())) {
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
                    SpellHelper.spawnBurst(serverLevel, result.getLocation(),
                            cachedSpell.getColor(), 14, 0.28);
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

    private static boolean isStupefy(String spellId) {
        return "stupefy".equals(spellId) || "wizards_and_beasts:stupefy".equals(spellId);
    }

    private static boolean isConfringo(String spellId) {
        return "confringo".equals(spellId) || "wizards_and_beasts:confringo".equals(spellId);
    }

    @Override
    public void tick() {
        super.tick();

        if (level() instanceof ServerLevel serverLevel && cachedSpell != null) {
            SpellHelper.spawnTrail(serverLevel, position(), getDeltaMovement(),
                    cachedSpell.getColor(), 3);
        }

        if (tickCount > 100) {
            discard();
        }
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
            } catch (IllegalArgumentException ignored) {
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
    public UUID getCasterUuid() {
        return casterUuid;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.01;
    }
}
