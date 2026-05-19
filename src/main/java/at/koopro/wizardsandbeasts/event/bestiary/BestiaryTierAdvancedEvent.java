package at.koopro.wizardsandbeasts.event.bestiary;

import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public final class BestiaryTierAdvancedEvent extends Event implements ICancellableEvent {
    private final Player player;
    private final Identifier entryId;
    private final DiscoveryTier oldTier;
    private final DiscoveryTier newTier;

    public BestiaryTierAdvancedEvent(Player player, Identifier entryId, DiscoveryTier oldTier, DiscoveryTier newTier) {
        this.player = player;
        this.entryId = entryId;
        this.oldTier = oldTier;
        this.newTier = newTier;
    }

    public Player player() { return player; }
    public Identifier entryId() { return entryId; }
    public DiscoveryTier oldTier() { return oldTier; }
    public DiscoveryTier newTier() { return newTier; }
}
