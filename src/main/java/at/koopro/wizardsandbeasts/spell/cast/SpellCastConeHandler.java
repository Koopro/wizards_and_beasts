package at.koopro.wizardsandbeasts.spell.cast;

import at.koopro.wizardsandbeasts.spell.core.*;
import at.koopro.wizardsandbeasts.spell.lib.*;
import at.koopro.wizardsandbeasts.spell.proficiency.*;

import at.koopro.wizardsandbeasts.spell.effect.SpellEffectContext;
import at.koopro.wizardsandbeasts.spell.effect.SpellEffectRunner;
import at.koopro.wizardsandbeasts.spell.proficiency.SpellScalingProfile;
import at.koopro.wizardsandbeasts.wand.cast.WandStats;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

final class SpellCastConeHandler {

    private SpellCastConeHandler() {
    }

    static boolean handle(ServerLevel level, ServerPlayer caster,
                          Spell spell, SpellProperties props,
                          float damageMultiplier, WandStats wand, SpellScalingProfile scalingProfile) {
        if (SpellCastSupport.isAccio(spell)) {
            return handleAccio(level, caster, spell, props, wand);
        }
        Vec3 look = caster.getLookAngle();
        boolean pullsItems = props.getPullStrength() != 0;
        float effectiveRange = props.getRange() * wand.rangeFor(spell);
        List<Entity> entities = SpellTargetHelper.findEntitiesInCone(level, caster, effectiveRange, pullsItems);

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

                // Data-driven effect components (Step 3): per-target run of the authored effect list.
                // No-op for spells without an effects list (all Java spells today).
                SpellEffectRunner.run(spell, SpellEffectContext.ofTarget(caster, living, level));

                if (props.ignites()) {
                    SpellHelper.ignite(entity, props.getIgniteDurationSeconds());
                    successful = true;
                }

                if (props.getKnockbackStrength() != 0) {
                    float baseKnockback = spell.getBaseKnockback() != 0.0f
                            ? spell.getBaseKnockback()
                            : props.getKnockbackStrength();
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
                if (away.lengthSqr() < 1.0e-4) {
                    continue;
                }
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

    private static boolean handleAccio(ServerLevel level, ServerPlayer caster,
                                       Spell spell, SpellProperties props,
                                       WandStats wand) {
        float effectiveRange = props.getRange() * wand.rangeFor(spell);
        List<Entity> entities = SpellTargetHelper.findEntitiesInCone(level, caster, effectiveRange, true);
        entities.addAll(SpellTargetHelper.findAccioExtraTargets(level, caster, effectiveRange));
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
}
