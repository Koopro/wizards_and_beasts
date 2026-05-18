package at.koopro.wizardsandbeasts.bestiary.event;

import net.neoforged.bus.api.Event;

public final class BestiaryEntriesLoadedEvent extends Event {
    private final int count;
    public BestiaryEntriesLoadedEvent(int count) { this.count = count; }
    public int count() { return count; }
}
