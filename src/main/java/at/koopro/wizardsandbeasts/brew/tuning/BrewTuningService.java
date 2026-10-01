package at.koopro.wizardsandbeasts.brew.tuning;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.brew.BrewingRecipe;
import at.koopro.wizardsandbeasts.brew.CauldronHeat;
import at.koopro.wizardsandbeasts.network.brew.BrewDataSyncPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jspecify.annotations.NullMarked;

import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * The server side of brew administration: the only writer of {@link BrewTuningData}, the thing that republishes
 * the effective registries and resends them to clients, and the one reading of the global brewing rules
 * ({@code Config}) that the cauldron consults. Server thread only.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class BrewTuningService {

    private BrewTuningService() {}

    public static void updateBrew(MinecraftServer server, String brewId, UnaryOperator<BrewOverride> change) {
        BrewTuningData data = BrewTuningData.get(server);
        data.putBrew(brewId, change.apply(data.brew(brewId)));
        publish(server, data);
    }

    public static void updateRecipe(MinecraftServer server, String recipeId, UnaryOperator<RecipeOverride> change) {
        BrewTuningData data = BrewTuningData.get(server);
        data.putRecipe(recipeId, change.apply(data.recipe(recipeId)));
        publish(server, data);
    }

    /** Re-registers the effective brews and recipes and sends them to every client (the existing brew sync). */
    private static void publish(MinecraftServer server, BrewTuningData data) {
        BrewTuning.publishOverrides(data.brews(), data.recipes());
        BrewDataSyncPayload.broadcast(server);
    }

    // ── global rules, read at the cauldron's own decision points ──

    /** Whether a cauldron may start a new brew at all. */
    public static boolean brewingEnabled() {
        return Config.brewingEnabled;
    }

    /** The heat time a brew started now gets: the recipe's, divided by the speed rule. */
    public static int heatTimeFor(BrewingRecipe recipe) {
        return Math.max(1, Math.round(recipe.heatTimeTicks() / Math.max(0.1f, Config.brewSpeedMultiplier)));
    }

    /** The recipe's own failure chance at the completion roll, scaled by the failure rule; at most 1. */
    public static float baseFailureFor(BrewingRecipe recipe) {
        return Math.min(1f, Math.max(0f, recipe.failureChance() * Config.brewFailureMultiplier));
    }

    /** Whether a cauldron over {@code heatPos} counts as heated: it does, when heat is not required. */
    public static boolean heated(Level level, BlockPos heatPos) {
        return !Config.brewRequireHeatSource || CauldronHeat.hasHeatSource(level, heatPos);
    }

    /** Failure chance added by one wrong item in a working pot. */
    public static float contaminationPenalty() {
        return Config.brewContaminationPenalty;
    }

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        BrewTuningData data = BrewTuningData.get(event.getServer());
        BrewTuning.publishOverrides(data.brews(), data.recipes());
    }

    /** A world closing takes its overrides with it; the registries go back to what the datapacks define. */
    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        BrewTuning.publishOverrides(Map.of(), Map.of());
    }
}
