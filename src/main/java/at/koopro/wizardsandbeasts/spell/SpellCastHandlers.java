package at.koopro.wizardsandbeasts.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.effect.FiniteImmuneEffects;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.network.SpellImpactBurstS2CPacket;
import at.koopro.wizardsandbeasts.util.WandHelper;
import at.koopro.wizardsandbeasts.wand.cast.WandStats;
import at.koopro.wizardsandbeasts.spell.proficiency.SpellScalingProfile;
import at.koopro.wizardsandbeasts.spell.imperio.ImperioServerLogic;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
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

final class SpellCastHandlers {

    private SpellCastHandlers() {}

    static boolean handleCone(ServerLevel level, ServerPlayer caster,
                              Spell spell, SpellProperties props,
                              float damageMultiplier, WandStats wand, SpellScalingProfile scalingProfile) {
        if (SpellCastSupport.isAccio(spell)) {
            return handleAccioCone(level, caster, spell, props, wand);
        }
        Vec3 look = caster.getLookAngle();
        boolean pullsItems = props.getPullStrength() != 0;
        float effectiveRange = props.getRange() * wand.rangeFor(spell);
        List<Entity> entities = SpellHelper.findEntitiesInCone(level, caster, effectiveRange, pullsItems);

        Vec3 casterEye = caster.getEyePosition();
        boolean successful = false;
        boolean expectoPatronum = SpellCastSupport.isExpectoPatronum(spell);
        for (Entity entity : entities) {
            if (props.getPullStrength() != 0) {
                Vec3 diff = caster.position().subtract(entity.position()).normalize()
                        .scale(props.getPullStrength());
                entity.push(diff.x, 0.2, diff.z);
                entity.hurtMarked = true;
                successful = true;
            }

            if (entity instanceof LivingEntity living) {
                // Patronus should only affect dark-aligned targets.
                if (expectoPatronum && !SpellCastSupport.isPatronusDarkAligned(living)) {
                    continue;
                }
                float damage = spell.getBaseDamage() * damageMultiplier;
                if (expectoPatronum && !living.isInvertedHealAndHarm()) {
                    damage = 0.0f;
                } else if (expectoPatronum) {
                    damage *= 0.55f;
                }

                if (props.getUndeadBonusDamage() > 0 && living.isInvertedHealAndHarm()) {
                    damage += props.getUndeadBonusDamage();
                }

                if (damage > 0) {
                    living.hurt(level.damageSources().magic(), damage);
                    successful = true;
                }

                spell.applyTargetEffects(living, scalingProfile.durationMult());
                successful = true;

                if (props.ignites()) {
                    SpellHelper.ignite(entity, props.getIgniteDurationSeconds());
                    successful = true;
                }

                if (props.getKnockbackStrength() != 0) {
                    float baseKnockback = spell.getBaseKnockback() != 0.0f ? spell.getBaseKnockback() : props.getKnockbackStrength();
                    SpellHelper.applyKnockback(entity, look, baseKnockback * scalingProfile.controlMult());
                    successful = true;
                }
            }
        }

        if (props.ignites()) {
            SpellHelper.tryIgniteBlockAlongLook(level, caster, effectiveRange);
        }
        if (SpellCastSupport.isGlacius(spell)) {
            Vec3 end = casterEye.add(look.scale(effectiveRange));
            BlockHitResult hit = SpellHelper.raycastFromCaster(level, caster, casterEye, end,
                    ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY);
            if (hit.getType() == HitResult.Type.BLOCK) {
                if (SpellHelper.tryGlaciusBlockInteraction(level, hit, spell)) {
                    successful = true;
                }
            }
        }
        if (expectoPatronum) {
            AABB box = caster.getBoundingBox().inflate(4.5);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box,
                    living -> living != caster && living.isAlive() && SpellCastSupport.isPatronusDarkAligned(living))) {
                Vec3 away = target.position().subtract(caster.position());
                if (away.lengthSqr() < 1.0e-4) continue;
                SpellHelper.applyKnockback(target, away, 1.2f);
                if (target instanceof Mob mob) {
                    mob.setTarget(null);
                }
                successful = true;
            }
        }

        Vec3 beamEnd = casterEye.add(look.scale(effectiveRange * 0.65));
        SpellHelper.spawnBeam(level, spell, casterEye, beamEnd);
        return successful;
    }

    private static boolean handleAccioCone(ServerLevel level, ServerPlayer caster,
                                           Spell spell, SpellProperties props,
                                           WandStats wand) {
        float effectiveRange = props.getRange() * wand.rangeFor(spell);
        List<Entity> entities = SpellHelper.findEntitiesInCone(level, caster, effectiveRange, true);
        entities.addAll(SpellHelper.findAccioExtraTargets(level, caster, effectiveRange));
        Vec3 casterEye = caster.getEyePosition();

        entities.sort(Comparator
                .comparing((Entity e) -> !(e instanceof net.minecraft.world.entity.item.ItemEntity))
                .thenComparingDouble(e -> e.distanceToSqr(caster)));

        boolean successful = false;
        for (Entity entity : entities) {
            Vec3 targetCenter = entity.getBoundingBox().getCenter();
            BlockHitResult los = level.clip(new ClipContext(
                    casterEye,
                    targetCenter,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    caster));
            if (los.getType() != HitResult.Type.MISS) {
                continue;
            }

            Vec3 toCaster = casterEye.subtract(targetCenter);
            double distance = Math.max(0.1, toCaster.length());
            Vec3 desired = toCaster.normalize().scale(Math.min(1.15, props.getPullStrength() * 0.48 + (distance * 0.045)));
            Vec3 current = entity.getDeltaMovement();
            Vec3 smooth = new Vec3(
                    Mth.lerp(0.65, current.x(), desired.x()),
                    Mth.lerp(0.55, current.y(), desired.y() * 0.45 + 0.06 * Math.signum(desired.y())),
                    Mth.lerp(0.65, current.z(), desired.z()));
            entity.setDeltaMovement(smooth);
            entity.hurtMarked = true;
            entity.fallDistance = 0.0f;
            successful = true;
        }

        SpellHelper.spawnBeam(level, spell, casterEye, casterEye.add(caster.getLookAngle().scale(effectiveRange * 0.65)));
        return successful;
    }

    static boolean handleTargeted(ServerLevel level, ServerPlayer caster,
                                  Spell spell, SpellProperties props,
                                  float damageMultiplier, WandStats wand, SpellScalingProfile scalingProfile) {
        if (SpellCastSupport.isFiniteIncantatem(spell)) {
            return handleFiniteIncantatemTargeted(level, caster, spell, props, wand);
        }
        if (SpellCastSupport.isLiberacorpus(spell)) {
            return handleLiberacorpusTargeted(level, caster, spell, props, wand);
        }
        if (SpellCastSupport.isLevicorpus(spell)) {
            return handleLevicorpusTargeted(level, caster, spell, props, wand);
        }
        Vec3 start = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        float effectiveRange = props.getRange() * wand.rangeFor(spell);
        Vec3 end = start.add(look.scale(effectiveRange));

        LivingEntity target = SpellHelper.findTargetedEntity(level, caster, effectiveRange);

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
                    SpellHelper.createExplosion(level, caster,
                            Vec3.atCenterOf(pos), props.getExplosionPower(),
                            props.explosionBreaksBlocks());
                    successful = true;
                    if (SpellCastSupport.isBombarda(spell)) {
                        SpellHelper.playSpellImpact(level, blockHit.getLocation(), spell.getColor());
                        SpellHelper.pushNearbyLightweightEntities(level, caster, blockHit.getLocation(), look, 1.3f, 3.0);
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

    static boolean handleEpiskeySelf(ServerPlayer caster, Spell spell) {
        float missing = caster.getMaxHealth() - caster.getHealth();
        if (missing > 0.0f) {
            caster.heal(Math.min(4.0f, missing));
        }
        caster.removeEffect(MobEffects.POISON);
        caster.removeEffect(MobEffects.WITHER);
        caster.removeEffect(MobEffects.BLINDNESS);
        caster.removeEffect(MobEffects.NAUSEA);
        int regenDuration = missing >= 8.0f ? 120 : 80;
        caster.addEffect(new MobEffectInstance(MobEffects.REGENERATION, regenDuration, 0, false, true, true));
        return true;
    }

    static boolean handleFrigoraSelf(ServerLevel level, ServerPlayer caster, Spell spell) {
        caster.clearFire();
        caster.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 220, 0, false, true, true));
        caster.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 120, 0, false, true, true));
        Vec3 start = caster.getEyePosition();
        Vec3 end = start.add(caster.getLookAngle().scale(4.0));
        BlockHitResult blockHit = level.clip(new ClipContext(
                start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, caster));
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            SpellHelper.tryGlaciusBlockInteraction(level, blockHit, spell);
        }
        return true;
    }

    private static boolean handleFiniteIncantatemTargeted(ServerLevel level, ServerPlayer caster,
                                                        Spell spell, SpellProperties props,
                                                        WandStats wand) {
        if (!ModuleManager.isEnabled(Module.WANDS_AND_SPELLS)) {
            return false;
        }
        // TODO(finite): amplifier 1 = Finite Incantatem Totalum (area clear) — not implemented
        float effectiveRange = props.getRange() * wand.rangeFor(spell);
        LivingEntity lookedAt = SpellHelper.findTargetedEntity(level, caster, effectiveRange);
        LivingEntity target = lookedAt != null ? lookedAt : caster;

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
            Vec3 burstPos = target.getBoundingBox().getCenter();
            SpellImpactBurstS2CPacket.sendToTracking(
                    target,
                    burstPos,
                    SpellFamilies.of(spell),
                    spell.getColor(),
                    14,
                    0.14f);
            return true;
        }

        level.playSound(null, caster.blockPosition(), SoundEvents.FIRE_EXTINGUISH,
                SoundSource.PLAYERS, 0.35f, 1.55f);
        SpellImpactBurstS2CPacket.sendToTracking(
                caster,
                caster.getEyePosition(1.0f),
                SpellFamily.LIGHT,
                spell.getColor(),
                6,
                0.08f);
        return true;
    }

    private static boolean handleLiberacorpusTargeted(ServerLevel level, ServerPlayer caster,
                                                      Spell spell, SpellProperties props,
                                                      WandStats wand) {
        Vec3 start = caster.getEyePosition();
        float effectiveRange = props.getRange() * wand.rangeFor(spell);
        LivingEntity target = SpellHelper.findTargetedEntity(level, caster, effectiveRange);
        if (target == null) {
            return false;
        }
        boolean hadLevitation = target.hasEffect(MobEffects.LEVITATION);
        target.removeEffect(MobEffects.LEVITATION);
        if (hadLevitation) {
            SpellHelper.spawnBurst(level, spell, target.getBoundingBox().getCenter(), 14, 0.22);
            SpellHelper.spawnBeam(level, spell, start, target.getBoundingBox().getCenter());
            return true;
        }
        level.playSound(null, caster.blockPosition(), SoundEvents.FIRE_EXTINGUISH,
                SoundSource.PLAYERS, 0.35f, 1.55f);
        return false;
    }

    private static boolean handleLevicorpusTargeted(ServerLevel level, ServerPlayer caster,
                                                    Spell spell, SpellProperties props,
                                                    WandStats wand) {
        Vec3 start = caster.getEyePosition();
        float effectiveRange = props.getRange() * wand.rangeFor(spell);
        LivingEntity target = SpellHelper.findTargetedEntity(level, caster, effectiveRange);
        if (target == null) {
            return false;
        }
        target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 90, 1, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2, false, true, true));
        target.fallDistance = 0.0f;
        if (target instanceof Mob mob) {
            mob.setTarget(null);
        }
        spell.applyTargetEffects(target);
        SpellHelper.spawnBeam(level, spell, start, target.getBoundingBox().getCenter());
        return true;
    }

    static boolean handleRepair(ServerPlayer caster, ItemStack wandStack, int repairAmount) {
        ItemStack toRepair = wandStack == caster.getMainHandItem()
                ? caster.getOffhandItem()
                : caster.getMainHandItem();
        if (!WandHelper.isWand(wandStack) && WandHelper.isWand(caster.getMainHandItem())) {
            toRepair = caster.getOffhandItem();
        }

        if (ModDataComponents.isElderWand(toRepair)) {
            caster.displayClientMessage(
                    Component.literal("This wand refuses Reparo.").withStyle(ChatFormatting.DARK_RED), true);
            return false;
        }

        if (toRepair.isDamageableItem() && toRepair.isDamaged()) {
            int before = toRepair.getDamageValue();
            toRepair.setDamageValue(0);
            return before > 0;
        }
        return false;
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
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_OPEN,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.45f, 1.1f);
            return true;
        }
        Proficiency proficiency = spell.getProficiency(caster);
        int tier = SpellCastSupport.lockTier(state);
        if (tier == 0) {
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(),
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.4f, 0.6f);
            caster.displayClientMessage(
                    Component.literal("Alohomora fizzles: nothing to unlock here.").withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!SpellCastSupport.canUnlockTier(proficiency, tier)) {
            String detail = switch (tier) {
                case 2 -> "Iron locks need Mastered Alohomora.";
                default -> "This lock is too complex for your current skill.";
            };
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.VILLAGER_NO,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 1.0f);
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
