package at.koopro.wizardsandbeasts.client.heritage.state;

import at.koopro.wizardsandbeasts.heritage.identity.HeritageProfile;
import org.jspecify.annotations.NullMarked;

/** The last {@link HeritageProfile} the server sent this client about its own player. */
@NullMarked
public final class ClientHeritageProfileState {

    private static HeritageProfile profile = HeritageProfile.EMPTY;
    private static long receivedAt = -1L;

    private ClientHeritageProfileState() {}

    public static void accept(HeritageProfile next, long gameTime) {
        profile = next;
        receivedAt = gameTime;
    }

    public static HeritageProfile get() {
        return profile;
    }

    /** Whether any profile has arrived since the client joined. */
    public static boolean received() {
        return receivedAt >= 0L;
    }

    public static void clear() {
        profile = HeritageProfile.EMPTY;
        receivedAt = -1L;
    }
}
