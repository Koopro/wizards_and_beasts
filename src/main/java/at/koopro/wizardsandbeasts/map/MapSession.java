package at.koopro.wizardsandbeasts.map;

import net.minecraft.resources.Identifier;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Collection;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

/**
 * One player's open Marauder's Map, for as long as the screen is open.
 *
 * <p>This is the fix for the map's central design bug. The old implementation bound the map to the
 * holder's position the first time they used it and never moved it again, so a map made at spawn
 * showed spawn forever — the further you explored, the more useless it became. The two things a map
 * shows have completely different scopes and were being given the same one:
 *
 * <ul>
 *   <li><b>Terrain and markers</b> are the atlas. They are unbounded: the player pans wherever they
 *       have been, and the session only tracks which regions have already been shipped so the same
 *       parchment is not sent twice.</li>
 *   <li><b>Moving dots</b> are a live sweep with a real radius, and that radius is centred on the
 *       <em>holder, right now</em>. The map senses what is near it. Recomputed every sweep.</li>
 * </ul>
 *
 * <p>Server-side only, mutated on the server thread.
 */
public final class MapSession {

    /** How far the map senses movement, in blocks. The dots, not the parchment. */
    public static final int SENSE_RADIUS = 128;

    private final UUID mapId;
    private Identifier dimension;
    /** Regions already shipped for the current dimension; cleared when the holder changes worlds. */
    private final Set<Long> sentRegions = new HashSet<>();
    private final Queue<Long> pendingRegions = new ArrayDeque<>();
    /**
     * Membership index for {@link #pendingRegions}.
     *
     * <p>A deque's {@code contains} is a linear scan, and this queue holds one entry per charted
     * region -- opening a map of a well-explored world queues hundreds at once, and every
     * subsequent queue call would then walk all of them. The set keeps the de-duplication O(1) on
     * exactly the input this feature is built to produce.
     */
    private final Set<Long> pendingIndex = new HashSet<>();
    private boolean markersDirty = true;
    /**
     * False at open: {@code MaraudersMapItem} sends the opening payload itself, palette included,
     * so a session that started dirty would have the streamer send a duplicate on the very next
     * tick. It goes true when the palette grows or the holder changes dimension.
     */
    private boolean paletteDirty;

    public MapSession(UUID mapId, Identifier dimension) {
        this.mapId = mapId;
        this.dimension = dimension;
    }

    public UUID mapId() {
        return mapId;
    }

    public Identifier dimension() {
        return dimension;
    }

    /**
     * Repoints the session at a new dimension, dropping everything shipped for the old one.
     *
     * <p>The old code removed the viewer outright on a dimension change, which closed the sweep and
     * left the screen open showing frozen Overworld dots while the player stood in the Nether. The
     * screen has to be told, not silently abandoned.
     *
     * @return true when the dimension actually changed
     */
    public boolean moveTo(Identifier newDimension) {
        if (dimension.equals(newDimension)) {
            return false;
        }
        dimension = newDimension;
        sentRegions.clear();
        pendingRegions.clear();
        pendingIndex.clear();
        markersDirty = true;
        paletteDirty = true;
        return true;
    }

    /** Queues a region for shipping unless it is already sent or already queued. */
    public void queueRegion(long key) {
        if (sentRegions.contains(key) || !pendingIndex.add(key)) {
            return;
        }
        pendingRegions.add(key);
    }

    public void queueRegions(Collection<Long> keys) {
        for (Long key : keys) {
            queueRegion(key);
        }
    }

    /**
     * Re-queues a region the surveyor has just changed.
     *
     * <p>Distinct from {@link #queueRegion}: this one clears the sent flag first, because the point
     * is precisely that the client's copy is now stale.
     */
    public void invalidateRegion(long key) {
        sentRegions.remove(key);
        if (pendingIndex.add(key)) {
            pendingRegions.add(key);
        }
    }

    /** Next region to ship, or {@code null} when the client is up to date. */
    public Long pollRegion() {
        Long key = pendingRegions.poll();
        if (key != null) {
            pendingIndex.remove(key);
            sentRegions.add(key);
        }
        return key;
    }

    public boolean hasPendingRegions() {
        return !pendingRegions.isEmpty();
    }

    public boolean markersDirty() {
        return markersDirty;
    }

    public void markMarkersDirty() {
        markersDirty = true;
    }

    public void clearMarkersDirty() {
        markersDirty = false;
    }

    public boolean paletteDirty() {
        return paletteDirty;
    }

    public void clearPaletteDirty() {
        paletteDirty = false;
    }

    /** A new biome entered the palette, so the client's copy no longer decodes every index. */
    public void markPaletteDirty() {
        paletteDirty = true;
    }
}
