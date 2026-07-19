package at.koopro.wizardsandbeasts.client.apparition.state;

import at.koopro.wizardsandbeasts.apparition.ApparitionPoint;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Client mirror of the player's memorised Apparition destinations, plus the hook the server uses to open
 * the selector. Class-init-safe (only common-typed static state); the screen is only touched inside methods
 * that already run on the client thread.
 */
@NullMarked
public final class ClientApparitionPointsState {

    private static volatile List<ApparitionPoint> points = List.of();

    private ClientApparitionPointsState() {}

    public static void accept(List<ApparitionPoint> synced) {
        points = List.copyOf(synced);
    }

    public static List<ApparitionPoint> points() {
        return points;
    }

    public static void clear() {
        points = List.of();
    }

    /** Opens the destination selector. Called from the S2C payload handler on the client thread. */
    public static void openSelector() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.setScreen(new at.koopro.wizardsandbeasts.client.apparition.gui.ApparitionSelectorScreen());
        }
    }
}
