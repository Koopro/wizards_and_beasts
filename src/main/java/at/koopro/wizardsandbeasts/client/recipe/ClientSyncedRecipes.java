package at.koopro.wizardsandbeasts.client.recipe;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The recipes the server chose to send this client.
 *
 * <p>Since 1.21.2 the client has no populated {@code RecipeManager} — the vanilla packet carries only
 * ingredient property sets and stonecutter recipes. NeoForge sends full recipes for types a mod requests
 * through {@code OnDatapackSyncEvent.sendRecipes} and hands them over here, once, as a {@link RecipeMap}.
 * There is no client-side accessor to fetch it again later, so it has to be kept when it arrives.
 *
 * <p>Cleared on disconnect, as {@code RecipesReceivedEvent}'s own documentation instructs: holding one
 * server's recipes while connected to another would describe crafts that do not exist there.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class ClientSyncedRecipes {

    private static volatile RecipeMap current = RecipeMap.EMPTY;

    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private ClientSyncedRecipes() {}

    /** Run after each delivery, so a recipe viewer can rebuild from what actually arrived. */
    public static void whenReceived(Runnable listener) {
        LISTENERS.add(listener);
    }

    public static <I extends RecipeInput, T extends Recipe<I>> List<T> byType(RecipeType<T> type) {
        return current.byType(type).stream().map(RecipeHolder::value).toList();
    }

    @SubscribeEvent
    public static void onRecipesReceived(RecipesReceivedEvent event) {
        current = event.getRecipeMap();
        LISTENERS.forEach(Runnable::run);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        current = RecipeMap.EMPTY;
        LISTENERS.forEach(Runnable::run);
    }
}
