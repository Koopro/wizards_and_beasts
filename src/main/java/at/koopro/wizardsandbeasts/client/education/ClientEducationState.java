package at.koopro.wizardsandbeasts.client.education;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.education.EducationView;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.jspecify.annotations.NullMarked;

/**
 * The last education view the server sent. A readout only: every grade, threshold and question in it was decided by
 * the server ({@code EducationService#view}); the tab and the desk send intents back, never results.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class ClientEducationState {

    private static volatile EducationView view = EducationView.NONE;
    private static volatile long version;

    private ClientEducationState() {}

    public static EducationView get() {
        return view;
    }

    /** Bumps on every update, so a screen can tell a fresh view from the one it already laid out. */
    public static long version() {
        return version;
    }

    public static void set(EducationView next) {
        view = next;
        version++;
    }

    /** On disconnect: another server's sitting must not tick on the next one's HUD. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        set(EducationView.NONE);
    }
}
