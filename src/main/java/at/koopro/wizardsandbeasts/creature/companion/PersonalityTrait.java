package at.koopro.wizardsandbeasts.creature.companion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Map;

/**
 * One personality trait, from {@code data/<ns>/companion_traits/<id>.json}.
 *
 * <p>Every field has a reader; a trait is not flavour text. A creature rolls a couple of traits from its
 * species' pool when first bonded, keeps them for life and passes them to its young.
 *
 * <pre>{@code
 * { "bond_multipliers": { "gift": 2.0, "care": 0.8 }, "refuses": ["attack"], "flee_below_health": 0.5 }
 * }</pre>
 *
 * @param bondMultipliers  scales the bond a kind of interaction gives (absent = 1)
 * @param refuses          commands it will not obey until it is {@link CompanionStage#MASTERED}
 * @param fleeBelowHealth  fraction of max health below which it breaks off a fight and runs to its owner; 0 never
 * @param followDistance   scales how close it keeps to its owner while following
 * @param trainingRate     scales the training an order earns
 * @param guardsOwner      in follow mode it also turns on whatever attacks its owner, untold
 */
@NullMarked
public record PersonalityTrait(Map<Interaction, Float> bondMultipliers, List<CompanionCommand> refuses,
                               float fleeBelowHealth, float followDistance, float trainingRate, boolean guardsOwner) {

    public static final Codec<PersonalityTrait> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Interaction.CODEC, Codec.floatRange(0.0f, 5.0f))
                    .optionalFieldOf("bond_multipliers", Map.of()).forGetter(PersonalityTrait::bondMultipliers),
            CompanionCommand.CODEC.listOf().optionalFieldOf("refuses", List.of()).forGetter(PersonalityTrait::refuses),
            Codec.floatRange(0.0f, 1.0f).optionalFieldOf("flee_below_health", 0.0f)
                    .forGetter(PersonalityTrait::fleeBelowHealth),
            Codec.floatRange(0.25f, 4.0f).optionalFieldOf("follow_distance", 1.0f)
                    .forGetter(PersonalityTrait::followDistance),
            Codec.floatRange(0.0f, 5.0f).optionalFieldOf("training_rate", 1.0f)
                    .forGetter(PersonalityTrait::trainingRate),
            Codec.BOOL.optionalFieldOf("guards_owner", false).forGetter(PersonalityTrait::guardsOwner)
    ).apply(i, PersonalityTrait::new));

    public PersonalityTrait {
        bondMultipliers = Map.copyOf(bondMultipliers);
        refuses = List.copyOf(refuses);
    }

    public float multiplier(Interaction interaction) {
        return bondMultipliers.getOrDefault(interaction, 1.0f);
    }

    public static String key(String id) {
        return "companion.wizards_and_beasts.trait." + id;
    }
}
