package at.koopro.wizardsandbeasts.creature.ecology;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.NullMarked;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static at.koopro.wizardsandbeasts.creature.ecology.EcologyCapability.*;

/**
 * A creature's ecology, declared in {@code data/wizards_and_beasts/creature_ecology/<creature id>.json}.
 *
 * <p>One file per creature, keyed by the entity id, for the same reason bond profiles are: half the roster is
 * bespoke Java with no {@code CreatureDefinition}, and a second home for the same fact would drift. A file may
 * {@code "extends"} an archetype in {@code creature_ecology/_archetypes/} ({@link EcologyProfileFiles}). Every field
 * but {@code capabilities} is optional.
 *
 * @param capabilities    what the creature is (see {@link EcologyCapability})
 * @param senseRadius     how far it notices players, predators, prey, kin and things on the ground, in blocks
 * @param herdRadius      how far a {@code herd} creature lets itself drift from the rest before it regroups
 * @param territoryRadius how far a {@code territorial} creature holds ground around its home
 * @param fedTicks        how long a {@code predator} without a hunger need leaves prey alone after a kill
 * @param needs           the drives it has, each with ticks from satisfied to desperate ({@link EcologyNeed})
 * @param personality     stated personality ranges; the rest follow from capabilities ({@link EcologyPersonality})
 * @param personalSpace   how close a {@code solitary} creature lets its own kind come
 * @param huntStyle       how a {@code predator} closes on prey
 * @param forageBlocks    what a grazer/browser/insectivore eats, replacing its diet's default blocks
 * @param foodItems       food it goes to on the ground when hungry, besides meat for a scavenger
 */
@NullMarked
public record EcologyProfile(Set<EcologyCapability> capabilities, double senseRadius, double herdRadius,
                             double territoryRadius, int fedTicks, Map<EcologyNeed, Integer> needs,
                             Map<EcologyPersonality.Axis, EcologyPersonality.Range> personality,
                             double personalSpace, HuntStyle huntStyle, Optional<TagKey<Block>> forageBlocks,
                             Optional<TagKey<Item>> foodItems) {

    public static final EcologyProfile NONE = new EcologyProfile(EnumSet.noneOf(EcologyCapability.class), 16, 10, 16, 6000);

    /** The plain profile: capabilities and radii, no needs, no stated personality. */
    public EcologyProfile(Set<EcologyCapability> capabilities, double senseRadius, double herdRadius,
                          double territoryRadius, int fedTicks) {
        this(capabilities, senseRadius, herdRadius, territoryRadius, fedTicks, Map.of(), Map.of(), 12.0,
                HuntStyle.CHASE, Optional.empty(), Optional.empty());
    }

    /** How a predator closes on prey. */
    public enum HuntStyle implements StringRepresentable {
        /** Runs it down from wherever it is seen. */
        CHASE,
        /** Creeps up slowly, low, and only charges from close. */
        STALK;

        public static final Codec<HuntStyle> CODEC = StringRepresentable.fromEnum(HuntStyle::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private static final Codec<Map<EcologyNeed, Integer>> NEEDS_CODEC =
            Codec.unboundedMap(EcologyNeed.CODEC, Codec.intRange(200, 240000));
    private static final Codec<Map<EcologyPersonality.Axis, EcologyPersonality.Range>> PERSONALITY_CODEC =
            Codec.unboundedMap(EcologyPersonality.Axis.CODEC, EcologyPersonality.Range.CODEC);

    private static final Codec<EcologyProfile> RAW = RecordCodecBuilder.create(instance -> instance.group(
            EcologyCapability.CODEC.listOf().fieldOf("capabilities").forGetter(p -> List.copyOf(p.capabilities())),
            Codec.doubleRange(4, 32).optionalFieldOf("sense_radius", 16.0).forGetter(EcologyProfile::senseRadius),
            Codec.doubleRange(3, 32).optionalFieldOf("herd_radius", 10.0).forGetter(EcologyProfile::herdRadius),
            Codec.doubleRange(4, 48).optionalFieldOf("territory_radius", 16.0).forGetter(EcologyProfile::territoryRadius),
            Codec.intRange(0, 72000).optionalFieldOf("fed_ticks", 6000).forGetter(EcologyProfile::fedTicks),
            NEEDS_CODEC.optionalFieldOf("needs", Map.of()).forGetter(EcologyProfile::needs),
            PERSONALITY_CODEC.optionalFieldOf("personality", Map.of()).forGetter(EcologyProfile::personality),
            Codec.doubleRange(2, 32).optionalFieldOf("personal_space", 12.0).forGetter(EcologyProfile::personalSpace),
            HuntStyle.CODEC.optionalFieldOf("hunt_style", HuntStyle.CHASE).forGetter(EcologyProfile::huntStyle),
            TagKey.hashedCodec(Registries.BLOCK).optionalFieldOf("forage_blocks").forGetter(EcologyProfile::forageBlocks),
            TagKey.hashedCodec(Registries.ITEM).optionalFieldOf("food_items").forGetter(EcologyProfile::foodItems)
    ).apply(instance, (caps, sense, herd, territory, fed, needs, personality, space, hunt, forage, food) ->
            new EcologyProfile(caps.isEmpty() ? EnumSet.noneOf(EcologyCapability.class) : EnumSet.copyOf(caps),
                    sense, herd, territory, fed, needs.isEmpty() ? Map.of() : new EnumMap<>(needs),
                    personality.isEmpty() ? Map.of() : new EnumMap<>(personality), space, hunt, forage, food)));

    /** Contradictions are refused at load: a creature cannot both seek people out and avoid them. */
    public static final Codec<EcologyProfile> CODEC = RAW.validate(EcologyProfile::validate);

    static DataResult<EcologyProfile> validate(EcologyProfile p) {
        if (p.has(HUMAN_AVOIDANT) && p.has(HUMAN_FRIENDLY)) {
            return DataResult.error(() -> "human-avoidant and human-friendly contradict each other");
        }
        if (p.has(AGGRESSIVE) && p.has(COWARDLY)) {
            return DataResult.error(() -> "aggressive and cowardly contradict each other");
        }
        if (p.has(AGGRESSIVE) && !p.has(TERRITORIAL) && !p.has(PREDATOR)) {
            return DataResult.error(() -> "aggressive needs territorial or predator: it says how the creature answers"
                    + " an intruder or prey, and alone has nothing to answer");
        }
        int rhythms = (p.has(NOCTURNAL) ? 1 : 0) + (p.has(DIURNAL) ? 1 : 0) + (p.has(CREPUSCULAR) ? 1 : 0);
        if (rhythms > 1) {
            return DataResult.error(() -> "nocturnal, diurnal and crepuscular are three different days: pick one");
        }
        if (p.has(SOLITARY) && (p.has(HERD) || p.has(SOCIAL))) {
            return DataResult.error(() -> "solitary contradicts herd and social");
        }
        if (p.has(COLD_ADAPTED) && p.has(WARMTH_LOVING)) {
            return DataResult.error(() -> "cold-adapted and warmth-loving contradict each other");
        }
        if (p.needs.containsKey(EcologyNeed.THIRST) && p.has(AQUATIC)) {
            return DataResult.error(() -> "an aquatic creature lives in its water and has no thirst to walk to it");
        }
        boolean eats = p.has(GRAZER) || p.has(BROWSER) || p.has(INSECTIVORE) || p.has(PREDATOR) || p.has(SCAVENGER)
                || p.foodItems.isPresent();
        if (p.needs.containsKey(EcologyNeed.HUNGER) && !eats) {
            return DataResult.error(() -> "hunger with nothing to eat: add a diet (grazer, browser, insectivore,"
                    + " predator, scavenger) or food_items");
        }
        if ((p.has(GRAZER) || p.has(BROWSER) || p.has(INSECTIVORE)) && !p.needs.containsKey(EcologyNeed.HUNGER)) {
            return DataResult.error(() -> "a diet is acted on through hunger: add \"needs\": {\"hunger\": ...}");
        }
        if (p.needs.containsKey(EcologyNeed.SOCIAL) && !p.has(HERD) && !p.has(SOCIAL)) {
            return DataResult.error(() -> "a social need belongs to a herd or social creature");
        }
        if (p.needs.containsKey(EcologyNeed.CURIOSITY) && !p.has(CURIOUS)) {
            return DataResult.error(() -> "a curiosity need belongs to a curious creature");
        }
        if (p.needs.containsKey(EcologyNeed.MAGIC) && !p.has(MAGICAL_SENSE)) {
            return DataResult.error(() -> "a magic need belongs to a creature with a magical sense");
        }
        return DataResult.success(p);
    }

    public boolean has(EcologyCapability capability) {
        return capabilities.contains(capability);
    }

    public boolean needs(EcologyNeed need) {
        return needs.containsKey(need);
    }

    public boolean isEmpty() {
        return capabilities.isEmpty();
    }

    /** Whether it forages from blocks: a grazer, browser or insectivore. */
    public boolean foragesBlocks() {
        return has(GRAZER) || has(BROWSER) || has(INSECTIVORE) || forageBlocks.isPresent();
    }

    /** Its personality ranges: implied by capabilities, overridden by what the profile states. */
    public Map<EcologyPersonality.Axis, EcologyPersonality.Range> personalityRanges() {
        return EcologyPersonality.rangesFor(capabilities, personality);
    }
}
