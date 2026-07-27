package at.koopro.wizardsandbeasts.event.brew;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.brew.BrewDataSyncPayload;
import at.koopro.wizardsandbeasts.wand.recipe.WandmakingRecipeType;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Gets this mod's crafting data to the client, for both pipelines that a recipe viewer needs to describe.
 *
 * <p><b>Wandmaking.</b> Since 1.21.2 the vanilla recipe packet carries only ingredient property sets and
 * stonecutter recipes — full recipes are no longer synced. NeoForge restores it per type, but only for
 * types a mod explicitly asks for here, and JEI asks for exactly seven hard-coded vanilla types. Without
 * this request, {@code WandmakingRecipe} instances simply do not exist on a client connected to a
 * dedicated server, and any viewer category built from them is empty.
 *
 * <p><b>Brewing.</b> Not a {@code Recipe} at all, so {@code sendRecipes} cannot carry it; it goes as its
 * own payload. See {@code BrewDataSyncPayload} for why it has to travel at all.
 *
 * <p>Both fire on {@link OnDatapackSyncEvent}, which covers first join and every {@code /reload}. A null
 * player means a reload affecting everyone, so the brew snapshot is broadcast rather than sent once.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class RecipeDataSyncEvents {

    private RecipeDataSyncEvents() {}

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        event.sendRecipes(WandmakingRecipeType.INSTANCE.get());

        ServerPlayer player = event.getPlayer();
        if (player != null) {
            BrewDataSyncPayload.sendTo(player);
        } else {
            BrewDataSyncPayload.broadcast(event.getPlayerList().getServer());
        }
    }
}
