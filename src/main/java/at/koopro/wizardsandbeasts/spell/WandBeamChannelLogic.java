package at.koopro.wizardsandbeasts.spell;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.network.CrucioIntentFeedbackS2CPacket;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.skill.SkillTreeId;
import at.koopro.wizardsandbeasts.item.WandItem;
import at.koopro.wizardsandbeasts.network.AvadaBlastS2CPacket;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import at.koopro.wizardsandbeasts.network.SpellImpactBurstS2CPacket;
import at.koopro.wizardsandbeasts.network.SpellCastC2SPacket;
import at.koopro.wizardsandbeasts.spell.cast.BeamRay;
import at.koopro.wizardsandbeasts.spell.cast.BeamRayResolver;
import at.koopro.wizardsandbeasts.util.WandHelper;
import at.koopro.wizardsandbeasts.wand.cast.WandStatsResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side held-beam spells while {@link WandItem} is in use.
 * Reach ramps with tick count to stay aligned with the client beam extension
 * ({@link BeamRayResolver#extensionBlocksPerTick()}).
 */
public final class WandBeamChannelLogic {

    private static final int LEVIOSA_EFFECT_INTERVAL_TICKS = 1;
    private static final int LEVIOSA_TARGET_GRACE_MISS_TICKS = 4;
    private static final int LEVIOSA_PARTICLE_COLOR = 0xFFB266FF;
    private static final float LEVIOSA_MIN_DISTANCE = 2.0f;
    private static final float LEVIOSA_MAX_DISTANCE = 24.0f;
    private static final double LEVIOSA_SPRING_STRENGTH = 0.45;
    private static final double LEVIOSA_MAX_SPEED = 0.9;
    private static final double LEVIOSA_MAX_SPEED_CHARGED = 1.15;
    private static final double LEVIOSA_SPRING_STRENGTH_CHARGED = 0.62;
    private static final int AVADA_MIN_CHARGE_TICKS = 12;

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private WandBeamChannelLogic() {}

    public static void tick(ServerPlayer player, ItemStack wandStack) {
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!(wandStack.getItem() instanceof WandItem)) {
            endChannel(player);
            return;
        }
        if (!WandHelper.isWandBondedTo(player, wandStack)) {
            endChannel(player);
            return;
        }

        PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
        String spellId = data.getActiveSpellId();
        if (spellId == null) {
            endChannel(player);
            return;
        }

        Spell spell = Spells.byId(spellId);
        if (spell == null) {
            endChannel(player);
            return;
        }

        SpellProperties props = spell.getProperties();
        if (props == null) {
            endChannel(player);
            return;
        }

        CastType ct = props.getCastType();
        if (ct != CastType.BEAM_LETHAL && ct != CastType.BEAM_CHANNEL) {
            endChannel(player);
            return;
        }
        if (!WandBeamSpellIds.isHeldChannelSpell(spell.getId())) {
            // Prevent non-channel spells (or datapack castType accidents) from applying effects every held tick.
            endChannel(player);
            return;
        }

        if (!data.knowsSpell(spellId)) {
            endChannel(player);
            return;
        }
        long currentTick = level.getGameTime();
        if (data.isOnCooldown(spellId, currentTick)) {
            // Prevent held-channel spells from applying effects while the spell is still recharging.
            endChannel(player);
            return;
        }
        if (Config.enforceSpellRequirements && !spell.getRequirement().isMet(player, data)) {
            endChannel(player);
            return;
        }

        float range = props.getRange() * WandStatsResolver.resolve(wandStack).rangeFor(spell);
        if (range <= 0) range = 32f;

        Session s = SESSIONS.computeIfAbsent(player.getUUID(), u -> new Session());
        if (!Objects.equals(s.spellId, spellId)) {
            clearSessionEffects(player, s);
        }
        s.syncSpell(spellId);
        s.beamTicks++;

        int targetScanInterval = getTargetScanIntervalTicks();
        int channelEffectInterval = getChannelEffectIntervalTicks();

        float maxReach = Math.min(range, s.beamTicks * BeamRayResolver.extensionBlocksPerTick());
        boolean leviosa = WandBeamSpellIds.isLeviosa(spell.getId());
        boolean aguamenti = WandBeamSpellIds.isAguamenti(spell.getId());
        LivingEntity target = null;
        Entity leviosaTarget = null;
        if (leviosa) {
            if (s.cachedTarget != null) {
                leviosaTarget = findEntityInLevel(player, s.cachedTarget);
                if (!isValidLeviosaTarget(player, leviosaTarget)) {
                    leviosaTarget = null;
                }
            }
            if (leviosaTarget == null || s.beamTicks % targetScanInterval == 0) {
                Entity scanned = findLeviosaTargetAlongCrosshair(player, maxReach);
                if (scanned != null) {
                    leviosaTarget = scanned;
                }
            }
            s.cachedTarget = leviosaTarget == null ? null : leviosaTarget.getUUID();
        } else if (!aguamenti) {
            if (s.beamTicks % targetScanInterval == 0 || s.cachedTarget == null) {
                // Use the shared resolver so client visual end and server target always agree.
                BeamRay ray = BeamRayResolver.resolve(player, 1.0f, maxReach, BeamRayResolver.LIVING_FILTER);
                if (ray.hitsEntity()
                        && ray.hit() instanceof net.minecraft.world.phys.EntityHitResult ehr
                        && ehr.getEntity() instanceof LivingEntity living) {
                    target = living;
                }
                s.cachedTarget = target == null ? null : target.getUUID();
            } else {
                target = findLivingInLevel(player, s.cachedTarget);
            }
        }

        if (leviosa) {
            handleLeviosaChannel(player, spell.getId(), leviosaTarget, s, maxReach);
        } else if (aguamenti) {
            handleAguamentiChannel(level, player, spell, s, maxReach);
        } else if (ct == CastType.BEAM_LETHAL) {
            handleAvada(level, player, spell.getId(), target, s);
        } else {
            handleCrucioChannel(player, spell, target, s, channelEffectInterval);
        }
    }

    private static void handleAguamentiChannel(ServerLevel level, ServerPlayer player, Spell spell,
                                              Session s, float maxReach) {
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        BlockHitResult blockHit = level.clip(new ClipContext(
                start, start.add(look.scale(maxReach)),
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.ANY,
                player));
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            if (SpellHelper.aguamentiSoakSoilOrFillCauldron(level, blockHit.getBlockPos(), spell)) {
                recordBeamProficiencyHit(player, spell.getId(), s, 20);
                s.aguamentiWaterAim = null;
                s.aguamentiWaterHold = 0;
                return;
            }
        }
        BlockPos waterAim = SpellHelper.aguamentiResolveSourceWaterAim(level, start, look, maxReach, blockHit);
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
        SpellHelper.aguamentiTryPlaceSourceAfterHold(level, player, spell, waterAim, s.aguamentiWaterHold);
        if (s.aguamentiWaterHold >= 20) {
            recordBeamProficiencyHit(player, spell.getId(), s, 40);
        }
        if (s.beamTicks % 3 == 0) {
            Vec3 c = waterAim.getCenter();
            SpellImpactBurstS2CPacket.sendToTracking(player, c, SpellFamilies.of(spell), spell.getColor(), 5, 0.12f);
        }
    }

    /**
     * Clears Crucio-style effects from the previous target when the wand stops channeling.
     */
    public static void endChannel(ServerPlayer player) {
        Session s = SESSIONS.remove(player.getUUID());
        if (s == null) return;
        clearSessionEffects(player, s);
    }

    private static void clearSessionEffects(ServerPlayer player, Session s) {
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

    private static void handleAvada(ServerLevel level, ServerPlayer caster, String spellId,
                                    @Nullable LivingEntity target, Session s) {
        if (s.avadaConsumed || target == null) return;
        if (s.beamTicks < AVADA_MIN_CHARGE_TICKS) {
            if (s.beamTicks % 3 == 0) {
                SpellImpactBurstS2CPacket.sendToTracking(caster, target.getBoundingBox().getCenter(),
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
            // TODO(redemption): timed love protection window against AK
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
        AvadaBlastS2CPacket.sendToTracking(caster, caster.getEyePosition(), target.getBoundingBox().getCenter());
        SpellCastC2SPacket.completeWandCastRelease(caster);
        SpellCastC2SPacket.ignoreDuplicateReleasesUntil(caster, level.getGameTime() + 15);
        caster.releaseUsingItem();
    }

    private static void handleCrucioChannel(ServerPlayer caster, Spell spell,
                                            @Nullable LivingEntity target, Session s, int channelEffectInterval) {
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
            PacketDistributor.sendToPlayer(caster, new CrucioIntentFeedbackS2CPacket(intent));
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

    private static void handleLeviosaChannel(ServerPlayer caster, String spellId, @Nullable Entity target, Session s, float maxReach) {
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

    private static void recordBeamProficiencyHit(ServerPlayer player, String spellId, Session s, int minIntervalTicks) {
        int interval = Math.max(1, minIntervalTicks);
        if (s.beamTicks - s.lastProficiencyHitTick < interval) {
            return;
        }
        s.lastProficiencyHitTick = s.beamTicks;
        SpellProficiencyTracker.recordSuccessfulHit(player, spellId);
    }

    @Nullable
    private static LivingEntity findLivingInLevel(ServerPlayer player, UUID id) {
        Entity e = findEntityInLevel(player, id);
        return e instanceof LivingEntity le ? le : null;
    }

    @Nullable
    private static Entity findEntityInLevel(ServerPlayer player, UUID id) {
        if (!(player.level() instanceof ServerLevel sl)) return null;
        return sl.getEntity(id);
    }

    @Nullable
    private static Entity findLeviosaTargetAlongCrosshair(ServerPlayer caster, float maxRange) {
        if (maxRange <= 0f) return null;
        BeamRay ray = BeamRayResolver.resolve(caster, 1.0f, maxRange, BeamRayResolver.LEVIOSA_FILTER);
        if (ray.hit() instanceof net.minecraft.world.phys.EntityHitResult ehr) {
            Entity hit = ehr.getEntity();
            return isValidLeviosaTarget(caster, hit) ? hit : null;
        }
        return null;
    }

    private static boolean isValidLeviosaTarget(ServerPlayer caster, Entity entity) {
        if (entity == null || entity == caster) return false;
        if (entity instanceof ItemEntity) return true;
        return entity != null
                && entity.isPickable()
                && (entity instanceof LivingEntity
                || entity instanceof ArmorStand);
    }

    private static int getTargetScanIntervalTicks() {
        return switch (Config.perfProfile) {
            case LOW -> Math.max(2, Config.beamTargetScanIntervalTicks + 2);
            case HIGH -> Math.max(1, Config.beamTargetScanIntervalTicks - 1);
            case MEDIUM -> Math.max(1, Config.beamTargetScanIntervalTicks);
        };
    }

    private static int getChannelEffectIntervalTicks() {
        return switch (Config.perfProfile) {
            case LOW -> Math.max(2, Config.beamChannelEffectIntervalTicks + 2);
            case HIGH -> Math.max(1, Config.beamChannelEffectIntervalTicks - 1);
            case MEDIUM -> Math.max(1, Config.beamChannelEffectIntervalTicks);
        };
    }

    private static void applyLeviosaEffects(ServerPlayer caster, Entity target, Session s, float maxReach) {
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
        if (caster.level() instanceof ServerLevel level) {
            SpellImpactBurstS2CPacket.sendToTracking(caster, target.getBoundingBox().getCenter(),
                    SpellFamily.ARCANE, LEVIOSA_PARTICLE_COLOR, 6, 0.18f);
        }
    }

    private static void clearLeviosaEffects(Entity target, @Nullable Boolean hadNoGravityBeforeChannel) {
        target.setNoGravity(Boolean.TRUE.equals(hadNoGravityBeforeChannel));
    }

    public static void adjustLeviosaDistance(ServerPlayer player, float delta) {
        if (Math.abs(delta) < 1.0e-4f) return;
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) return;
        String spellId = s.spellId;
        if (spellId == null) {
            PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
            spellId = data.getActiveSpellId();
        }
        Spell spell = spellId == null ? null : Spells.byId(spellId);
        if (spell == null || !WandBeamSpellIds.isLeviosa(spell.getId())) {
            return;
        }

        ItemStack wand = player.getUseItem();
        if (!(wand.getItem() instanceof WandItem)) {
            if (player.getMainHandItem().getItem() instanceof WandItem) {
                wand = player.getMainHandItem();
            } else if (player.getOffhandItem().getItem() instanceof WandItem) {
                wand = player.getOffhandItem();
            } else {
                return;
            }
        }
        float range = spell.getProperties().getRange() * WandStatsResolver.resolve(wand).rangeFor(spell);
        if (range <= 0f) range = 32f;
        float maxReach = Math.min(range, s.beamTicks * BeamRayResolver.extensionBlocksPerTick());
        float cap = Math.max(LEVIOSA_MIN_DISTANCE, Math.min(maxReach, LEVIOSA_MAX_DISTANCE));
        s.leviosaHoldDistance = Mth.clamp(s.leviosaHoldDistance + delta, LEVIOSA_MIN_DISTANCE, cap);
    }

    private static void stripCrucioEffects(LivingEntity entity) {
        entity.removeEffect(MobEffects.WITHER);
        entity.removeEffect(MobEffects.SLOWNESS);
        entity.removeEffect(ModEffects.CRUCIATUS_PAIN);
        entity.removeEffect(ModEffects.CRUCIO_SANITY_DRAIN);
    }

    static final class Session {
        @Nullable String spellId;
        int beamTicks;
        boolean avadaConsumed;
        @Nullable UUID lastCrucioTarget;
        @Nullable UUID lastLeviosaTarget;
        @Nullable Boolean lastLeviosaHadNoGravity;
        int leviosaMissTicks;
        float leviosaHoldDistance = 6.0f;
        int lastProficiencyHitTick;
        @Nullable UUID cachedTarget;
        @Nullable BlockPos aguamentiWaterAim;
        int aguamentiWaterHold;

        void syncSpell(String id) {
            if (!Objects.equals(spellId, id)) {
                spellId = id;
                beamTicks = 0;
                avadaConsumed = false;
                lastCrucioTarget = null;
                lastLeviosaTarget = null;
                lastLeviosaHadNoGravity = null;
                leviosaMissTicks = 0;
                leviosaHoldDistance = 6.0f;
                lastProficiencyHitTick = -9999;
                cachedTarget = null;
                aguamentiWaterAim = null;
                aguamentiWaterHold = 0;
            }
        }
    }
}
