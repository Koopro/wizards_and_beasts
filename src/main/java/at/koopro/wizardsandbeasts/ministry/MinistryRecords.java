package at.koopro.wizardsandbeasts.ministry;

import at.koopro.wizardsandbeasts.ministry.data.PlayerMinistryRecord;
import at.koopro.wizardsandbeasts.network.ministry.MinistryRecordSyncS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NullMarked;

import java.util.function.UnaryOperator;

/**
 * Read/mutate access to a player's {@link PlayerMinistryRecord}. Every change goes through {@link #mutate}
 * so there is one place to hang syncing or auditing, rather than {@code setData} calls scattered across the
 * enforcement code.
 *
 * <p>That seam now carries the client sync. Writes that change nothing send nothing — the equality check in
 * {@link #mutate} runs before the push — which matters because the Ministry tick calls through here every
 * second for every online player.
 */
@NullMarked
public final class MinistryRecords {

    private MinistryRecords() {}

    public static PlayerMinistryRecord get(Player player) {
        return player.getData(ModAttachments.MINISTRY_RECORD.get());
    }

    public static void mutate(ServerPlayer player, UnaryOperator<PlayerMinistryRecord> mutator) {
        PlayerMinistryRecord previous = get(player);
        PlayerMinistryRecord next = mutator.apply(previous);
        if (!next.equals(previous)) {
            player.setData(ModAttachments.MINISTRY_RECORD.get(), next);
            MinistryRecordSyncS2CPayload.syncToPlayer(player);
            if (ministryStandingMoved(previous, next)) {
                // The Ministry axis is derived from this record; without this the sheet showed yesterday's band.
                at.koopro.wizardsandbeasts.standing.StandingService.sync(player);
            }
        }
    }

    /**
     * Whether a record change moves what the client shows on the Ministry standing axis: rank, conduct, or a
     * notoriety change that crosses a band. Heat decays every second, so a plain inequality would resend standing
     * every second to every wizard cooling off.
     */
    static boolean ministryStandingMoved(PlayerMinistryRecord previous, PlayerMinistryRecord next) {
        return previous.rank() != next.rank() || previous.conduct() != next.conduct()
                || ministryBand(previous) != ministryBand(next);
    }

    private static at.koopro.wizardsandbeasts.standing.StandingBand ministryBand(PlayerMinistryRecord record) {
        float bound = at.koopro.wizardsandbeasts.Config.standingAxisBound;
        float value = at.koopro.wizardsandbeasts.standing.StandingBands.ministryOf(
                at.koopro.wizardsandbeasts.standing.StandingService.rankCredit(record.rank()), record.notoriety(),
                record.conduct(), bound);
        return at.koopro.wizardsandbeasts.standing.StandingBands.bandFor(value, bound,
                at.koopro.wizardsandbeasts.Config.standingLeanThresholdPercent,
                at.koopro.wizardsandbeasts.Config.standingStrongThresholdPercent);
    }

    public static void set(ServerPlayer player, PlayerMinistryRecord record) {
        player.setData(ModAttachments.MINISTRY_RECORD.get(), record);
        MinistryRecordSyncS2CPayload.syncToPlayer(player);
    }
}
