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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import org.jspecify.annotations.Nullable;
import java.util.UUID;

/** Static spell-specific handlers for held wand-beam channels. */
final class WandBeamSpellHandlers {

    static final int LEVIOSA_EFFECT_INTERVAL_TICKS = 1;
    static final int LEVIOSA_TARGET_GRACE_MISS_TICKS = 7;
    static final int LEVIOSA_PARTICLE_COLOR = 0xFFB266FF;
    static final float LEVIOSA_MIN_DISTANCE = 2.0f;
    static final float LEVIOSA_MAX_DISTANCE = 24.0f;
    static final int AVADA_MIN_CHARGE_TICKS = 24;
    private static final double LEVIOSA_SPRING_STRENGTH = 0.45;
    private static final double LEVIOSA_MAX_SPEED = 0.9;
    private static final double LEVIOSA_MAX_SPEED_CHARGED = 1.15;
    private static final double LEVIOSA_SPRING_STRENGTH_CHARGED = 0.62;
    private static final double LEVIOSA_SLAM_MIN_SPEED = 0.55;
    private static final float LEVIOSA_SLAM_MAX_DAMAGE = 8.0f;
    private static final int LEVIOSA_SLAM_COOLDOWN_TICKS = 20;

    private WandBeamSpellHandlers() {}

    static void handleAguamentiChannel(ServerLevel level, ServerPlayer player, Spell spell,
                                       WandBeamSession s, float maxReach) {
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        // Pressure jet: shove living things caught in the stream, douse anything burning, and hurt
        // fire-immune creatures (blazes, magma cubes) the way a blast of water should.
        if (s.beamTicks % 4 == 0) {
            Vec3 jetEnd = start.add(look.scale(Math.min(maxReach, 8.0)));
            AABB jetBox = new AABB(start, jetEnd).inflate(1.2);
            for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, jetBox,
                    e -> e != player && e.isAlive())) {
                Vec3 to = le.getBoundingBox().getCenter().subtract(start);
                if (to.lengthSqr() < 1.0e-4 || to.normalize().dot(look) < 0.3) {
                    continue;
                }
                at.koopro.wizardsandbeasts.spell.lib.SpellHelper.applyKnockback(le, look, 0.55f);
                le.hurtMarked = true;
                le.clearFire();
                if (le.fireImmune()) {
                    le.hurt(level.damageSources().magic(), 2.0f);
                }
            }
        }
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
        UUID casterId = player.getUUID();
        releaseCrucioTarget(player, s, casterId);
        releaseLeviosaTarget(player, s, casterId, true);
    }

    /** Strips this caster's Crucio effects from its held target and drops the claim, if any. */
    private static void releaseCrucioTarget(ServerPlayer caster, WandBeamSession s, UUID casterId) {
        if (s.lastCrucioTarget == null) {
            return;
        }
        LivingEntity prev = findLivingInLevel(caster, s.lastCrucioTarget);
        if (prev != null) {
            stripCrucioEffects(prev);
        }
        BeamTargetClaims.release(s.lastCrucioTarget, casterId);
        s.lastCrucioTarget = null;
    }

    /** Restores this caster's Leviosa target's gravity and drops the claim, if any. */
    private static void releaseLeviosaTarget(ServerPlayer caster, WandBeamSession s, UUID casterId, boolean fling) {
        if (s.lastLeviosaTarget == null) {
            return;
        }
        Entity prev = findEntityInLevel(caster, s.lastLeviosaTarget);
        if (prev != null) {
            clearLeviosaEffects(prev, s.lastLeviosaHadNoGravity);
            if (fling) {
                // Wingardium throw: releasing the beam flings the held object/mob where the caster aims.
                Vec3 dir = caster.getLookAngle();
                double force = 1.6;
                prev.setDeltaMovement(dir.x * force, dir.y * force + 0.2, dir.z * force);
                prev.hurtMarked = true;
                prev.fallDistance = 0f;
                SpellImpactBurstS2CPayload.sendToTracking(caster, prev.getBoundingBox().getCenter(),
                        SpellFamily.ARCANE, LEVIOSA_PARTICLE_COLOR, 8, 0.2f);
            }
        }
        BeamTargetClaims.release(s.lastLeviosaTarget, casterId);
        s.lastLeviosaTarget = null;
        s.lastLeviosaHadNoGravity = null;
    }

    static void handleAvada(ServerLevel level, ServerPlayer caster, String spellId,
                            @Nullable LivingEntity target, WandBeamSession s) {
        // Same gate as handleCrucioChannel/ImperioServerLogic — the lethal path must not
        // outlive the DARK_ARTS module switch.
        if (!ModuleManager.isEnabled(Module.DARK_ARTS)) {
            return;
        }
        if (s.avadaConsumed || target == null) return;
        if (s.beamTicks < AVADA_MIN_CHARGE_TICKS) {
            // Longer, louder wind-up so the curse is dodgeable: gathering green light on the target
            // every other tick telegraphs the kill, and breaking line of sight (checked below) cancels it.
            if (s.beamTicks % 2 == 0) {
                SpellImpactBurstS2CPayload.sendToTracking(caster, target.getBoundingBox().getCenter(),
                        SpellFamily.DARK, 0xFF00FF00, 8, 0.14f);
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
        UUID casterId = caster.getUUID();
        if (target == null) {
            releaseCrucioTarget(caster, s, casterId);
            s.crucioHoldTicks = 0;
            return;
        }

        UUID tid = target.getUUID();
        if (s.lastCrucioTarget != null && !s.lastCrucioTarget.equals(tid)) {
            releaseCrucioTarget(caster, s, casterId);
            s.crucioHoldTicks = 0;
        }

        // Another wizard already holds this target under their beam: a second Crucio is contested and
        // applies nothing, so neither caster's cleanup can strip the other's active curse.
        if (!BeamTargetClaims.claim(tid, casterId)) {
            return;
        }

        s.lastCrucioTarget = tid;
        s.crucioHoldTicks++;
        int effectInterval = Math.max(1, (int) (channelEffectInterval / Math.max(0.5f, crucioIntentMultiplier(caster, spell))));
        if (s.beamTicks % effectInterval == 0) {
            float intent = crucioIntentMultiplier(caster, spell);
            PacketDistributor.sendToPlayer(caster, new CrucioIntentFeedbackS2CPayload(intent));
            float corruption = caster.getData(ModAttachments.DARK_CORRUPTION.get());
            caster.setData(ModAttachments.DARK_CORRUPTION.get(), Math.min(100f, corruption
                    + at.koopro.wizardsandbeasts.skill.vocation.VocationAbilityHooks.scaleCorruptionGain(caster, 5.0f * intent)));
            target.removeEffect(MobEffects.WITHER);
            target.removeEffect(MobEffects.SLOWNESS);
            // F2: CRUCIATUS_PAIN now rides crucio.json's tick-cadence apply_effect component
            // (WandBeamChannelLogic.runChannelEffects). This tail keeps the unexpressible bits:
            // intent feedback, corruption accrual, legacy-effect cleanup, ramp damage below.
            recordBeamProficiencyHit(caster, spell.getId(), s, 20);
        }
        // Escalating agony: both the ramp damage and the pain intensity climb the longer the curse
        // holds ONE victim (crucioHoldTicks resets when the beam moves to a new target).
        if (s.crucioHoldTicks >= 30 && s.crucioHoldTicks % 20 == 0) {
            float intent = crucioIntentMultiplier(caster, spell);
            float rampDamage = Math.min(2.5f, 0.4f + (s.crucioHoldTicks / 80f)) * intent;
            target.hurt(caster.level().damageSources().magic(), rampDamage);
            int painAmp = Math.min(3, s.crucioHoldTicks / 40);
            if (painAmp > 0) {
                target.addEffect(new MobEffectInstance(ModEffects.CRUCIATUS_PAIN, 40, painAmp, false, false, true));
            }
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
        UUID casterId = caster.getUUID();
        if (target == null) {
            s.leviosaMissTicks++;
            if (s.leviosaMissTicks <= LEVIOSA_TARGET_GRACE_MISS_TICKS) {
                return;
            }
            releaseLeviosaTarget(caster, s, casterId, false);
            return;
        }
        s.leviosaMissTicks = 0;

        UUID tid = target.getUUID();
        if (s.lastLeviosaTarget != null && !s.lastLeviosaTarget.equals(tid)) {
            releaseLeviosaTarget(caster, s, casterId, false);
        }

        // Another wizard already holds this target: don't fight over its velocity (last-tick-wins tug).
        if (!BeamTargetClaims.claim(tid, casterId)) {
            return;
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
    static Entity findLeviosaTargetAlongCrosshair(ServerPlayer caster, float maxRange, boolean allowBlockLift) {
        if (maxRange <= 0f) return null;
        BeamRay ray = BeamRayResolver.resolve(caster, 1.0f, maxRange, BeamRayResolver.LEVIOSA_FILTER);
        if (ray.hit() instanceof net.minecraft.world.phys.EntityHitResult ehr) {
            Entity hit = ehr.getEntity();
            return isValidLeviosaTarget(caster, hit) ? hit : null;
        }
        // Only convert a block into a liftable entity on a genuine re-acquire (no target already
        // held) -- otherwise a periodic freshness rescan that briefly clips a wall behind an
        // already-held mob would rip that wall block out and hijack the target away from it.
        if (allowBlockLift && ray.hit() instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK
                && caster.level() instanceof ServerLevel level) {
            BlockPos pos = bhr.getBlockPos();
            BlockState state = level.getBlockState(pos);
            if (isLiftableBlock(level, pos, state)) {
                return FallingBlockEntity.fall(level, pos, state);
            }
        }
        return null;
    }

    static boolean isValidLeviosaTarget(ServerPlayer caster, Entity entity) {
        if (entity == null || entity == caster) return false;
        if (entity instanceof ItemEntity || entity instanceof FallingBlockEntity) return true;
        return entity.isPickable()
                && (entity instanceof LivingEntity
                || entity instanceof ArmorStand);
    }

    private static boolean isLiftableBlock(ServerLevel level, BlockPos pos, BlockState state) {
        return !state.isAir()
                && state.getFluidState().isEmpty()
                && state.getDestroySpeed(level, pos) >= 0
                && !state.hasBlockEntity();
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
        if (!(caster.level() instanceof ServerLevel level)) return;

        // Check fallout from the velocity we commanded last tick before we overwrite it below.
        applyLeviosaSlamDamageIfColliding(level, target, s);

        float allowedMax = Mth.clamp(maxReach, LEVIOSA_MIN_DISTANCE, LEVIOSA_MAX_DISTANCE);
        s.leviosaHoldDistance = Mth.clamp(s.leviosaHoldDistance, LEVIOSA_MIN_DISTANCE, allowedMax);
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        Vec3 wantedAnchor = eye.add(look.scale(s.leviosaHoldDistance));

        // Don't command the target into solid geometry: clamp the anchor to just short of any
        // block in the way, otherwise the spring re-pushes into the wall every tick (stutter loop).
        BlockHitResult wallHit = level.clip(new ClipContext(eye, wantedAnchor,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        Vec3 anchor = wallHit.getType() == HitResult.Type.BLOCK
                ? eye.add(look.scale(Math.max(LEVIOSA_MIN_DISTANCE, eye.distanceTo(wallHit.getLocation()) - 0.5)))
                : wantedAnchor;

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

        if (target instanceof Mob mob) {
            // Stop the mob's own pathfinding/moveControl from fighting the spring pull.
            mob.getNavigation().stop();
            mob.setJumping(false);
        }
        if (target instanceof FallingBlockEntity fbe) {
            // Vanilla auto-discards (drops as an item) after 600 airborne ticks regardless of
            // position; a long Leviosa hold must not trip that safety timeout.
            fbe.time = 0;
        }

        target.setDeltaMovement(blendedVelocity);
        target.setNoGravity(true);
        target.setOnGround(false);
        target.hurtMarked = true;
        target.fallDistance = 0f;
        s.leviosaLastCommandedSpeed = (float) blendedVelocity.length();

        SpellImpactBurstS2CPayload.sendToTracking(caster, target.getBoundingBox().getCenter(),
                SpellFamily.ARCANE, LEVIOSA_PARTICLE_COLOR, 6, 0.18f);
    }

    /** Mirrors {@code BroomImpacts.calculateImpactSeverity}: speed ratio against a wall/ground hit becomes damage. */
    private static void applyLeviosaSlamDamageIfColliding(ServerLevel level, Entity target, WandBeamSession s) {
        if (!(target instanceof LivingEntity living)) return;
        if (!(target.horizontalCollision || target.verticalCollision)) return;
        if (s.leviosaLastCommandedSpeed < LEVIOSA_SLAM_MIN_SPEED) return;
        if (s.beamTicks - s.lastLeviosaSlamTick < LEVIOSA_SLAM_COOLDOWN_TICKS) return;

        s.lastLeviosaSlamTick = s.beamTicks;
        float severity = Mth.clamp((float) (s.leviosaLastCommandedSpeed / LEVIOSA_MAX_SPEED_CHARGED), 0f, 1f);
        living.hurtServer(level, level.damageSources().flyIntoWall(), severity * LEVIOSA_SLAM_MAX_DAMAGE);
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
