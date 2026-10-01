package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Common ability (signature for the Graphorn): a hide that repels magic. Two things, set separately:
 *
 * <ul>
 *   <li>{@code resist_fraction} — how hard the hide is to get an enchantment through: a stun, a bind or a hoist
 *       takes hold only after enough spells land at once ({@code spell.resistance.MagicResistance}).</li>
 *   <li>{@code damage_fraction} — how much of a spell's raw damage the hide heals back. Defaults to
 *       {@code resist_fraction}, which is what the single number meant before the two were split.</li>
 * </ul>
 *
 * <p>Split for the dragons (documentation/CANON_AUDIT.md C-12): a dragon takes "about half a dozen wizards" to stun
 * (<i>Goblet of Fire</i> ch. 19), but giving it the same number as damage heal-back would have been a combat
 * rebalance nobody asked for. The mod's spells deal {@code damageSources().magic()} ({@link DamageTypes#MAGIC});
 * indirect magic is covered too.
 */
public record SpellResist(float resistFraction, Optional<Float> damageFraction) implements CreatureAbility {

    public static final MapCodec<SpellResist> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("resist_fraction", 0.5f).forGetter(SpellResist::resistFraction),
            Codec.FLOAT.optionalFieldOf("damage_fraction").forGetter(SpellResist::damageFraction)
    ).apply(instance, SpellResist::new));

    /** A hide whose enchantment resistance and damage heal-back are the same number. */
    public SpellResist(float resistFraction) {
        this(resistFraction, Optional.empty());
    }

    /** The fraction of magic damage healed back. */
    public float healedFraction() {
        return damageFraction.orElse(resistFraction);
    }

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.SPELL_RESIST;
    }

    @Override
    public void onHurt(@NonNull GenericBeastEntity entity, @NonNull DamageSource source, float amount) {
        float healed = healedFraction();
        if (healed <= 0 || amount <= 0 || !entity.isAlive()) {
            return;
        }
        if (!source.is(DamageTypes.MAGIC) && !source.is(DamageTypes.INDIRECT_MAGIC)) {
            return;
        }
        entity.heal(amount * healed);
        if (entity.level() instanceof ServerLevel level) {
            AbilitySupport.emitAtBody(level, entity, AbilitySupport.Particle.ENCHANT, 6);
        }
    }
}
