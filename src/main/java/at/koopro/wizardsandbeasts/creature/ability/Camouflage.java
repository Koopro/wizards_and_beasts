package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.jspecify.annotations.NonNull;

/**
 * Common ability: passive concealment. The creature stays Invisible while idle and only becomes visible for
 * {@code revealTicks} after it attacks or is hurt (tracked on the shared ability cooldown). The
 * Demiguise/Moke/Tebo/Pogrebin kit. Invisibility is refreshed each tick while concealed.
 */
public record Camouflage(int revealTicks) implements CreatureAbility {

    /** Private key for the post-hit reveal window — its own timer, not a shared "cooldown". */
    private static final String REVEAL_KEY = "camo_reveal";

    public static final MapCodec<Camouflage> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.optionalFieldOf("reveal_ticks", 60).forGetter(Camouflage::revealTicks)
    ).apply(instance, Camouflage::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.CAMOUFLAGE;
    }

    /** Remaining Invisibility below which it is topped up (the effect lasts 40 ticks). */
    public static final int REFRESH_BELOW_TICKS = 20;

    @Override
    public void tick(@NonNull GenericBeastEntity entity) {
        if (entity.level().isClientSide()) {
            return;
        }
        // Revealed window: counts down on its own keyed timer after a hit (set in onHurt / onMeleeContact).
        // Topped up before it can lapse rather than every tick: the creature is just as continuously invisible, but a
        // fresh effect (and the effect-update bookkeeping it triggers) is made twice a second, not twenty times.
        if (entity.getCooldown(REVEAL_KEY) <= 0) {
            MobEffectInstance current = entity.getEffect(MobEffects.INVISIBILITY);
            if (current == null || current.getDuration() <= REFRESH_BELOW_TICKS) {
                entity.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, true, false));
            }
        }
    }

    @Override
    public void onHurt(@NonNull GenericBeastEntity entity, net.minecraft.world.damagesource.DamageSource source, float amount) {
        reveal(entity);
    }

    @Override
    public void onMeleeContact(@NonNull GenericBeastEntity entity, net.minecraft.world.entity.LivingEntity target) {
        reveal(entity);
    }

    private void reveal(GenericBeastEntity entity) {
        entity.removeEffect(MobEffects.INVISIBILITY);
        entity.setCooldown(REVEAL_KEY, Math.max(entity.getCooldown(REVEAL_KEY), revealTicks));
    }
}
