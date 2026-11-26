package at.koopro.wizardsandbeasts.event.brew;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.brew.BrewDataSyncPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Gets this mod's brewing data to the client, for the recipe viewer.
 *
 * <p>Wandmaking needs nothing here since 2026-11-26: its pairs are a rule over the wood and core registries
 * ({@code WandmakingRules}), and those datapack registries are synced to the client with the rest.
 *
 * <p><b>Brewing.</b> Not a {@code Recipe} at all, so {@code sendRecipes} cannot carry it; it goes as its
 * own payload. See {@code BrewDataSyncPayload} for why it has to travel at all.
 *
 * <p>It fires on {@link OnDatapackSyncEvent}, which covers first join and every {@code /reload}. A null
 * player means a reload affecting everyone, so the brew snapshot is broadcast rather than sent once.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class RecipeDataSyncEvents {

    private RecipeDataSyncEvents() {}

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        ServerPlayer player = event.getPlayer();
        if (player != null) {
            BrewDataSyncPayload.sendTo(player);
            at.koopro.wizardsandbeasts.brew.knowledge.BrewKnowledgeS2CPayload.sendTo(player);
        } else {
            BrewDataSyncPayload.broadcast(event.getPlayerList().getServer());
            at.koopro.wizardsandbeasts.brew.knowledge.BrewKnowledgeS2CPayload.broadcast(event.getPlayerList().getServer());
        }
    }
}
