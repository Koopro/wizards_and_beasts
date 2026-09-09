package at.koopro.wizardsandbeasts.creature.bond;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * How one species forms a relationship with a player, declared in a datapack.
 *
 * <p>Lives at {@code data/<namespace>/creature_bonds/<creature id>.json}:
 *
 * <pre>{@code
 * {
 *   "feeds": [ { "item": "minecraft:stick", "gain": 10, "cooldownSeconds": 45 } ],
 *   "followThreshold": 50,
 *   "milestones": [25, 50, 75, 100],
 *   "gift": { "item": "wizards_and_beasts:holly_sapling", "minBond": 75, "cooldownSeconds": 900 }
 * }
 * }</pre>
 *
 * <h2>Why a side directory rather than a field on {@code CreatureDefinition}</h2>
 *
 * <p>Because the creatures worth bonding to are exactly the ones that have no
 * {@code CreatureDefinition}. Bowtruckle, Mooncalf, Thestral, Phoenix and the Niffler are bespoke
 * entity classes registered from {@code ModCreatures.BESPOKE_IDS}, and none of them ships a
 * {@code creatures/*.json} — putting the profile there would have made the layer reach every
 * creature except the five it was extracted from. Keyed by entity id instead, one directory serves
 * the bespoke classes and the data-driven ninety-six identically.
 *
 * <p><b>Presence of the file is the opt-in.</b> There is no {@code bondable} boolean anywhere: a
 * creature bonds if and only if a profile is loaded for its id, so adding the relationship layer to
 * a data-driven creature is a datapack file and no Java at all.
 *
 * @param feeds            what it accepts, in the order they are tried; the first match wins
 * @param maxBond          the ceiling, 100 by convention (the Niffler's)
 * @param followThreshold  bond at which it starts following its owner
 * @param milestones       bond levels that fire a {@code MagizoologyXPEvent} when first crossed
 * @param proximityRange   blocks within which simply being near the owner counts as company
 * @param proximitySeconds seconds of company per {@code proximityGain}
 * @param proximityGain    bond awarded per completed company interval
 * @param betrayalPenalty  bond lost when the <em>owner</em> is the one that hurt it
 * @param gift             what it hands over while bonded, if anything
 * @param breeding         how it raises young, if it does
 * @param masteryBond      bond at which the creature's bestiary entry is marked {@code MASTERED};
 *                         absent means bonding never touches the book. This is the Niffler's
 *                         hard-coded "80 sets MASTERED" rule, made a number instead of a branch
 * @param xpSource         tag on the milestone XP event; defaults to {@code <creature>_bond}
 * @param feedSound        sound on a successful feed
 * @param refuseSound      sound when it is still full, or when the offering is refused
 */
@NullMarked
public record BondProfile(
        List<BondFeed> feeds,
        int maxBond,
        int followThreshold,
        List<Integer> milestones,
        double proximityRange,
        int proximitySeconds,
        int proximityGain,
        int betrayalPenalty,
        Optional<BondGift> gift,
        Optional<BondBreeding> breeding,
        Optional<Integer> masteryBond,
        Optional<String> xpSource,
        Optional<Identifier> feedSound,
        Optional<Identifier> refuseSound) {

    /** The Niffler's numbers, which every other species is a variation on. */
    private static final int DEFAULT_MAX_BOND = 100;
    private static final int DEFAULT_FOLLOW_THRESHOLD = 50;
    private static final List<Integer> DEFAULT_MILESTONES = List.of(25, 50, 75, 100);
    private static final double DEFAULT_PROXIMITY_RANGE = 6.0;
    private static final int DEFAULT_PROXIMITY_SECONDS = 30;

    public static final Codec<BondProfile> CODEC = RecordCodecBuilder.<BondProfile>create(
            instance -> instance.group(
                    BondFeed.CODEC.listOf().optionalFieldOf("feeds", List.of()).forGetter(BondProfile::feeds),
                    Codec.INT.optionalFieldOf("maxBond", DEFAULT_MAX_BOND).forGetter(BondProfile::maxBond),
                    Codec.INT.optionalFieldOf("followThreshold", DEFAULT_FOLLOW_THRESHOLD)
                            .forGetter(BondProfile::followThreshold),
                    Codec.INT.listOf().optionalFieldOf("milestones", DEFAULT_MILESTONES)
                            .forGetter(BondProfile::milestones),
                    Codec.DOUBLE.optionalFieldOf("proximityRange", DEFAULT_PROXIMITY_RANGE)
                            .forGetter(BondProfile::proximityRange),
                    Codec.INT.optionalFieldOf("proximitySeconds", DEFAULT_PROXIMITY_SECONDS)
                            .forGetter(BondProfile::proximitySeconds),
                    Codec.INT.optionalFieldOf("proximityGain", 1).forGetter(BondProfile::proximityGain),
                    Codec.INT.optionalFieldOf("betrayalPenalty", 10).forGetter(BondProfile::betrayalPenalty),
                    BondGift.CODEC.optionalFieldOf("gift").forGetter(BondProfile::gift),
                    BondBreeding.CODEC.optionalFieldOf("breeding").forGetter(BondProfile::breeding),
                    Codec.INT.optionalFieldOf("masteryBond").forGetter(BondProfile::masteryBond),
                    Codec.STRING.optionalFieldOf("xpSource").forGetter(BondProfile::xpSource),
                    Identifier.CODEC.optionalFieldOf("feedSound").forGetter(BondProfile::feedSound),
                    Identifier.CODEC.optionalFieldOf("refuseSound").forGetter(BondProfile::refuseSound)
            ).apply(instance, BondProfile::new))
            .validate(BondProfile::validate);

    public BondProfile {
        maxBond = Math.max(1, maxBond);
        followThreshold = Math.max(0, followThreshold);
        proximityRange = Math.max(0.0, proximityRange);
        proximitySeconds = Math.max(0, proximitySeconds);
        proximityGain = Math.max(0, proximityGain);
        betrayalPenalty = Math.max(0, betrayalPenalty);
        feeds = List.copyOf(feeds);
        milestones = milestones.stream().sorted().distinct().toList();
    }

    /**
     * Refused at load rather than discovered in play.
     *
     * <p>A profile with no feeds and no proximity gain is a creature that can never be bonded to at
     * all — every threshold in it is unreachable, so the file reads as configured content while
     * behaving exactly like no file. A follow threshold above the ceiling is the same failure one
     * level down: the creature bonds, and then never does the one thing the bond was for.
     */
    private static DataResult<BondProfile> validate(BondProfile profile) {
        boolean canGainAtAll = !profile.feeds.isEmpty()
                || (profile.proximityGain > 0 && profile.proximityRange > 0.0);
        if (!canGainAtAll) {
            return DataResult.error(() -> "no feeds and no proximity gain: bond could never rise above 0");
        }
        if (profile.followThreshold > profile.maxBond) {
            return DataResult.error(() -> "followThreshold " + profile.followThreshold
                    + " is above maxBond " + profile.maxBond + ", so it would never follow");
        }
        for (int milestone : profile.milestones) {
            if (milestone > profile.maxBond) {
                return DataResult.error(() -> "milestone " + milestone + " is above maxBond "
                        + profile.maxBond + " and can never be crossed");
            }
        }
        BondGift gift = profile.gift.orElse(null);
        if (gift != null && gift.minBond() > profile.maxBond) {
            return DataResult.error(() -> "gift minBond " + gift.minBond() + " is above maxBond "
                    + profile.maxBond + " and would never be given");
        }
        BondBreeding breeding = profile.breeding.orElse(null);
        if (breeding != null && breeding.minBond() > profile.maxBond) {
            return DataResult.error(() -> "breeding minBond " + breeding.minBond() + " is above maxBond "
                    + profile.maxBond + ", so nothing could ever breed");
        }
        return DataResult.success(profile);
    }

    /** The feed entry matching {@code stack}, or {@code null} when this species will not eat it. */
    @Nullable
    public BondFeed feedFor(ItemStack stack) {
        for (BondFeed feed : feeds) {
            if (feed.matches(stack)) {
                return feed;
            }
        }
        return null;
    }

    /** Milestone XP tag for {@code level}, e.g. {@code bowtruckle_bond_50}. */
    public String xpTag(Identifier species, int level) {
        return xpSource.orElseGet(() -> species.getPath() + "_bond") + "_" + level;
    }
}
