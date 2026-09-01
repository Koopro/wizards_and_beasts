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
        }
    }

    public static void set(ServerPlayer player, PlayerMinistryRecord record) {
        player.setData(ModAttachments.MINISTRY_RECORD.get(), record);
        MinistryRecordSyncS2CPayload.syncToPlayer(player);
    }
}
