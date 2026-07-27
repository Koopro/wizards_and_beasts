package at.koopro.wizardsandbeasts.event.heritage;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.network.heritage.HeritageDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.wand.WandAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;

/**
 * Server-side entry point into heritage selection, and the cache invalidation that hangs off it.
 *
 * <p>{@code HeritageSelectionScreen} was built as a hard first-join gate (ESC swallowed, not a pause
 * screen) but nothing ever opened it: the only caller of the {@code openSelector} flag was the admin
 * {@code /wandb heritage reset} command. Without a variant no wand can bond (see
 * {@code WandResonanceSystem}) and no heritage ability can be granted, so a survival player was locked
 * out of the mod. The prompt repeats each login until a heritage is committed.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class HeritageOnboarding {

    private HeritageOnboarding() {}

    /** Opens the selection ceremony for any player who has not committed a heritage yet. */
    @SubscribeEvent
    public static void onLoginPromptSelection(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        if (data.isLocked() || data.getSelectedHeritage() != null) return;

        HeritageDataSyncS2CPayload.syncToPlayer(player, true);
    }

    /**
     * Resonance scores are cached per wood|core|flexibility and two of the four factors read the
     * heritage variant, so every score cached before a heritage change is stale. Clearing on all three
     * transitions keeps a freshly sorted wizard from being judged against their pre-selection self.
     */
    @SubscribeEvent
    public static void onHeritageSelected(HeritageEvents.PlayerHeritageSelectedEvent event) {
        clearResonanceCache(event.getPlayer());
    }

    @SubscribeEvent
    public static void onHeritageChanged(HeritageEvents.PlayerHeritageChangedEvent event) {
        clearResonanceCache(event.getPlayer());
    }

    @SubscribeEvent
    public static void onHeritageReset(HeritageEvents.PlayerHeritageResetEvent event) {
        clearResonanceCache(event.getPlayer());
    }

    private static void clearResonanceCache(ServerPlayer player) {
        player.setData(WandAttachments.WAND_RESONANCE_CACHE.get(), new HashMap<>());
    }
}
