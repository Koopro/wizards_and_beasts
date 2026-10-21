package at.koopro.wizardsandbeasts.client.companion;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.creature.companion.CompanionRoster;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.jspecify.annotations.NullMarked;

/** The owner's roster as the server last sent it. Read by the Beasts tab; never written by the client. */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class ClientCompanionState {

    private static volatile CompanionRoster roster = CompanionRoster.EMPTY;

    private ClientCompanionState() {}

    public static CompanionRoster get() {
        return roster;
    }

    public static void set(CompanionRoster next) {
        roster = next;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        roster = CompanionRoster.EMPTY;
    }
}
