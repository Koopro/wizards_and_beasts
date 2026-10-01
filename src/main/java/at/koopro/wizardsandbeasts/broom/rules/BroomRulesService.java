package at.koopro.wizardsandbeasts.broom.rules;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.broom.BroomDefinitionsSyncS2CPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jspecify.annotations.NullMarked;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The server side of the broom rules: the only writer of {@link BroomRulesData}; republishes the effective definitions
 * and resends them through the existing broom sync. Server thread only.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class BroomRulesService {

    private BroomRulesService() {}

    public static void setStat(MinecraftServer server, String broom, BroomStat stat, Optional<Float> value) {
        BroomRulesData data = BroomRulesData.get(server);
        data.setStat(broom, stat, value);
        publish(server, data);
    }

    public static void setDisabled(MinecraftServer server, String broom, boolean disabled) {
        BroomRulesData data = BroomRulesData.get(server);
        data.setDisabled(broom, disabled);
        publish(server, data);
    }

    private static void publish(MinecraftServer server, BroomRulesData data) {
        BroomRules.publish(data.stats(), data.disabled(), Config.broomServerSpeedScale);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            BroomDefinitionsSyncS2CPayload.syncToPlayer(player);
        }
    }

    /** The speed-scale rule changed ({@code Config.onLoad}); republished on the server thread. */
    public static void onSpeedScaleChanged() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && server.isRunning()) {
            server.execute(() -> publish(server, BroomRulesData.get(server)));
        }
    }

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        BroomRulesData data = BroomRulesData.get(event.getServer());
        BroomRules.publish(data.stats(), data.disabled(), Config.broomServerSpeedScale);
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        BroomRules.publish(Map.of(), Set.of(), 1.0f);
    }
}
