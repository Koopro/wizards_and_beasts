package at.koopro.wizardsandbeasts.client.pose;

import org.jspecify.annotations.NullMarked;

import java.util.EnumMap;
import java.util.Map;

/** Collects one pass's intentions. Parts are created lazily, so an uninterested pass costs nothing. */
@NullMarked
public final class PoseBuilder {

    private final Map<PlayerModelPart, PartPoseData> parts = new EnumMap<>(PlayerModelPart.class);

    public PartPoseData get(PlayerModelPart part) {
        return parts.computeIfAbsent(part, p -> new PartPoseData());
    }

    public Map<PlayerModelPart, PartPoseData> parts() {
        return parts;
    }

    public boolean isEmpty() {
        return parts.isEmpty();
    }

    public void clear() {
        parts.clear();
    }
}
