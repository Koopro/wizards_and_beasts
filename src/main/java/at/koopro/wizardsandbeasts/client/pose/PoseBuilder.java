package at.koopro.wizardsandbeasts.client.pose;

import org.jspecify.annotations.NullMarked;

import java.util.EnumMap;
import java.util.Map;

/** Collects one pass's intentions. Parts are created lazily, so an uninterested pass costs nothing. */
@NullMarked
public final class PoseBuilder {

    private final Map<PlayerModelPart, PartPoseData> parts = new EnumMap<>(PlayerModelPart.class);
    private float opWeight = 1.0f;

    public PartPoseData get(PlayerModelPart part) {
        return parts.computeIfAbsent(part, p -> new PartPoseData(this));
    }

    /**
     * Weight stamped on every op queued from here on, until changed or cleared.
     *
     * <p>For a pass that crossfades two of its own poses: it queues the outgoing one at its falling share, then the
     * incoming one at its rising share, and the layer applies them in that order — each lerping from where the last
     * left the part. A part multiplier cannot do this, because it is one number for every op on the part.
     */
    public PoseBuilder opWeight(float weight) {
        this.opWeight = Math.max(0.0f, Math.min(1.0f, weight));
        return this;
    }

    public float opWeight() {
        return opWeight;
    }

    public Map<PlayerModelPart, PartPoseData> parts() {
        return parts;
    }

    public boolean isEmpty() {
        return parts.isEmpty();
    }

    public void clear() {
        parts.clear();
        opWeight = 1.0f;
    }
}
