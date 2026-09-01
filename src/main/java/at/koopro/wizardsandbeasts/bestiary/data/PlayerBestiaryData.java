package at.koopro.wizardsandbeasts.bestiary.data;

import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * What one player has learned about the world's creatures, and when they last took a rare material
 * from each.
 *
 * <p>The two live together because they are the same subject and the same lifetime: a harvest lockout
 * is meaningless without the tier that earned it, and both must survive death and a relog. Adding a
 * second attachment would have meant a second thing to sync, a second thing to copy on death, and a
 * second chance for the two to disagree.
 *
 * @param tiers        entry id → how much of it the player has earned
 * @param lastHarvests entry id → game time of the last successful rare harvest. Absent means never.
 *                     Deliberately persisted rather than held in memory: a lockout a player could clear
 *                     by relogging is not a lockout.
 */
public record PlayerBestiaryData(Map<Identifier, DiscoveryTier> tiers,
                                 Map<Identifier, Long> lastHarvests) {

    public static final Codec<PlayerBestiaryData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Identifier.CODEC, DiscoveryTier.CODEC)
                    .optionalFieldOf("tiers", Map.of())
                    .forGetter(PlayerBestiaryData::tiers),
            // Optional so a save written before harvest existed loads with its discoveries intact. A
            // required field would fail the parse and reset the whole bestiary to empty.
            Codec.unboundedMap(Identifier.CODEC, Codec.LONG)
                    .optionalFieldOf("lastHarvests", Map.of())
                    .forGetter(PlayerBestiaryData::lastHarvests)
    ).apply(instance, (tiers, harvests) ->
            new PlayerBestiaryData(new HashMap<>(tiers), new HashMap<>(harvests))));

    /** An empty record, for the attachment default. */
    public PlayerBestiaryData() {
        this(new HashMap<>(), new HashMap<>());
    }

    /**
     * Kept for the callers that only care about discoveries — the mutable-map constructor the tier
     * helper has always used.
     */
    public PlayerBestiaryData(Map<Identifier, DiscoveryTier> tiers) {
        this(tiers, new HashMap<>());
    }
}
