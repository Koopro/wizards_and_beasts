package at.koopro.wizardsandbeasts.event.bestiary.niffler;

import at.koopro.wizardsandbeasts.entity.niffler.NifflerEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

import org.jspecify.annotations.NonNull;

/**
 * Fired when a Niffler flees after its pouch is accessed without sufficient bond.
 * Posted on NeoForge.EVENT_BUS. Cancellable — cancel to prevent the flee.
 */
public class NifflerAngryEvent extends Event implements ICancellableEvent {

    private final @NonNull NifflerEntity niffler;
    private final @NonNull Player offendingPlayer;

    public NifflerAngryEvent(@NonNull NifflerEntity niffler, @NonNull Player offendingPlayer) {
        this.niffler = niffler;
        this.offendingPlayer = offendingPlayer;
    }

    public @NonNull NifflerEntity getNiffler() { return niffler; }
    public @NonNull Player getOffendingPlayer() { return offendingPlayer; }

}
