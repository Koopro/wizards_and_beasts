package at.koopro.wizardsandbeasts.spell.tuning;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.PayloadBroadcast;
import at.koopro.wizardsandbeasts.network.spell.SpellTuningSyncS2CPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jspecify.annotations.NullMarked;

import java.util.function.UnaryOperator;

/**
 * The server side of spell administration state: the only writer of {@link SpellTuningData}, and the one
 * place that publishes it into {@link SpellTuning} and tells every client.
 *
 * <p>Nothing here decides whether a change is allowed. The admin layer's {@code AdminSettingService} has
 * already authorised, parsed and validated a value before its binding calls {@link #update}; this class only
 * stores, publishes and syncs. Server thread only.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class SpellTuningService {

    private SpellTuningService() {}

    /** Applies {@code change} to one spell's override, then publishes and broadcasts the new state. */
    public static void update(MinecraftServer server, String spellId, UnaryOperator<SpellOverride> change) {
        SpellTuningData data = SpellTuningData.get(server);
        data.put(spellId, change.apply(data.override(spellId)));
        publish(server);
    }

    /** Re-reads world data into the live snapshot and sends it to every connected client. */
    public static void publish(MinecraftServer server) {
        SpellTuning.publishLocal(SpellTuning.local().withOverrides(SpellTuningData.get(server).overrides()));
        PayloadBroadcast.toAll(server, new SpellTuningSyncS2CPayload(SpellTuning.local()));
    }

    /**
     * The config-owned globals changed ({@code Config.onLoad}). Published locally at once; broadcast on the
     * server thread, because a config reload can arrive from the file watcher's thread.
     */
    public static void onGlobalsChanged(SpellTuningSnapshot.Globals globals) {
        SpellTuning.publishLocalGlobals(globals);
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && server.isRunning()) {
            server.execute(() -> PayloadBroadcast.toAll(server, new SpellTuningSyncS2CPayload(SpellTuning.local())));
        }
    }

    public static void syncTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new SpellTuningSyncS2CPayload(SpellTuning.local()));
    }

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        SpellTuning.publishLocal(SpellTuning.local().withOverrides(SpellTuningData.get(event.getServer()).overrides()));
    }

    /** A world closing takes its overrides with it; the next world loads its own. */
    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        SpellTuning.clearLocalOverrides();
    }

    @SubscribeEvent
    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncTo(player);
        }
    }
}
