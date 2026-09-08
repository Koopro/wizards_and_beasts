package at.koopro.wizardsandbeasts.bestiary.debug;

import at.koopro.wizardsandbeasts.bestiary.BestiaryDataHelper;
import at.koopro.wizardsandbeasts.bestiary.BestiaryEntryRegistry;
import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.command.debug.dev.DevLog;
import at.koopro.wizardsandbeasts.command.debug.dev.FeatureDevKit;
import at.koopro.wizardsandbeasts.network.bestiary.BestiaryDataSyncPayload;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * Every entry at the top tier, which is what unlocks harvesting.
 *
 * <p>Rare drops are tier-gated, so testing a beast's loot table means first getting its entry to
 * IDENTIFIED or MASTERED — and the honest way to do that is to go and study the beast, repeatedly,
 * which is not a thing anyone should do before every drop test. This is the shortcut, and it is the
 * one gate that most reliably makes a working drop look broken.
 */
@NullMarked
public final class BestiaryDevKit implements FeatureDevKit {

    @Override
    public String id() {
        return "bestiary";
    }

    @Override
    public String title() {
        return "Bestiary";
    }

    @Override
    public String summary() {
        return "Every entry to MASTERED - tier gates harvesting, so this unblocks drop testing.";
    }

    @Override
    public void open(ServerPlayer target, DevLog log) {
        writeAll(target, log, DiscoveryTier.MASTERED);
    }

    @Override
    public void reset(ServerPlayer target, DevLog log) {
        writeAll(target, log, DiscoveryTier.UNDISCOVERED);
    }

    /**
     * {@code forceSetTier}, not {@code setTier}.
     *
     * <p>The ordinary setter only ever raises a tier — it is the discovery path, and discovery does
     * not go backwards. That is right for gameplay and useless for a reset, which has to be able to
     * put an entry back to UNDISCOVERED.
     */
    private static void writeAll(ServerPlayer target, DevLog log, DiscoveryTier tier) {
        var entries = BestiaryEntryRegistry.getAll();
        if (entries.isEmpty()) {
            log.warn("no bestiary entries registered - the datapack listener has not run");
            return;
        }
        entries.forEach(entry -> BestiaryDataHelper.forceSetTier(target, entry.id(), tier));
        BestiaryDataSyncPayload.syncToPlayer(target);
        log.changed("bestiary entries", entries.size() + " set to " + tier);
    }
}
