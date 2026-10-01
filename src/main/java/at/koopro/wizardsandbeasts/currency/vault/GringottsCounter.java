package at.koopro.wizardsandbeasts.currency.vault;

import at.koopro.wizardsandbeasts.entity.goblin.GoblinTellerEntity;
import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NullMarked;

/**
 * Which goblin teller let a player up to the counter — the server's record of a vault visit.
 *
 * <p>The teller is the vault's only door: it checks the Ministry-access papers ({@code MinistryLicenceGate}) and then
 * opens the vault screen. Before this record the vault packet was accepted from anywhere, so a client could deposit
 * and withdraw across the world without a teller and without papers (2026-09-29, documentation/MULTIPLAYER_AUDIT.md).
 * Now a vault action is honoured only while the teller that admitted the player is alive and within
 * {@link #REACH} blocks.
 */
@NullMarked
public final class GringottsCounter {

    /** How far from the teller a player may stand and still be at its counter. */
    public static final double REACH = 8.0;

    /** Entity id of the teller that admitted each player. Dropped at logout. */
    private static final PlayerScopedState<Integer> ADMITTED_BY = PlayerScopedState.create("gringotts_counter");

    private GringottsCounter() {}

    /** The teller checked this player's papers and opened the vault. */
    public static void admit(ServerPlayer player, GoblinTellerEntity teller) {
        ADMITTED_BY.put(player.getUUID(), teller.getId());
    }

    /** Whether this player is at the counter of the teller that admitted them. */
    public static boolean atCounter(ServerPlayer player) {
        Integer id = ADMITTED_BY.get(player.getUUID());
        if (id == null) {
            return false;
        }
        Entity teller = player.level().getEntity(id);
        return teller instanceof GoblinTellerEntity goblin && goblin.isAlive()
                && player.isAlive() && player.distanceToSqr(goblin) <= REACH * REACH;
    }
}
