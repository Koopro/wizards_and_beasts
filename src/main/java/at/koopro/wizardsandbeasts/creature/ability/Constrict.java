package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/**
 * Common ability: a serpentine coil. On melee contact the creature pulls the victim toward itself and
 * holds it with heavy Slowness (and brief mining-fatigue to mimic a pinned grapple). The
 * Occamy/Runespoor/Sea-Serpent/Grindylow/Kelpie/Basilisk kit.
 */
public record Constrict(int holdTicks, double pullStrength) implements CreatureAbility {

    public static final MapCodec<Constrict> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.optionalFieldOf("hold_ticks", 60).forGetter(Constrict::holdTicks),
            Codec.DOUBLE.optionalFieldOf("pull_strength", 0.6).forGetter(Constrict::pullStrength)
    ).apply(instance, Constrict::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.CONSTRICT;
    }

    @Override
    public void onMeleeContact(@NonNull GenericBeastEntity entity, @NonNull LivingEntity target) {
        Vec3 pull = entity.position().subtract(target.position()).normalize().scale(pullStrength);
        target.push(pull.x, 0.1, pull.z);
        target.hurtMarked = true;
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, holdTicks, 3));
        target.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, holdTicks, 1));
    }
}
