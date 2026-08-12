package at.koopro.wizardsandbeasts.pose;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;

/**
 * The three flight attitudes wave 1 poses.
 *
 * <p>Lives in the common package, not {@code client.pose}: the server owns which state a player is
 * in and syncs it, and the client only owns what that looks like.
 *
 * <p>Roll and barrel-roll are deliberately absent — out of scope for wave 1.
 */
@NullMarked
public enum FlightPoseState implements StringRepresentable {
    /** Airborne, negligible horizontal input. */
    HOVER("hover"),
    /** Steady directional movement, body tilted into travel. */
    GLIDE("glide"),
    /** High-speed forward attitude, body near-horizontal. */
    PROPELLED("propelled");

    public static final Codec<FlightPoseState> CODEC = StringRepresentable.fromEnum(FlightPoseState::values);

    private final String id;

    FlightPoseState(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
