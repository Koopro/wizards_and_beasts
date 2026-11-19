package at.koopro.wizardsandbeasts.client.pose;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/**
 * Per-entity crossfade between the discrete poses one pass can show — a book, a drink, a ready wand, nothing.
 *
 * <p>Built for passes whose pose is <em>derived</em> from the render state every frame (a held item, a use flag)
 * rather than played from a synced start tick. Such a pass knows only what to show now, so every change of mind was
 * a one-frame cut. This remembers the last kind per entity and the time it changed, and answers two shares: the
 * incoming kind rising and the outgoing one falling, so the pass can queue both (see
 * {@link PoseBuilder#opWeight(float)}).
 *
 * <h2>Driven by observation, not by a tick</h2>
 *
 * <p>{@link #observe} is called from the render pass. That is safe because it is idempotent for an unchanged kind,
 * and the layer runs every pass twice per frame (body half, limb half) with the same render state. Weights are a
 * pure function of the stamped change time and the clock, so the two runs agree. Keyed by entity id, never held in
 * fields, per the pose-layer rule that a singleton pass carries nothing per player.
 *
 * <p>The clock is client tick plus partial tick, as a double: a float tick count loses sub-tick precision after
 * roughly nine in-game days, and a world's game time is older than that.
 *
 * @param <K> the pass's kind enum
 */
@NullMarked
public final class KindFade<K> {

    /**
     * One frame's answer.
     *
     * @param current  the kind showing, or null
     * @param weight   its rising share
     * @param previous the kind letting go, or null
     * @param fall     its falling share
     */
    public record Shares<K>(@Nullable K current, float weight, @Nullable K previous, float fall) {

        public boolean idle() {
            return (current == null || weight <= 0f) && (previous == null || fall <= 0f);
        }
    }

    private static final class Track<K> {
        @Nullable K current;
        @Nullable K previous;
        double changeTime;
        /** The share the incoming kind starts from — nonzero when it comes back before it had fully let go. */
        float currentFrom;
        /** The share the outgoing kind had when it was replaced. */
        float previousFrom;
        double lastSeen;
    }

    private final int riseTicks;
    private final int fallTicks;
    private final Map<Integer, Track<K>> tracks = new HashMap<>();

    public KindFade(int riseTicks, int fallTicks) {
        if (riseTicks <= 0 || fallTicks <= 0) {
            throw new IllegalArgumentException("KindFade needs positive durations");
        }
        this.riseTicks = riseTicks;
        this.fallTicks = fallTicks;
    }

    /** Records what the entity shows now and answers the two shares. */
    public Shares<K> observe(int entityId, @Nullable K kind, double time) {
        Track<K> track = tracks.get(entityId);
        if (track == null) {
            if (kind == null) {
                return new Shares<>(null, 0f, null, 0f);
            }
            track = new Track<>();
            track.current = kind;
            track.changeTime = time;
            tracks.put(entityId, track);
        } else if (!Objects.equals(track.current, kind)) {
            float shownCurrent = rise(track, time);
            float shownPrevious = fall(track, time);
            K outgoing = track.current;
            // Coming straight back to what was letting go resumes from its share rather than from zero.
            float resume = Objects.equals(kind, track.previous) ? shownPrevious : 0f;
            track.previous = outgoing;
            track.previousFrom = outgoing == null ? 0f : shownCurrent;
            track.current = kind;
            track.currentFrom = resume;
            track.changeTime = time;
        }
        track.lastSeen = time;
        return new Shares<>(track.current, track.current == null ? 0f : rise(track, time),
                track.previous, track.previous == null ? 0f : fall(track, time));
    }

    /** Drops entities nobody has rendered for a while, and finished let-gos. Call from the client tick. */
    public void sweep(double time) {
        Iterator<Map.Entry<Integer, Track<K>>> it = tracks.entrySet().iterator();
        while (it.hasNext()) {
            Track<K> track = it.next().getValue();
            boolean unseen = time - track.lastSeen > 100.0;
            boolean done = track.current == null && time - track.changeTime >= fallTicks;
            if (unseen || done) {
                it.remove();
            }
        }
    }

    public void clear() {
        tracks.clear();
    }

    int size() {
        return tracks.size();
    }

    private float rise(Track<K> track, double time) {
        float t = ClientCastAnimationState.smoothstep((float) ((time - track.changeTime) / riseTicks));
        return track.currentFrom + (1f - track.currentFrom) * t;
    }

    private float fall(Track<K> track, double time) {
        float t = ClientCastAnimationState.smoothstep((float) ((time - track.changeTime) / fallTicks));
        return track.previousFrom * (1f - t);
    }
}
