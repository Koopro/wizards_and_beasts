package at.koopro.wizardsandbeasts.spell.learning.tracing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.Map;

/**
 * What the server remembers about a player's tracing between sessions: when each recently failed spell may be tried
 * again, and whether they trace with the "Steady hand" assist.
 *
 * <p>Saved rather than held in memory (2026-10-23): a retry wait that a relog wiped was no wait at all, and the
 * assist was a flag each submission claimed for itself. Now the wait survives a logout and a death, and the assist is
 * a setting the server holds and the client is told.
 *
 * @param retryAt spell id -> game time from which it may be attempted again; only recent failures
 */
@NullMarked
public record TraceMemory(Map<String, Long> retryAt, boolean steadyHand) {

    public static final TraceMemory EMPTY = new TraceMemory(Map.of(), false);

    public static final Codec<TraceMemory> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("retry_at", Map.of())
                    .forGetter(TraceMemory::retryAt),
            Codec.BOOL.optionalFieldOf("steady_hand", false).forGetter(TraceMemory::steadyHand)
    ).apply(i, TraceMemory::new));

    public TraceMemory {
        retryAt = Map.copyOf(retryAt);
    }

    /** Ticks until {@code spellId} may be attempted again; 0 when it may be now. Pure. */
    public long waitFor(String spellId, long now) {
        Long at = retryAt.get(spellId);
        return at == null || at <= now ? 0 : at - now;
    }

    /** With {@code spellId} locked until {@code until}, and every expired entry dropped. Pure. */
    public TraceMemory withRetry(String spellId, long until, long now) {
        Map<String, Long> next = new HashMap<>();
        retryAt.forEach((id, at) -> {
            if (at > now) {
                next.put(id, at);
            }
        });
        next.put(spellId, until);
        return new TraceMemory(next, steadyHand);
    }

    public TraceMemory withSteadyHand(boolean steady) {
        return new TraceMemory(retryAt, steady);
    }
}
