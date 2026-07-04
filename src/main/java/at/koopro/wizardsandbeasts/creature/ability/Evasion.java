package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import org.jspecify.annotations.NonNull;

/**
 * Common ability: precognitive evasion. When a melee attacker connects (and the dodge roll succeeds while off
 * cooldown), the creature shrugs off the blow — it heals the damage back and flickers a short distance away
 * in a puff of portal particles. The Demiguise/Doxy/Swooping-evil "never quite there" kit. Uses its own keyed
 * cooldown so it coexists with any other cooldown ability on the same beast.
 */
public record Evasion(float dodgeChance, double range, int cooldownTicks) implements CreatureAbility {

    private static final String COOLDOWN_KEY = "evade";

    public static final MapCodec<Evasion> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("dodge_chance", 0.4f).forGetter(Evasion::dodgeChance),
            Codec.DOUBLE.optionalFieldOf("range", 5.0).forGetter(Evasion::range),
            Codec.INT.optionalFieldOf("cooldown_ticks", 40).forGetter(Evasion::cooldownTicks)
    ).apply(instance, Evasion::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.EVASION;
    }

    @Override
    public void onHurt(@NonNull GenericBeastEntity entity, @NonNull DamageSource source, float amount) {
        if (!entity.isAlive() || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        // Only dodge a real attacker's blow, and only off cooldown / on a successful roll.
        if (AbilitySupport.meleeAttacker(source.getDirectEntity()) == null
                || entity.getCooldown(COOLDOWN_KEY) > 0
                || entity.getRandom().nextFloat() >= dodgeChance) {
            return;
        }
        for (int attempt = 0; attempt < 8; attempt++) {
            double tx = entity.getX() + (entity.getRandom().nextDouble() - 0.5) * 2.0 * range;
            double tz = entity.getZ() + (entity.getRandom().nextDouble() - 0.5) * 2.0 * range;
            if (entity.randomTeleport(tx, entity.getY(), tz, true)) {
                entity.heal(amount); // negate the blow it slipped
                AbilitySupport.emitAt(level, AbilitySupport.Particle.PORTAL,
                        entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(), 18, 0.35, 0.15);
                level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                        SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.6f, 1.4f);
                entity.setCooldown(COOLDOWN_KEY, cooldownTicks);
                return;
            }
        }
    }
}
