package at.koopro.wizardsandbeasts.event.bestiary.niffler;

import at.koopro.wizardsandbeasts.entity.niffler.NifflerEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;

import org.jspecify.annotations.NonNull;

/**
 * Fired when a player successfully opens a Niffler's pouch inventory.
 * Posted on NeoForge.EVENT_BUS. Used for bestiary STUDIED discovery trigger.
 */
public class NifflerPouchOpenEvent extends Event {

    private final @NonNull NifflerEntity niffler;
    private final @NonNull Player player;

    public NifflerPouchOpenEvent(@NonNull NifflerEntity niffler, @NonNull Player player) {
        this.niffler = niffler;
        this.player = player;
    }

    public @NonNull NifflerEntity getNiffler() { return niffler; }
    public @NonNull Player getPlayer() { return player; }
}
