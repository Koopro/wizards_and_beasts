package at.koopro.wizardsandbeasts.heritage.rules;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageTransformService;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.network.PayloadBroadcast;
import at.koopro.wizardsandbeasts.network.heritage.HeritageRulesSyncS2CPayload;
import at.koopro.wizardsandbeasts.sync.PlayerStateSyncService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NullMarked;

import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * The server side of the heritage rules: the only writer of {@link HeritageRulesData}, and the one place that
 * publishes it into {@link HeritageRules} and tells every client.
 *
 * <p>Nothing here decides whether a change is allowed — {@code AdminSettingService} has already authorised,
 * parsed and validated the value before its binding calls {@link #update}. Server thread only.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class HeritageRulesService {

    private HeritageRulesService() {}

    /** Applies {@code change} to one heritage's rule, then publishes, broadcasts and settles online players. */
    public static void update(MinecraftServer server, Heritage heritage, UnaryOperator<HeritageRule> change) {
        HeritageRulesData data = HeritageRulesData.get(server);
        boolean couldTransform = HeritageRules.transformationAllowed(heritage);
        data.put(heritage.getId(), change.apply(data.rule(heritage.getId())));
        publish(server);
        if (couldTransform != HeritageRules.transformationAllowed(heritage)) {
            settleTransformations(server, heritage);
        }
    }

    public static void publish(MinecraftServer server) {
        HeritageRules.publishLocal(HeritageRulesData.get(server).rules());
        PayloadBroadcast.toAll(server, new HeritageRulesSyncS2CPayload(HeritageRules.local()));
    }

    public static void syncTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new HeritageRulesSyncS2CPayload(HeritageRules.local()));
    }

    /**
     * The transformation rule of {@code heritage} changed. Closing it brings every player of that heritage who
     * is in their second shape back through the ordinary change — never a snap, never a trap — and either way
     * the ability wheel is re-granted, so the form button appears or disappears at once.
     */
    private static void settleTransformations(MinecraftServer server, Heritage heritage) {
        boolean allowed = HeritageRules.transformationAllowed(heritage);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerHeritageData data = HeritageTransformService.data(player);
            if (data.getSelectedHeritage() != heritage) {
                continue;
            }
            if (!allowed && HeritageTransformService.isTransformed(data)) {
                HeritageTransformService.exit(player, data);
            }
            PlayerStateSyncService.syncAbilityGrants(player);
        }
    }

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        HeritageRules.publishLocal(HeritageRulesData.get(event.getServer()).rules());
    }

    /** A world closing takes its rules with it; the next world loads its own. */
    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        HeritageRules.publishLocal(Map.of());
    }

    @SubscribeEvent
    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncTo(player);
        }
    }
}
