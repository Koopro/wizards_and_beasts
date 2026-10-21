package at.koopro.wizardsandbeasts.creature.companion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Optional;

/**
 * How one species behaves as a companion, from {@code data/<ns>/companions/<creature id>.json}.
 *
 * <p>A side table next to {@code creature_bonds/}, keyed the same way, rather than more fields on
 * {@code BondProfile}: that record is two fields short of the codec builder's ceiling, and a species can bond
 * (be fed, follow) without being a companion. A creature is a companion iff it has both files.
 *
 * @param traitPool      personality traits it can roll, with weights
 * @param traitsPerCreature how many it rolls (distinct)
 * @param curiousAt      bond at which it stops being wild
 * @param trustingAt     bond at which it obeys the basic modes; bonded is its bond profile's follow threshold
 * @param trainedAt      training points (10 per order carried out, scaled by personality) for {@code TRAINED}
 * @param masteredAt     training at which a fully bonded, trained creature is mastered
 * @param commands       commands the species understands at all
 * @param abilities      what it does for its person
 * @param canFight       whether it can be set on a target (also needs an attack-damage attribute)
 * @param favourite      the treat it loves, worth a gift's bond once a day
 * @param breeding       habitat and compatibility, for species whose bond profile breeds
 */
@NullMarked
public record CompanionProfile(List<WeightedTrait> traitPool, int traitsPerCreature, int curiousAt, int trustingAt,
                               int trainedAt, int masteredAt, List<CompanionCommand> commands,
                               List<CompanionAbility> abilities, boolean canFight, Optional<Identifier> favourite,
                               Optional<Breeding> breeding) {

    public static final Codec<CompanionProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            WeightedTrait.CODEC.listOf().optionalFieldOf("trait_pool", List.of()).forGetter(CompanionProfile::traitPool),
            Codec.intRange(0, 4).optionalFieldOf("traits_per_creature", 2).forGetter(CompanionProfile::traitsPerCreature),
            Codec.intRange(0, 1000).optionalFieldOf("curious_at", 10).forGetter(CompanionProfile::curiousAt),
            Codec.intRange(0, 1000).optionalFieldOf("trusting_at", 30).forGetter(CompanionProfile::trustingAt),
            Codec.intRange(0, 10_000).optionalFieldOf("trained_at", 200).forGetter(CompanionProfile::trainedAt),
            Codec.intRange(0, 10_000).optionalFieldOf("mastered_at", 500).forGetter(CompanionProfile::masteredAt),
            CompanionCommand.CODEC.listOf().fieldOf("commands").forGetter(CompanionProfile::commands),
            CompanionAbility.CODEC.listOf().optionalFieldOf("abilities", List.of()).forGetter(CompanionProfile::abilities),
            Codec.BOOL.optionalFieldOf("can_fight", false).forGetter(CompanionProfile::canFight),
            Identifier.CODEC.optionalFieldOf("favourite").forGetter(CompanionProfile::favourite),
            Breeding.CODEC.optionalFieldOf("breeding").forGetter(CompanionProfile::breeding)
    ).apply(i, CompanionProfile::new));

    public CompanionProfile {
        traitPool = List.copyOf(traitPool);
        commands = List.copyOf(commands);
        abilities = List.copyOf(abilities);
    }

    public record WeightedTrait(String trait, int weight) {
        public static final Codec<WeightedTrait> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("trait").forGetter(WeightedTrait::trait),
                Codec.intRange(1, 100).optionalFieldOf("weight", 1).forGetter(WeightedTrait::weight)
        ).apply(i, WeightedTrait::new));
    }

    /**
     * Where and with whom young are raised.
     *
     * @param habitat          what must be around the pair; absent = anywhere
     * @param compatible       other species it may pair with (its own always)
     * @param mutationChance   chance a juvenile's second trait is a fresh roll instead of a parent's
     */
    public record Breeding(Optional<Habitat> habitat, List<Identifier> compatible, float mutationChance) {
        public static final Codec<Breeding> CODEC = RecordCodecBuilder.create(i -> i.group(
                Habitat.CODEC.optionalFieldOf("habitat").forGetter(Breeding::habitat),
                Identifier.CODEC.listOf().optionalFieldOf("compatible", List.of()).forGetter(Breeding::compatible),
                Codec.floatRange(0.0f, 1.0f).optionalFieldOf("mutation_chance", 0.15f).forGetter(Breeding::mutationChance)
        ).apply(i, Breeding::new));
    }

    /**
     * What a breeding pair needs around it, all of which must hold.
     *
     * @param needsSky open sky above the pair (mooncalves dance under the moon)
     * @param blocks   blocks of this tag that must be near
     * @param count    how many of them
     * @param radius   how near
     * @param biomes   biomes it breeds in
     */
    public record Habitat(boolean needsSky, Optional<TagKey<Block>> blocks, int count, int radius,
                          Optional<TagKey<Biome>> biomes) {
        public static final Codec<Habitat> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("needs_sky", false).forGetter(Habitat::needsSky),
                TagKey.codec(Registries.BLOCK).optionalFieldOf("blocks").forGetter(Habitat::blocks),
                Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(Habitat::count),
                Codec.intRange(1, 8).optionalFieldOf("radius", 4).forGetter(Habitat::radius),
                TagKey.codec(Registries.BIOME).optionalFieldOf("biomes").forGetter(Habitat::biomes)
        ).apply(i, Habitat::new));
    }

    public boolean understands(CompanionCommand command) {
        return commands.contains(command);
    }
}
