package at.koopro.wizardsandbeasts.pose;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

/**
 * A player's active pose override. Server-authoritative, synced to everyone tracking them.
 *
 * <p>Client-local would have been simpler and is wrong: third-person and multiplayer verification
 * both require that <em>other</em> players see the pose, and a pose only the posed player can see is
 * not a pose system, it is a screensaver.
 *
 * <p>This is the field the broom system will eventually write, rather than throwaway scaffolding for
 * the command. The command is wave 1's only writer; the broom becomes a second one later.
 *
 * @param state  the forced or derived flight state, empty when nothing is overriding
 * @param manual true when a command forced this state, false when it was derived from movement.
 *               The distinction matters because {@code auto} must be free to re-derive every tick
 *               while a forced state must survive until it is explicitly cleared.
 */
@NullMarked
public record PoseOverride(Optional<FlightPoseState> state, boolean manual) {

    /** No override. The value a player has until something writes one. */
    public static final PoseOverride NONE = new PoseOverride(Optional.empty(), false);

    public static final Codec<PoseOverride> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            FlightPoseState.CODEC.optionalFieldOf("state").forGetter(PoseOverride::state),
            Codec.BOOL.optionalFieldOf("manual", false).forGetter(PoseOverride::manual)
    ).apply(instance, PoseOverride::new));

    public static PoseOverride forced(FlightPoseState state) {
        return new PoseOverride(Optional.of(state), true);
    }

    public static PoseOverride derived(FlightPoseState state) {
        return new PoseOverride(Optional.of(state), false);
    }

    public boolean active() {
        return state.isPresent();
    }
}
