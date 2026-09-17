package at.koopro.wizardsandbeasts.bestiary;

import at.koopro.wizardsandbeasts.bestiary.data.PlayerBestiaryData;
import at.koopro.wizardsandbeasts.event.bestiary.BestiaryTierAdvancedEvent;
import at.koopro.wizardsandbeasts.network.bestiary.BestiaryDataSyncPayload;
import at.koopro.wizardsandbeasts.network.stats.PlayerStatsSyncPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;


public final class BestiaryDataHelper {
    private BestiaryDataHelper() {}

    public static DiscoveryTier getTier(Player player, Identifier entryId) {
        return player.getData(ModAttachments.BESTIARY_DATA.get()).tiers().getOrDefault(entryId, DiscoveryTier.UNKNOWN);
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
        PlayerBestiaryData next = player.getData(ModAttachments.BESTIARY_DATA.get()).copy();
        // Harvest lockouts and observation time are carried through: they live on the same record, and a tier
        // advancing is not a reason to hand back a rare drop or forget the hours spent watching.
        next.tiers().put(entryId, requestedTier);
        player.setData(ModAttachments.BESTIARY_DATA.get(), next);
        if (player instanceof ServerPlayer sp) {
            BestiaryDataSyncPayload.syncToPlayer(sp);
            PlayerStatsSyncPayload.syncToPlayer(sp); // KNOWLEDGE derives from bestiary discoveries
            // Only the earned path fires a deed. forceSetTier is the admin/debug door and must not
            // move a player's standing as a side effect of an operator fixing their data.
            at.koopro.wizardsandbeasts.standing.deed.DeedService.onBestiaryTier(sp, entryId, requestedTier);
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
        PlayerBestiaryData next = player.getData(ModAttachments.BESTIARY_DATA.get()).copy();
        if (tier == DiscoveryTier.UNKNOWN) {
            next.tiers().remove(entryId);
            // Clearing an entry outright clears its lockout and its watching time too: an operator resetting
            // someone's bestiary should not leave an invisible timer behind on an entry that no longer exists.
            next.lastHarvests().remove(entryId);
            next.observation().remove(entryId);
        } else {
            next.tiers().put(entryId, tier);
        }
        player.setData(ModAttachments.BESTIARY_DATA.get(), next);
        if (player instanceof ServerPlayer sp) {
            BestiaryDataSyncPayload.syncToPlayer(sp);
            PlayerStatsSyncPayload.syncToPlayer(sp); // KNOWLEDGE derives from bestiary discoveries
        }
    }

    // ── rare harvest lockouts ──────────────────────────────────────────────

    /**
     * Game time of this player's last rare harvest of {@code entryId}, or
     * {@link at.koopro.wizardsandbeasts.bestiary.harvest.HarvestGate#NEVER} if they have never taken one.
     */
    public static long getLastHarvestTick(Player player, Identifier entryId) {
        Long tick = player.getData(ModAttachments.BESTIARY_DATA.get()).lastHarvests().get(entryId);
        return tick == null ? at.koopro.wizardsandbeasts.bestiary.harvest.HarvestGate.NEVER : tick;
    }

    /**
     * Records a rare harvest. Server-side only and deliberately does <b>not</b> re-sync: the client is
     * never told about lockouts, because nothing on a client decides loot and a timer it could read is
     * a timer it could be written to lie about.
     */
    public static void recordHarvest(ServerPlayer player, Identifier entryId, long gameTime) {
        PlayerBestiaryData next = player.getData(ModAttachments.BESTIARY_DATA.get()).copy();
        next.lastHarvests().put(entryId, gameTime);
        player.setData(ModAttachments.BESTIARY_DATA.get(), next);
    }

    // ── observation ────────────────────────────────────────────────────────

    public static int getObservedTicks(Player player, Identifier entryId) {
        return player.getData(ModAttachments.BESTIARY_DATA.get()).observedTicks(entryId);
    }

    /**
     * Adds watching time. Server-side and unsynced, like harvest lockouts: the client is only ever told the tier
     * the time earns.
     *
     * @return the new total
     */
    public static int addObservedTicks(ServerPlayer player, Identifier entryId, int ticks) {
        PlayerBestiaryData next = player.getData(ModAttachments.BESTIARY_DATA.get()).copy();
        int total = (int) Math.min(Integer.MAX_VALUE, (long) next.observedTicks(entryId) + Math.max(0, ticks));
        next.observation().put(entryId, total);
        player.setData(ModAttachments.BESTIARY_DATA.get(), next);
        return total;
    }
}
