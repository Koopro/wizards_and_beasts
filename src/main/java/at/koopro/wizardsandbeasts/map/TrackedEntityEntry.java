package at.koopro.wizardsandbeasts.map;

import java.util.UUID;

/**
 * One moving dot.
 *
 * <p>{@code y} is here so the screen can tell a dot on the surface from one three storeys up or
 * down a mineshaft. The map is flat and always will be, but drawing every dot at full strength
 * regardless of depth turns a castle with cellars into an unreadable smear — depth is faded, not
 * hidden, because a name moving under your feet is exactly what this artefact is for.
 */
public record TrackedEntityEntry(
        UUID uuid,
        double x,
        double y,
        double z,
        float yaw,
        String displayName,
        byte category
) {
    public static final byte PLAYER = 0;
    public static final byte HOSTILE = 1;
    public static final byte PASSIVE = 2;
}
