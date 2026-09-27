package at.koopro.wizardsandbeasts.entity.beast;

import at.koopro.wizardsandbeasts.effect.BasiliskVenomEffect;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * Phoenix tears: healing and the drawing-out of poison.
 *
 * <p>Canon: phoenix tears "have healing powers" (Fantastic Beasts) and are the only known antidote to basilisk
 * venom — Fawkes weeps into Harry's wound in the Chamber of Secrets and the poison goes. So the tears draw out
 * basilisk venom ({@link BasiliskVenomEffect}, which refuses every other cure), and the lesser venoms and open
 * wounds with it: poison, withering and a Sectumsempra bleed. They heal half of a body's health and leave a short
 * regeneration.
 *
 * <p>Who is wept for, and how often, is the phoenix's decision ({@link PhoenixEntity#weepIfNeeded}); this class
 * only does the weeping.
 */
public final class PhoenixTears {

    private PhoenixTears() {}

    /** What tears draw out: venom and the wounds that will not close. */
    static List<Holder<MobEffect>> drawnOut() {
        return List.of(ModEffects.BASILISK_VENOM, MobEffects.POISON, MobEffects.WITHER, ModEffects.SECTUMSEMPRA_BLEED);
    }

    /** Whether {@code target} carries something tears draw out. */
    public static boolean poisoned(LivingEntity target) {
        return drawnOut().stream().anyMatch(target::hasEffect);
    }

    /** Weeps over {@code target}: half its health back, the venom drawn out, a short regeneration. */
    public static void weepOver(ServerLevel level, PhoenixEntity phoenix, LivingEntity target) {
        target.heal(target.getMaxHealth() * 0.5f);
        // The one cure basilisk venom answers to: lift its guard for exactly these removals.
        BasiliskVenomEffect.Antidote.allowRemoval(() -> {
            for (Holder<MobEffect> effect : drawnOut()) {
                target.removeEffect(effect);
            }
        });
        target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1), phoenix);
        level.sendParticles(ParticleTypes.FALLING_WATER, target.getX(), target.getY() + target.getBbHeight(),
                target.getZ(), 24, 0.3, 0.2, 0.3, 0.0);
        level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + target.getBbHeight() * 0.6,
                target.getZ(), 6, 0.25, 0.25, 0.25, 0.01);
        level.playSound(null, target.blockPosition(), ModSounds.PHOENIX_TEARS.get(), SoundSource.NEUTRAL, 1.0f, 1.0f);
    }
}
