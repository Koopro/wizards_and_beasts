package at.koopro.wizardsandbeasts.client.pose;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Client lifecycle for the pose layer: pass registration, the tick clock, and teardown.
 *
 * <p>Registration happens at client setup rather than in a static initialiser so the order the
 * passes go in is one visible list in one place. Priority decides who wins a contested part, so the
 * registration order does not matter — but the list is the only enumeration of what can pose a
 * player, and it is worth being able to read it.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class PoseClientEvents {

    private PoseClientEvents() {}

    private static final FlightPosePass FLIGHT = new FlightPosePass();

    /**
     * Registered unconditionally, per the module contract: registration never branches on whether a
     * module is enabled. {@code PlayerPoseLayer.run} does the gating, so a disabled module means the
     * passes are present and never consulted.
     */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> PlayerPoseLayer.get().register(FLIGHT));
    }

    /**
     * The one tick clock for every procedural pass.
     *
     * <p>{@code Post} rather than {@code Pre} so the timers advance after the player's own tick has
     * settled this tick's flying flag and key state. On {@code Pre} the pass would ramp against the
     * previous tick's input, which is a one-tick lag on every transition — small, but it is free to
     * not have it.
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        FLIGHT.tick();
    }

    /**
     * Drops the synced overrides on disconnect.
     *
     * <p>{@link ClientPoseState} is keyed by UUID with no world scoping, so without this a player
     * left flying in one world would still be marked as flying after joining another — and UUIDs are
     * stable across worlds, so it would land on the same player.
     */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientPoseState.clear();
    }

    /** The live flight pass, for the debug readout. */
    public static FlightPosePass flightPass() {
        return FLIGHT;
    }
}
