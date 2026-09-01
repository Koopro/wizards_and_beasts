package at.koopro.wizardsandbeasts.client.map;

import at.koopro.wizardsandbeasts.map.TrackedEntityEntry;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The footprints trailing behind every moving dot.
 *
 * <p>This is the map's signature, and it costs nothing: the positions are already arriving for the
 * dots, so a trail is a short client-side history of packets the screen has already received. No
 * extra sweep, no extra packet, no server state.
 *
 * <p>Purely cosmetic and purely client-side. A trail says nothing the dot at its head does not
 * already say — it fades out well inside the sense radius — so it adds no information the map was
 * not already allowed to show. What it adds is the sense that the ink is watching.
 */
public final class MapTrails {

    /** Footprints kept per subject. Six is about two seconds of sweep at the usual interval. */
    private static final int MAX_POINTS = 6;

    /** Below this a step is a shuffle, not a walk, and stamping it just thickens the dot. */
    private static final double MIN_STEP_BLOCKS = 1.5;

    /** Milliseconds a footprint takes to fade from full ink to nothing. */
    public static final long FADE_MILLIS = 2600L;

    /** A stamped step: where, and when the ink was laid. */
    public record Step(double x, double z, long stampedAt) {
    }

    private static final Map<UUID, Deque<Step>> TRAILS = new HashMap<>();

    private MapTrails() {
    }

    /**
     * Folds one sweep into the trails.
     *
     * <p>Subjects absent from the sweep are not dropped here — they are left to age out. A creature
     * that walks out of the sense radius should leave its last few footprints behind it rather than
     * having its whole trail vanish the instant it crosses the line, which is both nicer and closer
     * to what ink does.
     */
    public static void record(List<TrackedEntityEntry> entries) {
        long now = System.currentTimeMillis();
        for (TrackedEntityEntry entry : entries) {
            Deque<Step> trail = TRAILS.computeIfAbsent(entry.uuid(), k -> new ArrayDeque<>(MAX_POINTS));
            Step head = trail.peekLast();
            if (head != null) {
                double dx = entry.x() - head.x();
                double dz = entry.z() - head.z();
                if (dx * dx + dz * dz < MIN_STEP_BLOCKS * MIN_STEP_BLOCKS) {
                    continue;
                }
            }
            trail.addLast(new Step(entry.x(), entry.z(), now));
            while (trail.size() > MAX_POINTS) {
                trail.removeFirst();
            }
        }
        prune(now);
    }

    private static void prune(long now) {
        Iterator<Map.Entry<UUID, Deque<Step>>> it = TRAILS.entrySet().iterator();
        while (it.hasNext()) {
            Deque<Step> trail = it.next().getValue();
            trail.removeIf(step -> now - step.stampedAt() > FADE_MILLIS);
            if (trail.isEmpty()) {
                it.remove();
            }
        }
    }

    public static Iterable<Step> of(UUID uuid) {
        Deque<Step> trail = TRAILS.get(uuid);
        return trail != null ? trail : List.of();
    }

    /** Remaining ink for a step, 0..1. */
    public static float alpha(Step step, long now) {
        float remaining = 1.0F - (now - step.stampedAt()) / (float) FADE_MILLIS;
        return Math.clamp(remaining, 0.0F, 1.0F);
    }

    public static void clear() {
        TRAILS.clear();
    }
}
