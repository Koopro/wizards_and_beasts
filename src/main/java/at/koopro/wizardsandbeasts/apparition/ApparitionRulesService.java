package at.koopro.wizardsandbeasts.apparition;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jspecify.annotations.NullMarked;

import java.util.function.UnaryOperator;

/**
 * The only writer of {@link ApparitionRulesData}: stores a change and republishes {@link ApparitionRules} so the next
 * jump reads it. Server thread only. A stopped server publishes the authored rules, so an integrated world never
 * hands its tuning to the next one opened in the same session.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class ApparitionRulesService {

    private ApparitionRulesService() {}

    public static void update(MinecraftServer server, UnaryOperator<ApparitionRules.Tuning> change) {
        ApparitionRulesData data = ApparitionRulesData.get(server);
        data.set(change.apply(data.tuning()));
        ApparitionRules.publish(data.tuning());
    }

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        ApparitionRules.publish(ApparitionRulesData.get(event.getServer()).tuning());
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        ApparitionRules.publish(ApparitionRules.Tuning.AUTHORED);
    }
}
