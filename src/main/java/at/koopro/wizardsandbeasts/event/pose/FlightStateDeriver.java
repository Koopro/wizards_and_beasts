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
     * The smoothed horizontal speed, per player.
     *
     * <p>Smoothing is not polish here, it is the difference between working and not. A player's
     * movement reaches the server in packets rather than from simulation, so the raw per-tick figure
     * is spiky: a tick that receives no movement packet reads as stationary and the next one reads as
     * double speed. Classifying that directly made the state flip between HOVER and PROPELLED several
     * times a second, and since every flip restarts the client's cross-fade, the pose visibly
     * glitched the moment the player started moving.
     */
    private static final PlayerScopedState<Double> SMOOTHED_SPEED =
            PlayerScopedState.create("pose_smoothed_speed");

    /**
     * How much of each tick's reading to believe.
     *
     * <p>Low enough to swallow a dropped packet, high enough that the pose still answers the controls
     * — roughly a three-tick time constant, well inside the six-tick cross-fade that follows it.
     */
    private static final double SMOOTHING = 0.3;

    /** Blocks per tick above which the player is travelling rather than holding station. */
    private static final double GLIDE_SPEED = 0.12;

    /** Blocks per tick above which the player is under real propulsion. */
    private static final double PROPELLED_SPEED = 0.55;

    /**
     * Fraction of a threshold a player must fall back through before the state drops.
     *
     * <p>Smoothing removes the packet jitter; this removes the rest. Without it a player holding a
     * steady speed that happens to sit on a threshold still flips every tick, and each flip is a
     * packet to every tracking client.
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

        if (!player.getAbilities().flying) {
            SMOOTHED_SPEED.remove(player.getUUID());
            PoseOverrideService.derive(player, null);
            return;
        }

        // getKnownMovement is the movement the client last reported, which is the question being
        // asked. getDeltaMovement is not: for a player-controlled entity the server's copy is
        // whatever the last packet left there, and for creative flight it is frequently stale.
        Vec3 movement = player.getKnownMovement();

        // Horizontal only. A player dropping straight down at terminal velocity is not propelled,
        // and including Y would classify every dive as the fastest state there is.
        double raw = Math.sqrt(movement.x * movement.x + movement.z * movement.z);
        double previous = SMOOTHED_SPEED.getOrDefault(player.getUUID(), raw);
        double speed = previous + (raw - previous) * SMOOTHING;
        SMOOTHED_SPEED.put(player.getUUID(), speed);

        PoseOverrideService.derive(player, classify(speed, current(player)));
    }

    /**
     * Speed to state, with the drop thresholds pulled below the rise thresholds.
     *
     * @param currentState the state already in force, or null when there is none
     */
    static FlightPoseState classify(double speed, @Nullable FlightPoseState currentState) {
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

    /** Exposed for the test: one smoothing step, so the filter can be driven over a tick series. */
    static double smooth(double previous, double raw) {
        return previous + (raw - previous) * SMOOTHING;
    }

    private static @Nullable FlightPoseState current(ServerPlayer player) {
        return PoseOverrideService.get(player).state().orElse(null);
    }
}
