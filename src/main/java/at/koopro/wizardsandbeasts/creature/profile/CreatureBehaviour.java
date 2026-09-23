package at.koopro.wizardsandbeasts.creature.profile;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Optional;

/**
 * The half of a creature that is not numbers: how it idles, what it sounds like, what it notices and
 * how it fights.
 *
 * <p>Carried as one optional nested block on {@code CreatureDefinition} rather than as five more flat
 * fields, for two reasons. The record is already seventeen components and assembled from two
 * {@code MapCodec} halves to get past {@code RecordCodecBuilder.group}'s sixteen-argument ceiling, so
 * five more flat fields would push the second half over the same edge. And these five genuinely are
 * one concern — a creature's personality — where {@code maxHealth} and {@code followRange} are
 * another.
 *
 * <p>Every field is optional and the whole block is optional, so all ninety-six shipped creature
 * files parse unchanged and a creature that declares nothing behaves exactly as it did before. That
 * is the migration strategy: nothing is rewritten, the new systems simply have nothing to do until a
 * datapack gives them something.
 *
 * @param idle      what this creature does when nothing is happening
 * @param sounds    what it sounds like; absent means silent, which is a legitimate choice
 * @param reactions what it notices and how it answers
 * @param combat    its rhythm in a fight, separate from the abilities it fights with
 */
@NullMarked
public record CreatureBehaviour(
        Optional<IdleProfile> idle,
        Optional<SoundProfile> sounds,
        List<CreatureReaction> reactions,
        Optional<CombatProfile> combat) {

    public static final CreatureBehaviour EMPTY =
            new CreatureBehaviour(Optional.empty(), Optional.empty(), List.of(), Optional.empty());

    public static final Codec<CreatureBehaviour> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            IdleProfile.CODEC.optionalFieldOf("idle").forGetter(CreatureBehaviour::idle),
            SoundProfile.CODEC.optionalFieldOf("sounds").forGetter(CreatureBehaviour::sounds),
            CreatureReaction.CODEC.listOf().optionalFieldOf("reactions", List.of())
                    .forGetter(CreatureBehaviour::reactions),
            CombatProfile.CODEC.optionalFieldOf("combat").forGetter(CreatureBehaviour::combat)
    ).apply(instance, CreatureBehaviour::new));

    /** The idle profile, or the one its body plan implies. Never null, so callers need no branch. */
    public IdleProfile idleOrDefault(at.koopro.wizardsandbeasts.creature.BodyPlan bodyPlan) {
        return idle.orElseGet(() -> IdleProfile.forBodyPlan(bodyPlan));
    }

    /** The combat profile, or the shared approach-and-bite rhythm every creature had before this. */
    public CombatProfile combatOrDefault() {
        return combat.orElse(CombatProfile.DEFAULT);
    }
}
