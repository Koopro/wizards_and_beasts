package at.koopro.wizardsandbeasts.client.module;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.registry.ModCreativeTabs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.SessionSearchTrees;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.CreativeModeTabSearchRegistry;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Rebuilds this mod's creative tabs when the answer they were built from changes: a module state sync, or
 * tags being rebound.
 *
 * <p><b>Why not just call {@code CreativeModeTabs.tryRebuildTabContents}.</b> That method compares the
 * parameters it was last given and returns without doing anything when they match — and it compares the
 * {@code HolderLookup.Provider} by identity. A module flipping changes none of feature flags, operator
 * status or registry access, so the vanilla entry point is a no-op for exactly the case this class exists
 * to handle. Rebuilding our two tabs directly is the supported way round it: {@code buildContents} is
 * public API and fires {@code BuildCreativeModeTabContentsEvent} the same as vanilla would.
 *
 * <p>Only this mod's tabs are rebuilt. Another mod's tab holding our items is that mod's to refresh, and
 * silently re-running someone else's generator is a good way to duplicate their entries.
 *
 * <p>A creative screen that is already open keeps the contents it built with; reopening it picks up the
 * new listing. Flipping a module while staring at the inventory is not worth reaching into an open screen.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class CreativeTabRefresher {

    private CreativeTabRefresher() {}

    /** Subscribes the refresher to module syncs once the client is up. */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ClientModuleState.whenApplied(CreativeTabRefresher::refresh));
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        // The index rebuilds from the same event; ordering between the two handlers does not matter,
        // because this one reads the index lazily inside buildContents rather than at subscribe time.
        refresh();
    }

    public static void refresh() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            // No world means no creative screen to be stale — the tabs have not been built yet.
            return;
        }

        CreativeModeTab.ItemDisplayParameters parameters = new CreativeModeTab.ItemDisplayParameters(
                player.connection.enabledFeatures(),
                player.canUseGameMasterBlocks() && minecraft.options.operatorItemsTab().get(),
                minecraft.level.registryAccess());

        for (var tab : List.of(ModCreativeTabs.MAIN.get(), ModCreativeTabs.DECORATIVE_BLOCKS.get())) {
            tab.buildContents(parameters);
            updateSearchTrees(minecraft, parameters, tab);
        }
    }

    /**
     * Keeps the creative search index in step with what the tab now holds. Without this a hidden item is
     * gone from its tab but still typeable into the search bar, which reads as the gate half-working.
     */
    private static void updateSearchTrees(Minecraft minecraft,
                                          CreativeModeTab.ItemDisplayParameters parameters,
                                          CreativeModeTab tab) {
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null || !tab.hasSearchBar()) {
            return;
        }
        SessionSearchTrees searchTrees = connection.searchTrees();
        List<ItemStack> contents = List.copyOf(tab.getDisplayItems());
        searchTrees.updateCreativeTooltips(parameters.holders(), contents,
                CreativeModeTabSearchRegistry.getNameSearchKey(tab));
        searchTrees.updateCreativeTags(contents, CreativeModeTabSearchRegistry.getTagSearchKey(tab));
    }
}
