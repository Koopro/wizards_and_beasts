package at.koopro.wizardsandbeasts.util;

import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NullMarked;

/**
 * At most {@code perTick} requests from one player in one server tick, counted rather than spaced.
 *
 * <p>The gap throttle ({@link PlayerScopedState#throttle}) is wrong for clicks: a lagging server runs every packet that
 * piled up during the lag in one tick, all at the same game time, so a gap of two ticks kept the first click and dropped
 * the rest without a word. A budget per tick lets a burst of honest clicks through whole and still bounds what one client
 * can make the server do. What it refuses, it says so: {@link Verdict#REFUSED_FIRST} marks the first refusal of a tick,
 * which is when the caller tells the player, once, rather than once per dropped packet.
 */
@NullMarked
public final class TickBudget {

    public enum Verdict {
        TAKEN,
        /** Over budget, and the first refusal this tick: tell the player. */
        REFUSED_FIRST,
        /** Over budget, already told this tick. */
        REFUSED;

        public boolean taken() {
            return this == TAKEN;
        }
    }

    /** {@code [tick, spent]} per player. */
    private final PlayerScopedState<long[]> spent;
    private final int perTick;

    public TickBudget(String debugName, int perTick) {
        this.spent = PlayerScopedState.create(debugName);
        this.perTick = perTick;
    }

    public int perTick() {
        return perTick;
    }

    public Verdict spend(Player player, long now) {
        long[] row = spent.computeIfAbsent(player.getUUID(), id -> new long[] {now, 0});
        if (row[0] != now) {
            row[0] = now;
            row[1] = 0;
        }
        row[1]++;
        if (row[1] <= perTick) {
            return Verdict.TAKEN;
        }
        return row[1] == perTick + 1 ? Verdict.REFUSED_FIRST : Verdict.REFUSED;
    }
}
