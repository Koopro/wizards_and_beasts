package at.koopro.wizardsandbeasts.wand.rules;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.PayloadBroadcast;
import at.koopro.wizardsandbeasts.network.wand.WandGlobalsSyncS2CPayload;
import at.koopro.wizardsandbeasts.wand.making.WandmakingRules;
import at.koopro.wizardsandbeasts.wand.making.WandmakingSpec;
import net.minecraft.resources.Identifier;
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
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.Set;

/**
 * The server side of the wand rules: the only writer of {@link WandRulesData}, the publisher of {@link WandGlobals}
 * (from {@code Config}), and the one pairing lookup every wand-making path shares ({@link #specFor}).
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class WandRulesService {

    private WandRulesService() {}

    public static void setWood(MinecraftServer server, Identifier wood, boolean disabled) {
        WandRulesData data = WandRulesData.get(server);
        data.setWood(wood.toString(), disabled);
        publish(data);
    }

    public static void setCore(MinecraftServer server, Identifier core, boolean disabled) {
        WandRulesData data = WandRulesData.get(server);
        data.setCore(core.toString(), disabled);
        publish(data);
    }

    public static void setPair(MinecraftServer server, Identifier wood, Identifier core, boolean disabled) {
        WandRulesData data = WandRulesData.get(server);
        data.setPair(WandRules.pairKey(wood, core), disabled);
        publish(data);
    }

    private static void publish(WandRulesData data) {
        WandRules.publish(data.woods(), data.cores(), data.pairs());
    }

    /**
     * This wood married to this core, or empty when either is not defined. Every defined wood pairs with every
     * defined core ({@link WandmakingRules}). The bench, Ollivander's commissions and the admin tools all use this
     * lookup. It ignores the withdrawal rules, which {@link #makeable} adds.
     */
    public static Optional<WandmakingSpec> specFor(MinecraftServer server, Identifier wood, Identifier core) {
        return WandmakingRules.spec(server.registryAccess(), wood, core);
    }

    /** A pairing whose wood and core are defined and that no rule withdraws. */
    public static Optional<WandmakingSpec> makeable(MinecraftServer server, @Nullable Identifier wood, @Nullable Identifier core) {
        if (wood == null || core == null || !WandRules.mayMake(wood, core)) {
            return Optional.empty();
        }
        return specFor(server, wood, core);
    }

    /** Every pairing (every wood with every core), as {@code wood|core} keys. */
    public static Set<String> pairs(MinecraftServer server) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (WandmakingSpec spec : WandmakingRules.all(server.registryAccess())) {
            out.add(WandRules.pairKey(spec.woodKey(), spec.coreKey()));
        }
        return out;
    }

    // ── globals ──

    /** Config changed. Published locally at once; clients told on the server thread. */
    public static void onGlobalsChanged(WandGlobals.Values values) {
        WandGlobals.publishLocal(values);
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && server.isRunning()) {
            server.execute(() -> PayloadBroadcast.toAll(server, new WandGlobalsSyncS2CPayload(WandGlobals.local())));
        }
    }

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        publish(WandRulesData.get(event.getServer()));
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        WandRules.publish(Set.of(), Set.of(), Set.of());
    }

    @SubscribeEvent
    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new WandGlobalsSyncS2CPayload(WandGlobals.local()));
        }
    }
}
