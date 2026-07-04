package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

/**
 * Common ability: blood-frenzy. Each landed melee hit stacks a refreshing Strength (and Haste) buff up to
 * {@code maxStacks}, decaying when the creature stops connecting. Models a beast that winds itself into a
 * killing fury (Manticore, Wampus-cat, Werewolf). Stack count is carried by the effect's own amplifier, so no
 * extra per-entity state is needed — the buff simply expires once hits stop.
 */
public record Frenzy(int maxStacks, int decayTicks) implements CreatureAbility {

    public static final MapCodec<Frenzy> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.optionalFieldOf("max_stacks", 3).forGetter(Frenzy::maxStacks),
            Codec.INT.optionalFieldOf("decay_ticks", 80).forGetter(Frenzy::decayTicks)
    ).apply(instance, Frenzy::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.FRENZY;
    }

    @Override
    public void onMeleeContact(@NonNull GenericBeastEntity entity, @NonNull LivingEntity target) {
        if (entity.level().isClientSide()) {
            return;
        }
        MobEffectInstance current = entity.getEffect(MobEffects.STRENGTH);
        int next = Math.min(maxStacks - 1, current != null ? current.getAmplifier() + 1 : 0);
        entity.addEffect(new MobEffectInstance(MobEffects.STRENGTH, decayTicks, next, true, true));
        entity.addEffect(new MobEffectInstance(MobEffects.HASTE, decayTicks, next, true, true));
    }
}
