package at.koopro.wizardsandbeasts.spell.impl;

import at.koopro.wizardsandbeasts.spell.core.Proficiency;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import at.koopro.wizardsandbeasts.spell.core.SpellProperties;
import at.koopro.wizardsandbeasts.spell.core.SpellRequirement;
import at.koopro.wizardsandbeasts.spell.lib.SpellHelper;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialCombatRules;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialResourceManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class ObscurusGrasp extends Spell {

    private static final float GRASP_RADIUS = 6.0f;
    private static final double PULL_EPSILON_SQ = 1.0e-4;

    public ObscurusGrasp() {
        super("obscurus_grasp", "Obscurus Grasp", SpellCategory.DARK_ARTS, 100, 0.0f, 0xFF5A2B84);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.cone(GRASP_RADIUS)
                .sound(SoundEvents.WITHER_SHOOT, 0.6f, 0.8f)
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.proficiency(Spells.IMPERIO, Proficiency.PROFICIENT);
    }

    @Override
    public void execute(ServerLevel level, ServerPlayer caster, ItemStack wandStack) {
        Vec3 center = caster.position();
        Vec3 eye = caster.getEyePosition();
        AABB box = caster.getBoundingBox().inflate(GRASP_RADIUS);
        List<Entity> entities = level.getEntities(caster, box,
                entity -> entity instanceof LivingEntity living && living.isAlive() && entity != caster);

        int affected = 0;
        for (Entity entity : entities) {
            if (!(entity instanceof LivingEntity living) || entity == caster) continue;
            Vec3 targetCenter = entity.getBoundingBox().getCenter();
            BlockHitResult los = level.clip(new ClipContext(
                    eye,
                    targetCenter,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.ANY,
                    caster));
            if (los.getType() != HitResult.Type.MISS) {
                continue;
            }
            Vec3 delta = center.subtract(entity.position());
            double dx = delta.x;
            double dz = delta.z;
            double lenSq = dx * dx + dz * dz;
            if (lenSq < PULL_EPSILON_SQ) {
                continue;
            }
            double invLen = 1.0 / Math.sqrt(lenSq);
            Vec3 pull = new Vec3(dx * invLen, 0.0, dz * invLen).scale(0.45);
            Vec3 blended = entity.getDeltaMovement().scale(0.35).add(pull);
            entity.setDeltaMovement(blended.x, Math.max(-0.08, blended.y + 0.08), blended.z);
            entity.hurtMarked = true;
            entity.fallDistance = 0f;
            living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1, false, true, true));
            affected++;
        }

        ObscurialCombatRules.applyGraspCosts(caster);
        if (affected > 0) {
            ObscurialResourceManager.addStress(caster, Math.min(4.0f, affected * 0.5f));
        }

        level.sendParticles(ParticleTypes.WITCH,
                caster.getX(), caster.getY() + 0.8, caster.getZ(),
                18, 1.3, 0.4, 1.3, 0.02);
        Vec3 beamEnd = eye.add(caster.getLookAngle().scale(GRASP_RADIUS * 0.9));
        SpellHelper.spawnBeam(level, this, eye, beamEnd);
        playSound(level, caster);
    }
}
