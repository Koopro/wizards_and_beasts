package at.koopro.wizardsandbeasts.owl.career.track;

import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Optional;

/**
 * Which activities count, written into a track file.
 *
 * <pre>{@code
 * { "activity": "bestiary_tier", "min_tier": "STUDIED", "min_rating": 4 }
 * { "activity": "creature_bred", "species": ["wizards_and_beasts:mooncalf"] }
 * }</pre>
 *
 * @param activity  the kind of activity
 * @param species   creatures it must involve; empty for any
 * @param minTier   for bestiary activity, the least depth that counts
 * @param minRating the least Ministry classification (1–5) that counts; 0 for any, including unclassified
 */
@NullMarked
public record ActivityFilter(ActivityType activity, List<Identifier> species, Optional<DiscoveryTier> minTier,
                             int minRating) {

    public static final MapCodec<ActivityFilter> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ActivityType.CODEC.fieldOf("activity").forGetter(ActivityFilter::activity),
            Identifier.CODEC.listOf().optionalFieldOf("species", List.of()).forGetter(ActivityFilter::species),
            DiscoveryTier.CODEC.optionalFieldOf("min_tier").forGetter(ActivityFilter::minTier),
            Codec.intRange(0, 5).optionalFieldOf("min_rating", 0).forGetter(ActivityFilter::minRating)
    ).apply(i, ActivityFilter::new));

    public static final Codec<ActivityFilter> CODEC = MAP_CODEC.codec();

    public ActivityFilter {
        species = List.copyOf(species);
    }

    public static ActivityFilter of(ActivityType activity) {
        return new ActivityFilter(activity, List.of(), Optional.empty(), 0);
    }

    public boolean matches(CareerActivity done) {
        if (done.type() != activity) {
            return false;
        }
        if (!species.isEmpty() && (done.species() == null || !species.contains(done.species()))) {
            return false;
        }
        if (minTier.isPresent() && (done.tier() == null || done.tier().ordinal() < minTier.get().ordinal())) {
            return false;
        }
        return minRating <= 0 || done.rating() >= minRating;
    }
}
