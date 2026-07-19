package at.koopro.wizardsandbeasts.ability.grant;

import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Command-driven debug override source ({@code /wandb ability grant|revoke}). Holds a transient, in-memory
 * per-player set of forced ability keys — deliberately NOT persisted (a debug convenience, cleared on
 * restart). Grants surface in the merged snapshot under {@link AbilityGrants.Source#DEBUG} so tooling can
 * tell them apart from earned grants. Singleton because the store is process-global server state.
 */
@NullMarked
public final class DebugAbilityGrantSource implements AbilityGrantSource {

    public static final DebugAbilityGrantSource INSTANCE = new DebugAbilityGrantSource();

    private final Map<UUID, Set<String>> forced = new ConcurrentHashMap<>();

    private DebugAbilityGrantSource() {}

    @Override
    public AbilityGrants.Source source() {
        return AbilityGrants.Source.DEBUG;
    }

    @Override
    public List<String> grantsFor(ServerPlayer player) {
        Set<String> set = forced.get(player.getUUID());
        return set == null || set.isEmpty() ? List.of() : List.copyOf(set);
    }

    /** Adds a debug grant; returns {@code true} if it was newly added. */
    public boolean grant(ServerPlayer player, String abilityKey) {
        if (abilityKey.isBlank()) {
            return false;
        }
        return forced.computeIfAbsent(player.getUUID(), k -> ConcurrentHashMap.newKeySet())
                .add(abilityKey);
    }

    /** Removes a debug grant; returns {@code true} if it was present. */
    public boolean revoke(ServerPlayer player, String abilityKey) {
        Set<String> set = forced.get(player.getUUID());
        return set != null && set.remove(abilityKey);
    }

    public Set<String> forcedFor(ServerPlayer player) {
        Set<String> set = forced.get(player.getUUID());
        return set == null ? Set.of() : Set.copyOf(set);
    }
}
