package at.koopro.wizardsandbeasts.ministry;

import at.koopro.wizardsandbeasts.ministry.data.PlayerMinistryRecord;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NullMarked;

import java.util.function.UnaryOperator;

/**
 * Read/mutate access to a player's {@link PlayerMinistryRecord}. Every change goes through {@link #mutate}
 * so there is one place to hang syncing or auditing later, rather than {@code setData} calls scattered
 * across the enforcement code.
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
        }
    }

    public static void set(ServerPlayer player, PlayerMinistryRecord record) {
        player.setData(ModAttachments.MINISTRY_RECORD.get(), record);
    }
}
