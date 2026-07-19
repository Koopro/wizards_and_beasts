package at.koopro.wizardsandbeasts.util;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Transient server-side state keyed by player, that <b>cannot</b> outlive the player.
 *
 * <p>The mod had accumulated this shape roughly twenty times over — a {@code static Map<UUID, …>} holding a
 * beam session, a transition, a call, a cast timestamp — and each one had to remember to drop its entry on
 * logout. Most did not, so every player who ever joined left a permanent entry behind; the audit had been
 * fixing them one class at a time (Imperio, Protego, the niffler handler), which is the signature of a
 * missing abstraction rather than a run of unrelated bugs.
 *
 * <p>Every instance registers itself here on construction, and a single logout listener clears the leaving
 * player from all of them. Using this type is therefore the whole fix: there is no cleanup left to forget.
 *
 * <p><b>Server-side only.</b> {@code PlayerLoggedOutEvent} never fires on the client, so client-side
 * per-player caches must not use this — they would never be pruned. Keys are player UUIDs; do not use it
 * for arbitrary entity state, which has no logout to hang cleanup on.
 *
 * @param <T> the per-player value
 */
@NullMarked
public final class PlayerScopedState<T> {

    private static final List<PlayerScopedState<?>> ALL = new CopyOnWriteArrayList<>();

    private final Map<UUID, T> byPlayer = new ConcurrentHashMap<>();
    private final String debugName;

    private PlayerScopedState(String debugName) {
        this.debugName = debugName;
    }

    /**
     * Creates a state holder wired into the shared logout cleanup.
     *
     * @param debugName short identifier for diagnostics, e.g. {@code "wand-beam-sessions"}
     */
    public static <T> PlayerScopedState<T> create(String debugName) {
        PlayerScopedState<T> state = new PlayerScopedState<>(debugName);
        ALL.add(state);
        return state;
    }

    @Nullable
    public T get(UUID playerId) {
        return byPlayer.get(playerId);
    }

    @Nullable
    public T get(Player player) {
        return byPlayer.get(player.getUUID());
    }

    public T getOrDefault(UUID playerId, T fallback) {
        return byPlayer.getOrDefault(playerId, fallback);
    }

    public T computeIfAbsent(UUID playerId, Function<UUID, T> factory) {
        return byPlayer.computeIfAbsent(playerId, factory);
    }

    public void put(UUID playerId, T value) {
        byPlayer.put(playerId, value);
    }

    public void put(Player player, T value) {
        byPlayer.put(player.getUUID(), value);
    }

    @Nullable
    public T remove(UUID playerId) {
        return byPlayer.remove(playerId);
    }

    @Nullable
    public T remove(Player player) {
        return byPlayer.remove(player.getUUID());
    }

    public boolean contains(UUID playerId) {
        return byPlayer.containsKey(playerId);
    }

    public boolean isEmpty() {
        return byPlayer.isEmpty();
    }

    public int size() {
        return byPlayer.size();
    }

    /** Live view for iteration/tick loops. Safe to iterate while mutating — the backing map is concurrent. */
    public Map<UUID, T> view() {
        return byPlayer;
    }

    public void clear() {
        byPlayer.clear();
    }

    @Override
    public String toString() {
        return "PlayerScopedState[" + debugName + ", size=" + byPlayer.size() + "]";
    }

    /** Drops the leaving player from every registered holder. The one place cleanup lives. */
    @EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
    public static final class Cleanup {
        private Cleanup() {}

        @SubscribeEvent
        public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            UUID id = event.getEntity().getUUID();
            for (PlayerScopedState<?> state : ALL) {
                state.remove(id);
            }
        }
    }
}
