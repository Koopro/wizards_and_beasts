package at.koopro.wizardsandbeasts.effect;

import at.koopro.wizardsandbeasts.apparition.splinch.SplinchDamageTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jspecify.annotations.NonNull;

/**
 * Left part of yourself behind. The mark of a botched Apparition: you keep bleeding until someone puts you
 * back together, and you cannot Apparate again while a piece of you is still elsewhere.
 *
 * <p>Amplifier is the <b>severity</b> — 0 is a clean tear, 1 is a bad one — and it scales how fast the wound
 * bleeds and how much it slows and weakens you. It no longer scales lethality: neither amplifier can kill,
 * because the bleed runs through the clamped splinch damage type. This effect is the single source of truth
 * for "is this player splinched"; nothing stores a parallel severity or countdown, so it cannot desync from
 * the potion HUD the way a shadow copy would.
 */
public final class SplinchedEffect extends MobEffect {

    /** Wound colour — dried blood, matching the splinch chat feedback. */
    private static final int COLOR = 0x8A1F1F;

    /** Bleed interval in ticks at severity 0; a worse splinch bleeds twice as often. */
    private static final int BLEED_INTERVAL_TICKS = 60;
    private static final float BLEED_DAMAGE = 1.0f;

    /**
     * Amplifier at which the wound is a bad one: it bleeds twice as fast and twice as often.
     *
     * <p>It is no longer <i>lethal</i>. Splinching is bloody and agonising in the books and never fatal on
     * the page, so the bleed now runs through {@code SplinchDamageTypes.SPLINCH}, which
     * {@code SplinchDamageHandler} clamps at half a heart for both amplifiers. Anything else that hits you
     * while you are lying there at one heart still kills you perfectly well.
     */
    private static final float SEVERE_AMPLIFIER = 1;

    public SplinchedEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
        addAttributeModifier(Attributes.MOVEMENT_SPEED,
                net.minecraft.resources.Identifier.fromNamespaceAndPath(
                        at.koopro.wizardsandbeasts.WizardsAndBeastsMod.MODID, "effect.splinched.speed"),
                -0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.ATTACK_DAMAGE,
                net.minecraft.resources.Identifier.fromNamespaceAndPath(
                        at.koopro.wizardsandbeasts.WizardsAndBeastsMod.MODID, "effect.splinched.attack"),
                -0.20, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public boolean applyEffectTick(@NonNull ServerLevel level, @NonNull LivingEntity entity, int amplifier) {
        boolean severe = amplifier >= SEVERE_AMPLIFIER;
        float damage = BLEED_DAMAGE * (severe ? 2.0f : 1.0f);

        // No floor here: the splinch damage type is clamped at half a heart for every amplifier, in one place,
        // so the bleed and the arrival wound cannot disagree about how survivable a splinch is.
        entity.hurt(level.damageSources().source(SplinchDamageTypes.SPLINCH), damage);

        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(),
                severe ? 6 : 3, 0.25, 0.3, 0.25, 0.0);
        if (severe) {
            level.playSound(null, entity.blockPosition(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.25f, 0.7f);
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        int interval = amplifier >= SEVERE_AMPLIFIER ? BLEED_INTERVAL_TICKS / 2 : BLEED_INTERVAL_TICKS;
        return duration % interval == 0;
    }

    @Override
    public boolean isBeneficial() {
        return false;
    }
}
