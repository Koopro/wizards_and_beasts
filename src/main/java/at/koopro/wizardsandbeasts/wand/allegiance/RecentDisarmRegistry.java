package at.koopro.wizardsandbeasts.wand.allegiance;

import net.minecraft.server.level.ServerLevel;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks recent disarm events so {@link WandDisarmAllegianceSystem} can tell combat disarms from voluntary swaps.
 * // Spell system (future): call {@link #registerDisarm} when a disarm spell successfully removes a wand.
 */
public final class RecentDisarmRegistry {
    private static final Map<UUID, Entry> ENTRIES = new ConcurrentHashMap<>();

    private RecentDisarmRegistry() {
    }

    public record Entry(UUID attacker, long gameTime) {
    }

    public static void registerDisarm(ServerLevel level, UUID victim, UUID attacker) {
        ENTRIES.put(victim, new Entry(attacker, level.getGameTime()));
    }

    public static Optional<Entry> consumeIfRecent(ServerLevel level, UUID victim, int maxAgeTicks) {
        prune(level, maxAgeTicks);
        Entry e = ENTRIES.remove(victim);
        if (e == null) {
            return Optional.empty();
        }
        if (level.getGameTime() - e.gameTime > maxAgeTicks) {
            return Optional.empty();
        }
        return Optional.of(e);
    }

    private static void prune(ServerLevel level, int maxAgeTicks) {
        long now = level.getGameTime();
        Iterator<Map.Entry<UUID, Entry>> it = ENTRIES.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().gameTime > maxAgeTicks) {
                it.remove();
            }
        }
    }
}
