package at.koopro.wizardsandbeasts.bestiary;

import at.koopro.wizardsandbeasts.bestiary.data.PlayerBestiaryData;
import at.koopro.wizardsandbeasts.event.bestiary.BestiaryTierAdvancedEvent;
import at.koopro.wizardsandbeasts.network.bestiary.BestiaryDataSyncPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;

import java.util.HashMap;
import java.util.Map;

public final class BestiaryDataHelper {
    private BestiaryDataHelper() {}

    public static DiscoveryTier getTier(Player player, Identifier entryId) {
        return player.getData(ModAttachments.BESTIARY_DATA.get()).tiers().getOrDefault(entryId, DiscoveryTier.UNDISCOVERED);
    }

    public static boolean hasEntry(Player player, Identifier entryId) {
        return getTier(player, entryId) != DiscoveryTier.UNDISCOVERED;
    }

    public static void setTier(Player player, Identifier entryId, DiscoveryTier requestedTier) {
        DiscoveryTier old = getTier(player, entryId);
        if (requestedTier.ordinal() < old.ordinal()) {
            return;
        }
        BestiaryTierAdvancedEvent event = new BestiaryTierAdvancedEvent(player, entryId, old, requestedTier);
        if (NeoForge.EVENT_BUS.post(event).isCanceled()) {
            return;
        }
        Map<Identifier, DiscoveryTier> map = new HashMap<>(player.getData(ModAttachments.BESTIARY_DATA.get()).tiers());
        map.put(entryId, requestedTier);
        player.setData(ModAttachments.BESTIARY_DATA.get(), new PlayerBestiaryData(map));
        if (player instanceof ServerPlayer sp) {
            BestiaryDataSyncPayload.syncToPlayer(sp);
        }
    }

    public static void forceSetTier(Player player, Identifier entryId, DiscoveryTier tier) {
        DiscoveryTier old = getTier(player, entryId);
        if (old == tier) {
            return;
        }
        BestiaryTierAdvancedEvent event = new BestiaryTierAdvancedEvent(player, entryId, old, tier);
        if (NeoForge.EVENT_BUS.post(event).isCanceled()) {
            return;
        }
        Map<Identifier, DiscoveryTier> map = new HashMap<>(player.getData(ModAttachments.BESTIARY_DATA.get()).tiers());
        if (tier == DiscoveryTier.UNDISCOVERED) {
            map.remove(entryId);
        } else {
            map.put(entryId, tier);
        }
        player.setData(ModAttachments.BESTIARY_DATA.get(), new PlayerBestiaryData(map));
        if (player instanceof ServerPlayer sp) {
            BestiaryDataSyncPayload.syncToPlayer(sp);
        }
    }

    public static void advanceTier(Player player, Identifier entryId) {
        setTier(player, entryId, getTier(player, entryId).next());
    }
}
