package at.koopro.wizardsandbeasts.location;

import at.koopro.wizardsandbeasts.owl.career.track.PlayerCareerProgress.TrackRecord;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.Map;

/**
 * A player's standing at every location they have dealt with, keyed by location id. Each is a career-track
 * {@link TrackRecord}: reputation is its experience, the tier its rank, and its contracts the location's board.
 * Server-side attachment with {@code copyOnDeath}; the client sees only the view the server builds.
 */
@NullMarked
public record PlayerLocationStanding(Map<String, TrackRecord> places) {

    public static final Codec<PlayerLocationStanding> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, TrackRecord.CODEC).optionalFieldOf("places", Map.of())
                    .forGetter(PlayerLocationStanding::places)
    ).apply(i, PlayerLocationStanding::new));

    public static final PlayerLocationStanding EMPTY = new PlayerLocationStanding(Map.of());

    public PlayerLocationStanding {
        places = Map.copyOf(places);
    }

    public TrackRecord at(String location) {
        return places.getOrDefault(location, TrackRecord.FRESH);
    }

    public PlayerLocationStanding with(String location, TrackRecord record) {
        Map<String, TrackRecord> next = new HashMap<>(places);
        next.put(location, record);
        return new PlayerLocationStanding(next);
    }
}
