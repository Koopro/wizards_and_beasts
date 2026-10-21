package at.koopro.wizardsandbeasts.client.travel;

import at.koopro.wizardsandbeasts.travel.profile.TravelProfile;
import org.jspecify.annotations.NullMarked;

/** The last {@link TravelProfile} the server sent this client about its own player. */
@NullMarked
public final class ClientTravelProfileState {

    private static TravelProfile profile = TravelProfile.EMPTY;
    private static boolean received;

    private ClientTravelProfileState() {}

    public static void accept(TravelProfile next) {
        profile = next;
        received = true;
    }

    public static TravelProfile get() {
        return profile;
    }

    /** Whether any profile has arrived since the client joined. */
    public static boolean received() {
        return received;
    }

    public static void clear() {
        profile = TravelProfile.EMPTY;
        received = false;
    }
}
