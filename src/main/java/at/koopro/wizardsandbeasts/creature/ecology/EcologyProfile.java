package at.koopro.wizardsandbeasts.creature.ecology;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * A creature's ecology, declared in {@code data/wizards_and_beasts/creature_ecology/<creature id>.json}.
 *
 * <p>One file per creature, keyed by the entity id, for the same reason bond profiles are: half the roster is
 * bespoke Java with no {@code CreatureDefinition}, and a second home for the same fact would drift. Every field but
 * {@code capabilities} is optional.
 *
 * @param capabilities    what the creature is (see {@link EcologyCapability})
 * @param senseRadius     how far it notices players, predators, prey, kin and things on the ground, in blocks
 * @param herdRadius      how far a {@code herd} creature lets itself drift from the rest before it regroups
 * @param territoryRadius how far a {@code territorial} creature holds ground around its home
 * @param fedTicks        how long a {@code predator} leaves prey alone after a kill
 */
@NullMarked
public record EcologyProfile(Set<EcologyCapability> capabilities, double senseRadius, double herdRadius,
                             double territoryRadius, int fedTicks) {

    public static final EcologyProfile NONE = new EcologyProfile(EnumSet.noneOf(EcologyCapability.class), 16, 10, 16, 6000);

    private static final Codec<EcologyProfile> RAW = RecordCodecBuilder.create(instance -> instance.group(
            EcologyCapability.CODEC.listOf().fieldOf("capabilities").forGetter(p -> List.copyOf(p.capabilities())),
            Codec.doubleRange(4, 32).optionalFieldOf("sense_radius", 16.0).forGetter(EcologyProfile::senseRadius),
            Codec.doubleRange(3, 32).optionalFieldOf("herd_radius", 10.0).forGetter(EcologyProfile::herdRadius),
            Codec.doubleRange(4, 48).optionalFieldOf("territory_radius", 16.0).forGetter(EcologyProfile::territoryRadius),
            Codec.intRange(0, 72000).optionalFieldOf("fed_ticks", 6000).forGetter(EcologyProfile::fedTicks)
    ).apply(instance, (caps, sense, herd, territory, fed) -> new EcologyProfile(
            caps.isEmpty() ? EnumSet.noneOf(EcologyCapability.class) : EnumSet.copyOf(caps),
            sense, herd, territory, fed)));

    /** Contradictions are refused at load: a creature cannot both seek people out and avoid them. */
    public static final Codec<EcologyProfile> CODEC = RAW.validate(EcologyProfile::validate);

    private static DataResult<EcologyProfile> validate(EcologyProfile p) {
        if (p.has(EcologyCapability.HUMAN_AVOIDANT) && p.has(EcologyCapability.HUMAN_FRIENDLY)) {
            return DataResult.error(() -> "human-avoidant and human-friendly contradict each other");
        }
        if (p.has(EcologyCapability.AGGRESSIVE) && p.has(EcologyCapability.COWARDLY)) {
            return DataResult.error(() -> "aggressive and cowardly contradict each other");
        }
        if (p.has(EcologyCapability.AGGRESSIVE) && !p.has(EcologyCapability.TERRITORIAL)
                && !p.has(EcologyCapability.PREDATOR)) {
            return DataResult.error(() -> "aggressive needs territorial or predator: it says how the creature answers"
                    + " an intruder or prey, and alone has nothing to answer");
        }
        return DataResult.success(p);
    }

    public boolean has(EcologyCapability capability) {
        return capabilities.contains(capability);
    }

    public boolean isEmpty() {
        return capabilities.isEmpty();
    }
}
