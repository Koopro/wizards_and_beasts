package at.koopro.wizardsandbeasts.event.bestiary.niffler;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;

import org.jspecify.annotations.NonNull;

/**
 * Stub event fired when a player earns Magizoology XP (e.g., on bond milestones).
 * Full XP integration is out of scope; listeners may subscribe for future systems.
 * Posted on NeoForge.EVENT_BUS.
 */
public class MagizoologyXPEvent extends Event {

    private final @NonNull Player player;
    private final int xpAmount;
    private final @NonNull String source;

    public MagizoologyXPEvent(@NonNull Player player, int xpAmount, @NonNull String source) {
        this.player = player;
        this.xpAmount = xpAmount;
        this.source = source;
    }

    public @NonNull Player getPlayer() { return player; }
    public int getXpAmount() { return xpAmount; }
    /** Human-readable source tag, e.g. "niffler_bond_20". */
    public @NonNull String getSource() { return source; }
}
