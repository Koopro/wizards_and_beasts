package at.koopro.wizardsandbeasts.event.pose;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.pose.FlightPoseState;
import at.koopro.wizardsandbeasts.pose.PoseOverrideService;
import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Derives a flight state from how a player is actually moving, for {@code /wandb pose flight auto}.
 *
 * <p>Server-side, so every observer agrees on the state — a client-local derivation would give each
 * viewer a slightly different answer for the same player, and near a threshold two clients would
 * disagree about which pose to draw.
 *
 * <p>Wave 1 covers creative flight only. That is the only flight the mod owns end to end today; the
 * broom writes its own state directly through {@link PoseOverrideService#force} and elytra flight is
 * out of scope until there is a ruling on whether it should be posed at all.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class FlightStateDeriver {

    private FlightStateDeriver() {}

    /**
     * Measured from position, not {@code getDeltaMovement}.
     *
     * <p>A player-controlled entity's delta on the server is whatever the last movement packet
     * happened to leave there, and for creative flight that is frequently stale. The distance the
     * player actually covered between two ticks is the thing being asked about anyway.
     */
    private static final PlayerScopedState<Vec3> LAST_POSITION =
            PlayerScopedState.create("pose_last_position");

    /** Blocks per tick above which the player is travelling rather than holding station. */
    private static final double GLIDE_SPEED = 0.12;

    /** Blocks per tick above which the player is under real propulsion. */
    private static final double PROPELLED_SPEED = 0.55;

    /**
     * Fraction of a threshold a player must fall back through before the state drops.
     *
     * <p>Without this the two states flap every tick for anyone flying at exactly the threshold, and
     * because each flip is a state change it is also a packet to every tracking client.
     */
    private static final double HYSTERESIS = 0.75;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!ModuleManager.isEnabled(Module.PLAYER_ANIMATION)) {
            return;
        }

        Vec3 position = player.position();
        Vec3 previous = LAST_POSITION.getOrDefault(player.getUUID(), position);
        LAST_POSITION.put(player.getUUID(), position);

        if (!player.getAbilities().flying) {
            PoseOverrideService.derive(player, null);
            return;
        }

        // Horizontal only. A player dropping straight down at terminal velocity is not propelled,
        // and including Y would classify every dive as the fastest state there is.
        double speed = Math.sqrt(sq(position.x - previous.x) + sq(position.z - previous.z));
        PoseOverrideService.derive(player, classify(speed, current(player)));
    }

    /**
     * Speed to state, with the drop thresholds pulled below the rise thresholds.
     *
     * @param currentState the state already in force, or null when there is none
     */
    private static FlightPoseState classify(double speed, @Nullable FlightPoseState currentState) {
        double glideFloor = currentState == FlightPoseState.HOVER || currentState == null
                ? GLIDE_SPEED : GLIDE_SPEED * HYSTERESIS;
        double propelledFloor = currentState == FlightPoseState.PROPELLED
                ? PROPELLED_SPEED * HYSTERESIS : PROPELLED_SPEED;

        if (speed >= propelledFloor) {
            return FlightPoseState.PROPELLED;
        }
        if (speed >= glideFloor) {
            return FlightPoseState.GLIDE;
        }
        return FlightPoseState.HOVER;
    }

    private static @Nullable FlightPoseState current(ServerPlayer player) {
        return PoseOverrideService.get(player).state().orElse(null);
    }

    private static double sq(double value) {
        return value * value;
    }
}
