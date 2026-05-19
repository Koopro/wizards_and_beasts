package at.koopro.wizardsandbeasts.spell.beam;

import at.koopro.wizardsandbeasts.spell.core.*;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.network.spell.AvadaBlastS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.CrucioIntentFeedbackS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.SpellCastC2SPayload;
import at.koopro.wizardsandbeasts.network.spell.SpellImpactBurstS2CPayload;
import at.koopro.wizardsandbeasts.spell.lib.*;
import at.koopro.wizardsandbeasts.spell.proficiency.*;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.skill.SkillTreeId;
import at.koopro.wizardsandbeasts.spell.cast.BeamRay;
import at.koopro.wizardsandbeasts.spell.cast.BeamRayResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.UUID;

/** Static spell-specific handlers for held wand-beam channels. */
final class WandBeamSpellHandlers {

    static final int LEVIOSA_EFFECT_INTERVAL_TICKS = 1;
    static final int LEVIOSA_TARGET_GRACE_MISS_TICKS = 4;
    static final int LEVIOSA_PARTICLE_COLOR = 0xFFB266FF;
    static final float LEVIOSA_MIN_DISTANCE = 2.0f;
    static final float LEVIOSA_MAX_DISTANCE = 24.0f;
    static final int AVADA_MIN_CHARGE_TICKS = 12;
    private static final double LEVIOSA_SPRING_STRENGTH = 0.45;
    private static final double LEVIOSA_MAX_SPEED = 0.9;
    private static final double LEVIOSA_MAX_SPEED_CHARGED = 1.15;
    private static final double LEVIOSA_SPRING_STRENGTH_CHARGED = 0.62;

    private WandBeamSpellHandlers() {}

    static void handleAguamentiChannel(ServerLevel level, ServerPlayer player, Spell spell,
                                       WandBeamSession s, float maxReach) {
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        BlockHitResult blockHit = level.clip(new ClipContext(
                start, start.add(look.scale(maxReach)),
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.ANY,
                player));
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            if (AguamentiHelper.aguamentiSoakSoilOrFillCauldron(level, blockHit.getBlockPos(), spell)) {
                recordBeamProficiencyHit(player, spell.getId(), s, 20);
                s.aguamentiWaterAim = null;
                s.aguamentiWaterHold = 0;
                return;
            }
        }
        BlockPos waterAim = AguamentiHelper.aguamentiResolveSourceWaterAim(level, start, look, maxReach, blockHit);
        if (waterAim == null) {
            s.aguamentiWaterAim = null;
            s.aguamentiWaterHold = 0;
            return;
        }
        if (s.aguamentiWaterAim == null || !s.aguamentiWaterAim.equals(waterAim)) {
            s.aguamentiWaterAim = waterAim;
            s.aguamentiWaterHold = 1;
        } else {
            s.aguamentiWaterHold++;
        }
        AguamentiHelper.aguamentiTryPlaceSourceAfterHold(level, player, spell, waterAim, s.aguamentiWaterHold);
        if (s.aguamentiWaterHold >= 20) {
            recordBeamProficiencyHit(player, spell.getId(), s, 40);
        }
        if (s.beamTicks % 3 == 0) {
            Vec3 c = waterAim.getCenter();
            SpellImpactBurstS2CPayload.sendToTracking(player, c, SpellFamilies.of(spell), spell.getColor(), 5, 0.12f);
        }
    }

    static void clearSessionEffects(ServerPlayer player, WandBeamSession s) {
        if (s.lastCrucioTarget != null) {
            LivingEntity prev = findLivingInLevel(player, s.lastCrucioTarget);
            if (prev != null) {
                stripCrucioEffects(prev);
            }
        }
        if (s.lastLeviosaTarget != null) {
            Entity prevLeviosa = findEntityInLevel(player, s.lastLeviosaTarget);
            if (prevLeviosa != null) {
                clearLeviosaEffects(prevLeviosa, s.lastLeviosaHadNoGravity);
            }
        }
    }

    static void handleAvada(ServerLevel level, ServerPlayer caster, String spellId,
                            @Nullable LivingEntity target, WandBeamSession s) {
        if (s.avadaConsumed || target == null) return;
        if (s.beamTicks < AVADA_MIN_CHARGE_TICKS) {
            if (s.beamTicks % 3 == 0) {
                SpellImpactBurstS2CPayload.sendToTracking(caster, target.getBoundingBox().getCenter(),
                        SpellFamily.DARK, 0xFF00FF00, 4, 0.08f);
            }
            return;
        }
        BlockHitResult los = level.clip(new ClipContext(
                caster.getEyePosition(),
                target.getBoundingBox().getCenter(),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                caster));
        if (los.getType() != HitResult.Type.MISS) {
            return;
        }
        if (target instanceof ServerPlayer victim
                && Boolean.TRUE.equals(victim.getData(ModAttachments.LOVE_PROTECTION.get()))) {
            return;
        }
        target.invulnerableTime = 0;
        target.hurt(level.damageSources().playerAttack(caster), 1_000_000f);
        if (target.isAlive()) {
            return;
        }
        level.playSound(null, target.blockPosition(), ModSounds.SPELL_IMPACT_AVADA.get(), SoundSource.PLAYERS,
                0.85f, 0.94f + level.random.nextFloat() * 0.08f);
        s.avadaConsumed = true;
        recordBeamProficiencyHit(caster, spellId, s, 1);
        AvadaBlastS2CPayload.sendToTracking(caster, caster.getEyePosition(), target.getBoundingBox().getCenter());
        SpellCastC2SPayload.completeWandCastRelease(caster);
        SpellCastC2SPayload.ignoreDuplicateReleasesUntil(caster, level.getGameTime() + 15);
        caster.releaseUsingItem();
    }

    static void handleCrucioChannel(ServerPlayer caster, Spell spell,
                                    @Nullable LivingEntity target, WandBeamSession s, int channelEffectInterval) {
        if (!ModuleManager.isEnabled(Module.DARK_ARTS)) {
            return;
        }
        if (target == null) {
            if (s.lastCrucioTarget != null) {
                LivingEntity prev = findLivingInLevel(caster, s.lastCrucioTarget);
                if (prev != null) {
                    stripCrucioEffects(prev);
                }
                s.lastCrucioTarget = null;
            }
            return;
        }

        UUID tid = target.getUUID();
        if (s.lastCrucioTarget != null && !s.lastCrucioTarget.equals(tid)) {
            LivingEntity prev = findLivingInLevel(caster, s.lastCrucioTarget);
            if (prev != null) {
                stripCrucioEffects(prev);
            }
        }

        s.lastCrucioTarget = tid;
        int effectInterval = Math.max(1, (int) (channelEffectInterval / Math.max(0.5f, crucioIntentMultiplier(caster, spell))));
        if (s.beamTicks % effectInterval == 0) {
            float intent = crucioIntentMultiplier(caster, spell);
            PacketDistributor.sendToPlayer(caster, new CrucioIntentFeedbackS2CPayload(intent));
            float corruption = caster.getData(ModAttachments.DARK_CORRUPTION.get());
            caster.setData(ModAttachments.DARK_CORRUPTION.get(), Math.min(100f, corruption + 5.0f * intent));
            target.removeEffect(MobEffects.WITHER);
            target.removeEffect(MobEffects.SLOWNESS);
            int painTicks = Math.max(20, (int) (60 / intent));
            target.addEffect(new MobEffectInstance(ModEffects.CRUCIATUS_PAIN, painTicks, 0, false, true, true));
            recordBeamProficiencyHit(caster, spell.getId(), s, 20);
        }
        if (s.beamTicks >= 40 && s.beamTicks % 20 == 0) {
            float intent = crucioIntentMultiplier(caster, spell);
            float rampDamage = Math.min(1.5f, 0.4f + (s.beamTicks / 120f)) * intent;
            target.hurt(caster.level().damageSources().magic(), rampDamage);
            recordBeamProficiencyHit(caster, spell.getId(), s, 20);
        }
    }

    private static float crucioIntentMultiplier(ServerPlayer caster, Spell spell) {
        float baseIntent = 0.3f;
        float corruption = caster.getData(ModAttachments.DARK_CORRUPTION.get()) / 100.0f * 0.5f;
        float prof = spell.getProficiencyScalar(caster) * 0.4f;
        float darkArtsNodes = SkillSystemAPI.countUnlockedSkillsInTree(caster, SkillTreeId.DARK_ARTS) / 6.0f * 0.3f;
        return Mth.clamp(baseIntent + corruption + prof + darkArtsNodes, 0.1f, 1.5f);
    }

    static void handleLeviosaChannel(ServerPlayer caster, String spellId, @Nullable Entity target,
                                     WandBeamSession s, float maxReach) {
        if (target == null) {
            s.leviosaMissTicks++;
            if (s.leviosaMissTicks <= LEVIOSA_TARGET_GRACE_MISS_TICKS) {
                return;
            }
            if (s.lastLeviosaTarget != null) {
                Entity previous = findEntityInLevel(caster, s.lastLeviosaTarget);
                if (previous != null) {
                    clearLeviosaEffects(previous, s.lastLeviosaHadNoGravity);
                }
                s.lastLeviosaTarget = null;
                s.lastLeviosaHadNoGravity = null;
            }
            return;
        }
        s.leviosaMissTicks = 0;

        UUID tid = target.getUUID();
        if (s.lastLeviosaTarget != null && !s.lastLeviosaTarget.equals(tid)) {
            Entity previous = findEntityInLevel(caster, s.lastLeviosaTarget);
            if (previous != null) {
                clearLeviosaEffects(previous, s.lastLeviosaHadNoGravity);
            }
        }

        if (!tid.equals(s.lastLeviosaTarget)) {
            s.lastLeviosaHadNoGravity = target.isNoGravity();
            float dist = (float) caster.getEyePosition().distanceTo(target.getBoundingBox().getCenter());
            s.leviosaHoldDistance = Mth.clamp(dist, LEVIOSA_MIN_DISTANCE, Math.min(maxReach, LEVIOSA_MAX_DISTANCE));
        }
        s.lastLeviosaTarget = tid;
        if (s.beamTicks % LEVIOSA_EFFECT_INTERVAL_TICKS == 0) {
            applyLeviosaEffects(caster, target, s, maxReach);
            recordBeamProficiencyHit(caster, spellId, s, 20);
        }
    }

    static void recordBeamProficiencyHit(ServerPlayer player, String spellId, WandBeamSession s, int minIntervalTicks) {
        int interval = Math.max(1, minIntervalTicks);
        if (s.beamTicks - s.lastProficiencyHitTick < interval) {
            return;
        }
        s.lastProficiencyHitTick = s.beamTicks;
        SpellProficiencyTracker.recordSuccessfulHit(player, spellId);
    }

    @Nullable
    static LivingEntity findLivingInLevel(ServerPlayer player, UUID id) {
        Entity e = findEntityInLevel(player, id);
        return e instanceof LivingEntity le ? le : null;
    }

    @Nullable
    static Entity findEntityInLevel(ServerPlayer player, UUID id) {
        if (!(player.level() instanceof ServerLevel sl)) return null;
        return sl.getEntity(id);
    }

    @Nullable
    static Entity findLeviosaTargetAlongCrosshair(ServerPlayer caster, float maxRange) {
        if (maxRange <= 0f) return null;
        BeamRay ray = BeamRayResolver.resolve(caster, 1.0f, maxRange, BeamRayResolver.LEVIOSA_FILTER);
        if (ray.hit() instanceof net.minecraft.world.phys.EntityHitResult ehr) {
            Entity hit = ehr.getEntity();
            return isValidLeviosaTarget(caster, hit) ? hit : null;
        }
        return null;
    }

    static boolean isValidLeviosaTarget(ServerPlayer caster, Entity entity) {
        if (entity == null || entity == caster) return false;
        if (entity instanceof ItemEntity) return true;
        return entity.isPickable()
                && (entity instanceof LivingEntity
                || entity instanceof ArmorStand);
    }

    static int getTargetScanIntervalTicks() {
        return switch (Config.perfProfile) {
            case LOW -> Math.max(2, Config.beamTargetScanIntervalTicks + 2);
            case HIGH -> Math.max(1, Config.beamTargetScanIntervalTicks - 1);
            case MEDIUM -> Math.max(1, Config.beamTargetScanIntervalTicks);
        };
    }

    static int getChannelEffectIntervalTicks() {
        return switch (Config.perfProfile) {
            case LOW -> Math.max(2, Config.beamChannelEffectIntervalTicks + 2);
            case HIGH -> Math.max(1, Config.beamChannelEffectIntervalTicks - 1);
            case MEDIUM -> Math.max(1, Config.beamChannelEffectIntervalTicks);
        };
    }

    private static void applyLeviosaEffects(ServerPlayer caster, Entity target, WandBeamSession s, float maxReach) {
        float allowedMax = Mth.clamp(maxReach, LEVIOSA_MIN_DISTANCE, LEVIOSA_MAX_DISTANCE);
        s.leviosaHoldDistance = Mth.clamp(s.leviosaHoldDistance, LEVIOSA_MIN_DISTANCE, allowedMax);
        Vec3 anchor = caster.getEyePosition().add(caster.getLookAngle().scale(s.leviosaHoldDistance));
        Vec3 currentCenter = target.getBoundingBox().getCenter();
        Vec3 deltaToAnchor = anchor.subtract(currentCenter);
        double holdFactor = Mth.clamp(s.beamTicks / 80.0, 0.0, 1.0);
        double spring = Mth.lerp(holdFactor, LEVIOSA_SPRING_STRENGTH, LEVIOSA_SPRING_STRENGTH_CHARGED);
        double speedCap = Mth.lerp(holdFactor, LEVIOSA_MAX_SPEED, LEVIOSA_MAX_SPEED_CHARGED);
        Vec3 desiredVelocity = deltaToAnchor.scale(spring);
        if (desiredVelocity.lengthSqr() > (speedCap * speedCap)) {
            desiredVelocity = desiredVelocity.normalize().scale(speedCap);
        }
        // Blend with current motion to reduce jitter near walls while still feeling responsive.
        Vec3 blendedVelocity = target.getDeltaMovement().scale(0.25).add(desiredVelocity.scale(0.75));
        target.setDeltaMovement(blendedVelocity);
        target.setNoGravity(true);
        target.setOnGround(false);
        target.hurtMarked = true;
        target.fallDistance = 0f;
        if (caster.level() instanceof ServerLevel) {
            SpellImpactBurstS2CPayload.sendToTracking(caster, target.getBoundingBox().getCenter(),
                    SpellFamily.ARCANE, LEVIOSA_PARTICLE_COLOR, 6, 0.18f);
        }
    }

    static void clearLeviosaEffects(Entity target, @Nullable Boolean hadNoGravityBeforeChannel) {
        target.setNoGravity(Boolean.TRUE.equals(hadNoGravityBeforeChannel));
    }

    static void stripCrucioEffects(LivingEntity entity) {
        entity.removeEffect(MobEffects.WITHER);
        entity.removeEffect(MobEffects.SLOWNESS);
        entity.removeEffect(ModEffects.CRUCIATUS_PAIN);
        entity.removeEffect(ModEffects.CRUCIO_SANITY_DRAIN);
    }
}
