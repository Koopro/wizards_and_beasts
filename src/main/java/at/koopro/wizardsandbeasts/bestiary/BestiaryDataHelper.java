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

    public static PlayerBestiaryData getData(Player player) {
        return player.getData(ModAttachments.BESTIARY_DATA.get());
    }

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
        at.koopro.wizardsandbeasts.bestiary.data.EntryHistory before =
                player.getData(ModAttachments.BESTIARY_DATA.get()).historyOf(entryId);
        PlayerBestiaryData next = player.getData(ModAttachments.BESTIARY_DATA.get()).copy();
        // Harvest lockouts and observation time are carried through: they live on the same record, and a tier
        // advancing is not a reason to hand back a rare drop or forget the hours spent watching.
        next.tiers().put(entryId, requestedTier);
        if (requestedTier.ordinal() > old.ordinal()) {
            // The journal's discovery history: the day each depth came, and where the creature was first met.
            next.history().put(entryId, next.historyOf(entryId).reached(requestedTier,
                    player.level().getGameTime() / 24000L, biomeAt(player), player.level().dimension().identifier().toString()));
        }
        player.setData(ModAttachments.BESTIARY_DATA.get(), next);
        if (player instanceof ServerPlayer sp) {
            at.koopro.wizardsandbeasts.bestiary.journal.ResearchRewards.check(sp, entryId);
            BestiaryDataSyncPayload.syncToPlayer(sp);
            PlayerStatsSyncPayload.syncToPlayer(sp); // KNOWLEDGE derives from bestiary discoveries
            // Only the earned path fires a deed. forceSetTier is the admin/debug door and must not
            // move a player's standing as a side effect of an operator fixing their data.
            at.koopro.wizardsandbeasts.standing.deed.DeedService.onBestiaryTier(sp, entryId, requestedTier);
            // Field study past a first sighting is a Magizoologist's work; a glimpse is not.
            if (requestedTier.tierIndex() >= DiscoveryTier.OBSERVED.tierIndex()) {
                at.koopro.wizardsandbeasts.owl.career.CareerWork.record(sp, at.koopro.wizardsandbeasts.owl.career.CareerWork.Kind.CREATURE_CARE);
            }
            // A page gone deeper is the unit of field work a career track counts (surveys, rank milestones).
            if (requestedTier.ordinal() > old.ordinal()) {
                at.koopro.wizardsandbeasts.owl.career.track.CareerProgression.recordBestiary(sp, entryId, requestedTier);
            }
            // The Ministry's finder's fee for a first field record: once per species, ever.
            ExplorationFees.onTierEarned(sp, entryId, old, requestedTier, before);
            if (requestedTier == DiscoveryTier.KNOWN && old != DiscoveryTier.KNOWN) {
                // Knowing a creature is progress the skill web pays for, the way mastering a spell is — however
                // the page was completed: watching, study, a signature, or a bond deep enough to know it by.
                at.koopro.wizardsandbeasts.event.skill.SkillEvents.awardForKnownSpecies(sp);
            }
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

    private static String biomeAt(Player player) {
        return player.level().getBiome(player.blockPosition()).unwrapKey()
                .map(key -> key.identifier().toString()).orElse("");
    }

    // ── study acts and research ────────────────────────────────────────────

    /**
     * Records a hands-on study act on {@code entryId} — fed it, handled it, took what it shed, won its trust — for the
     * journal's research checklist, then checks whether the research is now complete. Syncs.
     */
    public static void recordStudyAct(ServerPlayer player, Identifier entryId,
                                      at.koopro.wizardsandbeasts.bestiary.CreatureProfile.StudyAct act) {
        PlayerBestiaryData current = player.getData(ModAttachments.BESTIARY_DATA.get());
        if (current.historyOf(entryId).acts().contains(act.getSerializedName())) {
            return;
        }
        PlayerBestiaryData next = current.copy();
        next.history().put(entryId, current.historyOf(entryId).withAct(act.getSerializedName()));
        player.setData(ModAttachments.BESTIARY_DATA.get(), next);
        at.koopro.wizardsandbeasts.bestiary.journal.ResearchRewards.check(player, entryId);
        BestiaryDataSyncPayload.syncToPlayer(player);
    }

    /** Marks research on {@code entryId} complete. Only {@code ResearchRewards} calls this, once per entry. */
    public static void markResearchComplete(ServerPlayer player, Identifier entryId) {
        PlayerBestiaryData next = player.getData(ModAttachments.BESTIARY_DATA.get()).copy();
        next.history().put(entryId, next.historyOf(entryId).completed());
        player.setData(ModAttachments.BESTIARY_DATA.get(), next);
    }

    /** Whether this player has completed research on any page describing {@code species}. */
    public static boolean researchComplete(Player player, net.minecraft.world.entity.EntityType<?> species) {
        PlayerBestiaryData data = player.getData(ModAttachments.BESTIARY_DATA.get());
        for (BestiaryEntry entry : at.koopro.wizardsandbeasts.event.bestiary.BestiaryDiscoveryHandler.entriesFor(species)) {
            if (data.historyOf(entry.id()).complete()) {
                return true;
            }
        }
        return false;
    }

    /** How many creatures this player has completed research on. */
    public static int completedResearch(Player player) {
        return (int) player.getData(ModAttachments.BESTIARY_DATA.get()).history().values().stream()
                .filter(at.koopro.wizardsandbeasts.bestiary.data.EntryHistory::complete).count();
    }

    // ── field notes ────────────────────────────────────────────────────────

    /**
     * Writes a field note on {@code entryId} and syncs the page. Returns whether it was new — the caller tells the
     * player only then.
     */
    public static boolean addFieldNote(ServerPlayer player, Identifier entryId, String note) {
        PlayerBestiaryData current = player.getData(ModAttachments.BESTIARY_DATA.get());
        if (current.notesOn(entryId).contains(note)) {
            return false;
        }
        PlayerBestiaryData next = current.copy();
        next.fieldNotes().computeIfAbsent(entryId, id -> new java.util.LinkedHashSet<>()).add(note);
        player.setData(ModAttachments.BESTIARY_DATA.get(), next);
        at.koopro.wizardsandbeasts.bestiary.journal.ResearchRewards.check(player, entryId);
        at.koopro.wizardsandbeasts.network.bestiary.BestiaryDataSyncPayload.syncToPlayer(player);
        return true;
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
        int total = observedTotal(next.observedTicks(entryId), ticks);
        next.observation().put(entryId, total);
        player.setData(ModAttachments.BESTIARY_DATA.get(), next);
        return total;
    }

    /** Watching time after adding {@code ticks} to {@code current}, saturating rather than overflowing. */
    public static int observedTotal(int current, int ticks) {
        return (int) Math.min(Integer.MAX_VALUE, (long) current + Math.max(0, ticks));
    }

    /**
     * Stores several entries' watching totals in one write — one copy of the record rather than one per entry.
     * The bestiary scan used to copy the whole record (tiers, harvest lockouts, watching time) once for every
     * creature a player watched each second (documentation/PERFORMANCE_AUDIT.md). Server-side and unsynced, like
     * {@link #addObservedTicks}.
     *
     * @param totals entry id → new total (already summed, see {@link #observedTotal})
     */
    public static void setObservedTicks(ServerPlayer player, java.util.Map<Identifier, Integer> totals) {
        if (totals.isEmpty()) {
            return;
        }
        PlayerBestiaryData next = player.getData(ModAttachments.BESTIARY_DATA.get()).copy();
        next.observation().putAll(totals);
        player.setData(ModAttachments.BESTIARY_DATA.get(), next);
    }
}
