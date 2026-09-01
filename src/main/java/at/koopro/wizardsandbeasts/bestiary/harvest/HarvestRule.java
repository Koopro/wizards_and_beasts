package at.koopro.wizardsandbeasts.bestiary.harvest;

import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.NullMarked;

/**
 * One rare material a creature yields, and the study it takes to get it.
 *
 * <p>Lives at {@code data/<namespace>/bestiary/harvest/<id>.json}:
 *
 * <pre>{@code
 * {
 *   "entry": "wizards_and_beasts:unicorn",
 *   "item": "wizards_and_beasts:unicorn_tail_hair",
 *   "minTier": "MASTERED",
 *   "chance": 0.35,
 *   "cooldownSeconds": 600
 * }
 * }</pre>
 *
 * <h2>Why a side table rather than a field on {@code BestiaryEntry}</h2>
 *
 * <p>Two reasons, and the first is hard. {@code BestiaryEntry} already has <b>exactly sixteen</b> codec
 * fields, which is {@code RecordCodecBuilder.group}'s ceiling — a seventeenth does not compile, and
 * getting one would mean restructuring the shipped format of all 107 entries. The second is that a
 * creature yields <em>several</em> materials at different tiers, so the natural shape is a list of rules
 * keyed by creature, not a field on the creature.
 *
 * <p>A rule <b>only ever adds</b> loot. Nothing here can remove, replace or reduce a drop, which is how
 * "basic drops never disappear" is guaranteed by construction rather than by being careful.
 *
 * @param entry           the bestiary entry whose tier gates this — <em>not</em> the entity type, because
 *                        the tier is held per entry and an entry is what a player actually studies
 * @param item            what drops
 * @param minTier         the tier the killer must have reached. {@link DiscoveryTier#UNDISCOVERED} is
 *                        rejected: a rule that gates on nothing is an unconditional extra drop, which
 *                        belongs in the creature's own loot table.
 * @param chance          0–1 probability, rolled per kill after the tier check passes
 * @param minCount        smallest stack size, at least 1
 * @param maxCount        largest stack size, never below {@code minCount}
 * @param cooldownSeconds per-player, per-entry lockout after a successful harvest. Zero means none,
 *                        which is right for a common material and wrong for one a mob farm can print.
 */
@NullMarked
public record HarvestRule(Identifier entry,
                          Item item,
                          DiscoveryTier minTier,
                          float chance,
                          int minCount,
                          int maxCount,
                          int cooldownSeconds) {

    public static final Codec<HarvestRule> CODEC = RecordCodecBuilder.<HarvestRule>create(
            instance -> instance.group(
                    Identifier.CODEC.fieldOf("entry").forGetter(HarvestRule::entry),
                    BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(HarvestRule::item),
                    DiscoveryTier.CODEC.fieldOf("minTier").forGetter(HarvestRule::minTier),
                    Codec.floatRange(0.0f, 1.0f).optionalFieldOf("chance", 1.0f).forGetter(HarvestRule::chance),
                    Codec.INT.optionalFieldOf("minCount", 1).forGetter(HarvestRule::minCount),
                    Codec.INT.optionalFieldOf("maxCount", 1).forGetter(HarvestRule::maxCount),
                    Codec.INT.optionalFieldOf("cooldownSeconds", 0).forGetter(HarvestRule::cooldownSeconds)
            ).apply(instance, HarvestRule::new))
            .validate(HarvestRule::validate);

    public HarvestRule {
        minCount = Math.max(1, minCount);
        maxCount = Math.max(minCount, maxCount);
        cooldownSeconds = Math.max(0, cooldownSeconds);
    }

    /**
     * Refused at load rather than at kill time. A rule that can never fire, or that fires
     * unconditionally, produces no error and no visible effect — the worst failure mode for content
     * whose whole job is to be rare.
     */
    private static DataResult<HarvestRule> validate(HarvestRule rule) {
        if (rule.minTier == DiscoveryTier.UNDISCOVERED) {
            return DataResult.error(() -> "minTier UNDISCOVERED gates nothing; an unconditional extra "
                    + "drop belongs in the creature's own loot table, not in a harvest rule");
        }
        if (rule.chance <= 0.0f) {
            return DataResult.error(() -> "chance " + rule.chance + " means this rule can never drop");
        }
        return DataResult.success(rule);
    }

    /** True once {@code held} is deep enough for this rule. */
    public boolean tierSatisfiedBy(DiscoveryTier held) {
        return held.ordinal() >= minTier.ordinal();
    }
}
