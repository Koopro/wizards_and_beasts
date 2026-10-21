package at.koopro.wizardsandbeasts.client.location;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.location.PlaceStanding;
import at.koopro.wizardsandbeasts.network.location.PlaceStandingsS2CPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * The owner's standing at each place as the server last sent it ({@link PlaceStandingsS2CPayload}). A readout for
 * the Character Sheet; nothing on the client decides a tier from it.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class ClientPlaceStandingState {

    private static volatile List<PlaceStanding> places = List.of();
    private static volatile boolean received;

    private ClientPlaceStandingState() {}

    public static List<PlaceStanding> get() {
        return places;
    }

    /** Whether any answer has arrived since the client joined: "no places" and "not asked yet" read differently. */
    public static boolean received() {
        return received;
    }

    /** Applied on the client thread by {@code ClientPayloadHandlers.handlePlaceStandings}. */
    public static void set(PlaceStandingsS2CPayload payload) {
        places = List.copyOf(payload.places());
        received = true;
    }

    /** On disconnect: another server's standing must not show on the next one's sheet. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        places = List.of();
        received = false;
    }
}
