package at.koopro.wizardsandbeasts.event.broom;

import net.neoforged.bus.api.Event;

public final class BroomDefinitionsLoadedEvent extends Event {
    private final int loadedCount;

    public BroomDefinitionsLoadedEvent(int loadedCount) {
        this.loadedCount = loadedCount;
    }

    public int loadedCount() {
        return loadedCount;
    }
}
