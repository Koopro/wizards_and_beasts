package at.koopro.wizardsandbeasts.owl.career.track;

import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * One counted thing the player just did.
 *
 * @param type    what happened
 * @param species the creature it happened with, or {@code null} when none is involved
 * @param tier    for {@link ActivityType#BESTIARY_TIER}, the depth reached; {@code null} otherwise
 * @param rating  the creature's Ministry classification (1–5), or 0 when it has none or none applies
 */
@NullMarked
public record CareerActivity(ActivityType type, @Nullable Identifier species, @Nullable DiscoveryTier tier, int rating) {

    public static CareerActivity of(ActivityType type, @Nullable Identifier species, int rating) {
        return new CareerActivity(type, species, null, rating);
    }

    /**
     * The lifetime counter this activity adds to: {@code type|species|tier}, empty parts left blank.
     * Kept as one string so the save stays a flat map and a filter can be evaluated against it later.
     */
    public String counterKey() {
        return type.name() + "|" + (species == null ? "" : species) + "|" + (tier == null ? "" : tier.name());
    }
}
