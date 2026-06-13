package at.koopro.wizardsandbeasts.spell.impl;

import at.koopro.wizardsandbeasts.spell.core.Proficiency;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import at.koopro.wizardsandbeasts.spell.lib.SpellHelper;
import at.koopro.wizardsandbeasts.spell.core.SpellProperties;
import at.koopro.wizardsandbeasts.spell.core.SpellRequirement;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialCombatRules;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class ObscurusSurge extends Spell {

    /** Cap horizontal speed (blocks/tick) after surge so it cannot stack without bound with sprint/jump. */
    private static final double MAX_HORIZONTAL_SPEED = 0.95;

    public ObscurusSurge() {
        super("obscurus_surge", "Obscurus Surge", SpellCategory.DARK_ARTS, 80, 0.0f, 0xFF4D1A74);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.self()
                .sound(SoundEvents.ENDERMAN_SCREAM, 0.6f, 1.3f)
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        // F2: crucio is a JSON spell now — id-string requirement (resolved lazily at isMet time).
        return SpellRequirement.proficiency("crucio", Proficiency.PROFICIENT);
    }

    @Override
    public void execute(ServerLevel level, ServerPlayer caster, ItemStack wandStack) {
        Vec3 look = caster.getLookAngle().normalize();
        BlockHitResult ahead = level.clip(new ClipContext(
                caster.getEyePosition(),
                caster.getEyePosition().add(look.scale(1.5)),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                caster
        ));
        double collisionScale = ahead.getType() == HitResult.Type.BLOCK ? 0.45 : 1.0;
        Vec3 boost = look.scale(1.25).add(0, 0.08, 0);
        Vec3 v = caster.getDeltaMovement().add(boost.scale(collisionScale));
        double hx = v.x;
        double hz = v.z;
        double hLen = Math.sqrt(hx * hx + hz * hz);
        if (hLen > MAX_HORIZONTAL_SPEED && hLen > 1.0e-6) {
            double scale = MAX_HORIZONTAL_SPEED / hLen;
            v = new Vec3(hx * scale, v.y, hz * scale);
        }
        caster.setDeltaMovement(v);
        caster.hurtMarked = true;
        ObscurialCombatRules.applySurgeCosts(caster);

        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                caster.getX(), caster.getY() + 0.9, caster.getZ(),
                14, 0.45, 0.25, 0.45, 0.01);
        SpellHelper.spawnBurst(level, this, caster.getBoundingBox().getCenter(), 20, 0.35);
        playSound(level, caster);
    }
}
