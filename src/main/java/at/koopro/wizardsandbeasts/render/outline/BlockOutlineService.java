package at.koopro.wizardsandbeasts.render.outline;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.outline.BlockOutlineS2CPayload;
import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-authoritative, timed block highlights, each shown to one viewer. Callers go through
 * {@link SpellOutlines}.
 *
 * <p>One call is one highlight: the position list goes over the wire once, and when the duration runs out
 * {@link #tick} sends only its id.
 *
 * <h2>Overlap</h2>
 *
 * <p>Same rule as entity outlines: <b>never shortens</b>. A second highlight over the same blocks is its own
 * group on its own clock, so a block stays marked until the last group covering it ends; while both run, the
 * newer one's colour shows.
 *
 * <h2>What the server holds</h2>
 *
 * <p>Only an expiry per highlight id, per viewer. The positions and colour live on the client, the only side
 * that draws them.
 *
 * <h2>Lifetime</h2>
 *
 * <p>A highlight ends when it expires, when its viewer logs out ({@link PlayerScopedState}), or when its
 * viewer changes dimension — there the server drops it silently, because the client clears its own copy on
 * the same event and a position list means nothing in the next world.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class BlockOutlineService {

    /** Per viewer: highlight id to the game time it expires. Server thread only; empty maps are removed. */
    private static final PlayerScopedState<Map<Integer, Long>> ACTIVE =
            PlayerScopedState.create("block-outlines");

    private static int nextId = 1;

    private BlockOutlineService() {}

    /**
     * Highlights blocks for one viewer. Sends nothing, and tracks nothing, for an empty set or a zero duration.
     *
     * @param positions in the viewer's current dimension. {@code BlockPos.betweenClosed} can be passed straight
     *                  in: it yields one {@code MutableBlockPos} over and over, and each position is copied as it
     *                  is visited. A list built by the caller must already hold {@code pos.immutable()} — a list
     *                  of that one mutable object says the same block N times before anything here sees it.
     * @return whether anything was sent
     */
    static boolean highlight(ServerPlayer viewer, Iterable<BlockPos> positions, OutlineStyle style) {
        List<BlockPos> copy = copyPositions(positions);
        if (copy.isEmpty() || style.durationTicks() <= 0) {
            return false;
        }
        int id = allocateId();
        long expiresAt = EntityOutlineService.gameTime(viewer.level().getServer()) + style.durationTicks();
        track(viewer.getUUID(), id, expiresAt);
        BlockOutlineS2CPayload.sendAdd(viewer, id, new OutlineEntry(style.argb(), expiresAt), copy);
        return true;
    }

    /** Ends every highlight the viewer has. Sends only if there was one. */
    static void clearAll(ServerPlayer viewer) {
        if (ACTIVE.remove(viewer) != null) {
            BlockOutlineS2CPayload.send(viewer, BlockOutlineS2CPayload.clearAll());
        }
    }

    /** Ends highlights whose time is up and tells their viewers. Returns at once when nothing is active. */
    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        expire(EntityOutlineService.gameTime(server)).forEach((viewerId, ids) -> {
            ServerPlayer viewer = server.getPlayerList().getPlayer(viewerId);
            if (viewer == null) {
                return;
            }
            for (int id : ids) {
                BlockOutlineS2CPayload.send(viewer, BlockOutlineS2CPayload.remove(id));
            }
        });
    }

    /** Highlights the server still considers live for this viewer. */
    public static int activeCount(UUID viewerId) {
        Map<Integer, Long> active = ACTIVE.get(viewerId);
        return active == null ? 0 : active.size();
    }

    // ── state seams (package-private for BlockOutlineTest; nothing here touches the network) ──

    /** Immutable copy of each position as it is visited — see {@link #highlight} on {@code betweenClosed}. */
    static List<BlockPos> copyPositions(Iterable<BlockPos> positions) {
        List<BlockPos> copy = new ArrayList<>();
        for (BlockPos pos : positions) {
            copy.add(pos.immutable());
        }
        return copy;
    }

    static void track(UUID viewerId, int highlightId, long expiresAt) {
        ACTIVE.computeIfAbsent(viewerId, id -> new HashMap<>()).put(highlightId, expiresAt);
    }

    /** Removes highlights due at {@code gameTime}; returns the expired ids per viewer. */
    static Map<UUID, List<Integer>> expire(long gameTime) {
        Map<UUID, List<Integer>> expired = new LinkedHashMap<>();
        for (Map.Entry<UUID, Map<Integer, Long>> viewer : ACTIVE.view().entrySet()) {
            Iterator<Map.Entry<Integer, Long>> it = viewer.getValue().entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, Long> highlight = it.next();
                if (gameTime >= highlight.getValue()) {
                    it.remove();
                    expired.computeIfAbsent(viewer.getKey(), id -> new ArrayList<>()).add(highlight.getKey());
                }
            }
            if (viewer.getValue().isEmpty()) {
                ACTIVE.remove(viewer.getKey());
            }
        }
        return expired;
    }

    static void reset() {
        ACTIVE.clear();
        nextId = 1;
    }

    /** Unique for the server's lifetime; wraps past {@code MAX_VALUE} to 1, skipping 0. */
    private static int allocateId() {
        int id = nextId;
        nextId = nextId == Integer.MAX_VALUE ? 1 : nextId + 1;
        return id;
    }

    // ── lifecycle ──

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tick(event.getServer());
    }

    /** Silent on purpose: the client drops its own copy on the same dimension change. */
    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        ACTIVE.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        reset();
    }
}
