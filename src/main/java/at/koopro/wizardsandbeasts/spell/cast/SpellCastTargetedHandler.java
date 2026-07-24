package at.koopro.wizardsandbeasts.spell.cast;

import at.koopro.wizardsandbeasts.spell.core.*;
import at.koopro.wizardsandbeasts.spell.lib.*;
import at.koopro.wizardsandbeasts.spell.proficiency.*;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.effect.FiniteImmuneEffects;
import at.koopro.wizardsandbeasts.spell.effect.SpellEffectContext;
import at.koopro.wizardsandbeasts.spell.effect.SpellEffectRunner;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.network.spell.SpellImpactBurstS2CPayload;
import at.koopro.wizardsandbeasts.spell.imperio.ImperioServerLogic;
import at.koopro.wizardsandbeasts.spell.proficiency.SpellScalingProfile;
import at.koopro.wizardsandbeasts.wand.cast.WandStats;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class SpellCastTargetedHandler {

    private SpellCastTargetedHandler() {
    }

    static boolean handle(ServerLevel level, ServerPlayer caster,
                          Spell spell, SpellProperties props,
                          float damageMultiplier, WandStats wand, SpellScalingProfile scalingProfile) {
        // Step 4: liberacorpus migrated to JSON — its dispel(specific:[levitation]) runs through the
        // generic target path below (early-return removed). finite_incantatem keeps its handler (self-cast
        // cleanse has no entity target, which the generic path can't serve), and levicorpus keeps its
        // handler (its effects were raw/unscaled; an apply_effect component would add duration scaling).
        if (SpellCastSupport.isFiniteIncantatem(spell)) {
            return handleFiniteIncantatem(level, caster, spell, props, wand);
        }
        if (SpellCastSupport.isLevicorpus(spell)) {
            return handleLevicorpus(level, caster, spell, props, wand);
        }
        Vec3 start = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        float effectiveRange = props.getRange() * wand.rangeFor(spell);
        Vec3 end = start.add(look.scale(effectiveRange));

        LivingEntity target = SpellTargetHelper.findTargetedEntity(level, caster, effectiveRange);

        boolean successful = false;
        if (target != null) {
            float damage = spell.getBaseDamage() * damageMultiplier;

            if (props.getUndeadBonusDamage() > 0 && target.isInvertedHealAndHarm()) {
                damage += props.getUndeadBonusDamage();
            }

            if (damage > 0) {
                target.hurt(level.damageSources().magic(), damage);
                successful = true;
            }

            spell.applyTargetEffects(target, scalingProfile.durationMult());
            successful = true;

            // Data-driven effect components (Step 3): run the authored effect list against the target.
            // No-op for spells without an effects list (all Java spells today).
            SpellEffectRunner.run(spell, SpellEffectContext.ofTarget(caster, target, level)
                    .withScaling(damageMultiplier, scalingProfile.durationMult(), scalingProfile.controlMult()));

            if (props.levitatesTarget()) {
                target.addEffect(new MobEffectInstance(MobEffects.LEVITATION,
                        Math.max(1, Math.round(props.getLevitateDurationTicks() * scalingProfile.durationMult())),
                        0, false, true, true));
                successful = true;
            }

            if (props.getPullStrength() != 0) {
                Vec3 diff = caster.position().subtract(target.position()).normalize()
                        .scale(props.getPullStrength());
                target.push(diff.x, 0.3, diff.z);
                target.hurtMarked = true;
                successful = true;
            }

            if (props.ignites()) {
                SpellHelper.ignite(target, props.getIgniteDurationSeconds());
                successful = true;
            }

            if (props.getKnockbackStrength() != 0) {
                float baseKnockback = spell.getBaseKnockback() != 0.0f ? spell.getBaseKnockback() : props.getKnockbackStrength();
                SpellHelper.applyKnockback(target, look, baseKnockback * scalingProfile.controlMult());
                successful = true;
            }

            if (props.controlsMob() && SpellCastSupport.isImperio(spell) && ModuleManager.isEnabled(Module.DARK_ARTS)) {
                int dur = Math.min(600, 200 + (int) (spell.getProficiencyScalar(caster) * 400));
                ImperioServerLogic.beginControl(level, caster, target, dur);
                successful = true;
            } else if (props.controlsMob() && target instanceof Mob mob) {
                mob.setTarget(null);
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,
                        Math.max(1, Math.round(props.getControlDurationTicks() * scalingProfile.durationMult())), 4, false, true, true));
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,
                        Math.max(1, Math.round(props.getControlDurationTicks() * scalingProfile.durationMult())), 2, false, true, true));
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING,
                        Math.max(1, Math.round(props.getControlDurationTicks() * scalingProfile.durationMult())), 0, false, true, true));
                successful = true;
            }

            if (props.explodes()) {
                SpellHelper.createExplosion(level, caster,
                        target.getBoundingBox().getCenter(), props.getExplosionPower(),
                        props.explosionBreaksBlocks());
                successful = true;
            }

            SpellHelper.spawnBeam(level, spell, start, target.getBoundingBox().getCenter());
        }

        if (props.opensBlocks() || (props.explodes() && target == null) || (target == null && SpellCastSupport.isImperio(spell))) {
            ClipContext.Fluid fluidMode = SpellCastSupport.isImperio(spell) ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE;
            BlockHitResult blockHit = level.clip(new ClipContext(start, end,
                    ClipContext.Block.OUTLINE, fluidMode, caster));
            if (blockHit.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = blockHit.getBlockPos();

                if (props.opensBlocks()) {
                    if (SpellCastSupport.isColloportus(spell)) {
                        successful |= handleColloportus(level, pos, caster, spell);
                    } else {
                        successful |= handleAlohomora(level, pos, caster, spell);
                    }
                }

                if (props.explodes()) {
                    // Bombarda charges: a longer wand-hold grows the blast, up to Bombarda Maxima at full charge.
                    float chargeMult = 1.0f;
                    boolean maxima = false;
                    if (SpellCastSupport.isBombarda(spell)) {
                        int held = WandCastTiming.consumeLastHoldTicks(caster);
                        chargeMult = 1.0f + Math.min(1.0f, held / 20.0f);
                        maxima = chargeMult >= 1.8f;
                    }
                    SpellHelper.createExplosion(level, caster,
                            Vec3.atCenterOf(pos), props.getExplosionPower() * chargeMult,
                            props.explosionBreaksBlocks());
                    successful = true;
                    if (SpellCastSupport.isBombarda(spell)) {
                        if (maxima) {
                            caster.displayClientMessage(
                                    Component.literal("Bombarda Maxima").withStyle(ChatFormatting.RED), true);
                        }
                        SpellHelper.playSpellImpact(level, blockHit.getLocation(), spell.getColor());
                        SpellHelper.pushNearbyLightweightEntities(level, caster, blockHit.getLocation(),
                                look, 1.3f * chargeMult, 3.0 * chargeMult);
                    }
                }
                if (SpellCastSupport.isImperio(spell) && ModuleManager.isEnabled(Module.DARK_ARTS)) {
                    Mob controlled = level.getEntitiesOfClass(Mob.class,
                                    new AABB(pos).inflate(2.5),
                                    Mob::isAlive)
                            .stream()
                            .min(Comparator.comparingDouble(mob -> mob.distanceToSqr(Vec3.atCenterOf(pos))))
                            .orElse(null);
                    if (controlled != null) {
                        int duration = Math.min(600, Math.max(40, 200 + (int) (spell.getProficiencyScalar(caster) * 400)));
                        ImperioServerLogic.beginControl(level, caster, controlled, duration);
                        SpellHelper.playSpellImpact(level, blockHit.getLocation(), spell.getColor());
                        successful = true;
                    }
                }

                SpellHelper.spawnBeam(level, spell, start, blockHit.getLocation());
            }
        }
        return successful;
    }

    private static boolean handleFiniteIncantatem(ServerLevel level, ServerPlayer caster,
                                                  Spell spell, SpellProperties props,
                                                  WandStats wand) {
        if (!ModuleManager.isEnabled(Module.WANDS_AND_SPELLS)) {
            return false;
        }
        float effectiveRange = props.getRange() * wand.rangeFor(spell);

        // A charged cast (long wand hold) by a competent caster is Finite Incantatem Totalum:
        // it lifts mod effects from every living thing nearby instead of a single target.
        int heldTicks = WandCastTiming.consumeLastHoldTicks(caster);
        boolean totalum = heldTicks > 15 && spell.getProficiencyScalar(caster) >= 0.3f;

        if (totalum) {
            int affected = 0;
            for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class,
                    caster.getBoundingBox().inflate(8.0), LivingEntity::isAlive)) {
                if (nearby != caster) {
                    interruptCasting(nearby);
                }
                if (clearModEffects(nearby, spell) > 0) {
                    affected++;
                }
            }
            caster.displayClientMessage(
                    Component.literal("Finite Incantatem Totalum").withStyle(ChatFormatting.AQUA), true);
            level.playSound(null, caster.blockPosition(), SoundEvents.FIRE_EXTINGUISH,
                    SoundSource.PLAYERS, 0.5f, 1.2f);
            SpellImpactBurstS2CPayload.sendToTracking(caster, caster.getEyePosition(1.0f),
                    SpellFamily.LIGHT, spell.getColor(), 18, 0.2f);
            return true;
        }

        LivingEntity lookedAt = SpellTargetHelper.findTargetedEntity(level, caster, effectiveRange);
        if (lookedAt != null) {
            interruptCasting(lookedAt);
        }
        LivingEntity target = lookedAt != null ? lookedAt : caster;
        if (clearModEffects(target, spell) > 0) {
            return true;
        }

        level.playSound(null, caster.blockPosition(), SoundEvents.FIRE_EXTINGUISH,
                SoundSource.PLAYERS, 0.35f, 1.55f);
        SpellImpactBurstS2CPayload.sendToTracking(
                caster,
                caster.getEyePosition(1.0f),
                SpellFamily.LIGHT,
                spell.getColor(),
                6,
                0.08f);
        return true;
    }

    /** Cancels a target's wand-beam channel (players) and langlocks them briefly so they can't re-cast. */
    private static void interruptCasting(LivingEntity target) {
        if (target instanceof ServerPlayer p) {
            at.koopro.wizardsandbeasts.spell.beam.WandBeamChannelLogic.endChannel(p);
        }
        target.addEffect(new MobEffectInstance(
                at.koopro.wizardsandbeasts.effect.ModEffects.LANGLOCK, 30, 0, false, true, true));
    }

    /** Removes all mod-namespaced, non-immune effects from one target; bursts on success; returns count. */
    private static int clearModEffects(LivingEntity target, Spell spell) {
        List<Holder<MobEffect>> toRemove = new ArrayList<>();
        for (MobEffectInstance inst : target.getActiveEffects()) {
            Holder<MobEffect> holder = inst.getEffect();
            MobEffect effect = holder.value();
            if (FiniteImmuneEffects.isImmune(effect)) {
                continue;
            }
            Identifier effectId = BuiltInRegistries.MOB_EFFECT.getKey(effect);
            if (effectId == null || !WizardsAndBeastsMod.MODID.equals(effectId.getNamespace())) {
                continue;
            }
            toRemove.add(holder);
        }

        int removed = 0;
        for (Holder<MobEffect> holder : toRemove) {
            if (target.removeEffect(holder)) {
                removed++;
            }
        }

        if (removed > 0) {
            SpellImpactBurstS2CPayload.sendToTracking(
                    target,
                    target.getBoundingBox().getCenter(),
                    SpellFamilies.of(spell),
                    spell.getColor(),
                    14,
                    0.14f);
        }
        return removed;
    }

    private static boolean handleLevicorpus(ServerLevel level, ServerPlayer caster,
                                            Spell spell, SpellProperties props,
                                            WandStats wand) {
        Vec3 start = caster.getEyePosition();
        float effectiveRange = props.getRange() * wand.rangeFor(spell);
        LivingEntity target = SpellTargetHelper.findTargetedEntity(level, caster, effectiveRange);
        if (target == null) {
            return false;
        }
        target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 90, 1, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2, false, true, true));
        target.fallDistance = 0.0f;
        // Slam on release: tag the target so it crashes down for fall damage when the lift ends.
        target.addTag(at.koopro.wizardsandbeasts.event.spell.LevicorpusSlamHandler.SLAM_TAG);
        if (target instanceof Mob mob) {
            mob.setTarget(null);
        }
        spell.applyTargetEffects(target);
        SpellHelper.spawnBeam(level, spell, start, target.getBoundingBox().getCenter());
        return true;
    }

    private static boolean handleColloportus(ServerLevel level, BlockPos pos, ServerPlayer caster, Spell spell) {
        BlockState state = level.getBlockState(pos);
        int tier = SpellCastSupport.lockTier(state);
        if (tier < 1) {
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(),
                    SoundSource.PLAYERS, 0.4f, 0.55f);
            caster.displayClientMessage(
                    Component.literal("Colloportus finds nothing here to seal.").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        if (ColloportusLockStore.isLocked(level, pos)) {
            caster.displayClientMessage(
                    Component.literal("Already magically sealed.").withStyle(ChatFormatting.DARK_AQUA), true);
            return false;
        }
        ColloportusLockStore.lock(level, pos);
        level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.PLAYERS, 0.55f, 0.85f);
        SpellHelper.playSpellImpact(level, Vec3.atCenterOf(pos), spell.getColor());
        return true;
    }

    static boolean handleAlohomora(ServerLevel level, BlockPos pos, ServerPlayer caster, Spell spell) {
        BlockState state = level.getBlockState(pos);
        if (ColloportusLockStore.isLocked(level, pos)) {
            level.playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.55f, 1.0f);
            caster.displayClientMessage(
                    Component.literal("Colloportus holds — Alohomora cannot open this.").withStyle(ChatFormatting.DARK_RED),
                    true);
            return false;
        }
        if (state.hasProperty(BlockStateProperties.OPEN) && SpellCastSupport.lockTier(state) == 0) {
            level.setBlockAndUpdate(pos, state.cycle(BlockStateProperties.OPEN));
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN,
                    SoundSource.PLAYERS, 0.45f, 1.1f);
            return true;
        }
        Proficiency proficiency = spell.getProficiency(caster);
        int tier = SpellCastSupport.lockTier(state);
        if (tier == 0) {
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(),
                    SoundSource.PLAYERS, 0.4f, 0.6f);
            caster.displayClientMessage(
                    Component.literal("Alohomora fizzles: nothing to unlock here.").withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!SpellCastSupport.canUnlockTier(proficiency, tier)) {
            String detail = switch (tier) {
                case 2 -> "Iron locks need Mastered Alohomora.";
                default -> "This lock is too complex for your current skill.";
            };
            level.playSound(null, pos, SoundEvents.VILLAGER_NO,
                    SoundSource.PLAYERS, 0.7f, 1.0f);
            caster.displayClientMessage(
                    Component.literal("Alohomora fails. " + detail).withStyle(ChatFormatting.RED), true);
            return false;
        }

        if (state.getBlock() instanceof DoorBlock door) {
            boolean open = state.getValue(DoorBlock.OPEN);
            door.setOpen(null, level, state, pos, !open);
        } else if (state.getBlock() instanceof TrapDoorBlock || state.getBlock() instanceof FenceGateBlock) {
            if (state.hasProperty(BlockStateProperties.OPEN)) {
                level.setBlockAndUpdate(pos, state.cycle(BlockStateProperties.OPEN));
            }
        }
        return true;
    }
}
