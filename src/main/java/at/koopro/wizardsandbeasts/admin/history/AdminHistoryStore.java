package at.koopro.wizardsandbeasts.admin.history;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.AdminSettings;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Ties the live history to the world: on start, the stored records are loaded and every new record or undo is written
 * through to {@link AdminHistoryData}; on stop the history is emptied, so one world's audit trail never shows up in the
 * next world opened in the same session.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class AdminHistoryStore {

    private AdminHistoryStore() {}

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        attach(event.getServer());
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        if (AdminSettings.service().history() instanceof InMemoryChangeHistory history) {
            history.setListener(null);
            history.clear();
            history.restore(java.util.List.of(), 1);
        }
    }

    public static void attach(MinecraftServer server) {
        if (!(AdminSettings.service().history() instanceof InMemoryChangeHistory history)) {
            return;
        }
        AdminHistoryData data = AdminHistoryData.get(server);
        history.setListener(null);
        history.restore(data.records(), data.next());
        history.setListener(new InMemoryChangeHistory.Listener() {
            @Override
            public void recorded(AdminChangeRecord record) {
                data.append(record);
            }

            @Override
            public void undone(long sequence) {
                data.markUndone(sequence);
            }
        });
    }
}
