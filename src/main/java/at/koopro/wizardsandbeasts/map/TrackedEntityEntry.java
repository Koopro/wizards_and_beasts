package at.koopro.wizardsandbeasts.map;

import java.util.UUID;

public record TrackedEntityEntry(
        UUID uuid,
        double x,
        double z,
        float yaw,
        String displayName,
        byte category
) {
    public static final byte PLAYER = 0;
    public static final byte HOSTILE = 1;
    public static final byte PASSIVE = 2;
}
