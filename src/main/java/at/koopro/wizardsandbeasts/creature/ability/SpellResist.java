package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import org.jspecify.annotations.NonNull;

/**
 * Common ability (signature for the Graphorn): a hide that repels magic. When struck by a magic-typed
 * source it heals back {@code resistFraction} of the damage — the net effect being a damage reduction. The
 * mod's spells all deal {@code damageSources().magic()} ({@link DamageTypes#MAGIC}); indirect magic is
 * covered too. Reusable for any spell-resistant beast (Graphorn/Sphinx/Nundu).
 */
public record SpellResist(float resistFraction) implements CreatureAbility {

    public static final MapCodec<SpellResist> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("resist_fraction", 0.5f).forGetter(SpellResist::resistFraction)
    ).apply(instance, SpellResist::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.SPELL_RESIST;
    }

    @Override
    public void onHurt(@NonNull GenericBeastEntity entity, @NonNull DamageSource source, float amount) {
        if (resistFraction <= 0 || amount <= 0 || !entity.isAlive()) {
            return;
        }
        if (!source.is(DamageTypes.MAGIC) && !source.is(DamageTypes.INDIRECT_MAGIC)) {
            return;
        }
        entity.heal(amount * resistFraction);
        if (entity.level() instanceof ServerLevel level) {
            AbilitySupport.emitAtBody(level, entity, AbilitySupport.Particle.ENCHANT, 6);
        }
    }
}
