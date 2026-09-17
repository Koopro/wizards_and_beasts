package at.koopro.wizardsandbeasts.render.outline;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.outline.EntityOutlineS2CPayload;
import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.NullMarked;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The server's record of which entities carry a coloured outline, and the only thing that syncs it.
 *
 * <p>Callers do not use this class. Gameplay goes through {@link SpellOutlines}, the debug command through
 * {@link DebugOutlines}; each can reach exactly one of the two layers below, so a spell cannot touch a debug
 * outline and a debug command cannot shorten a spell's.
 *
 * <h2>Two layers</h2>
 *
 * <ul>
 *   <li><b>Permanent</b> — players only, held in {@link PlayerScopedState}. {@link DebugOutlines}.</li>
 *   <li><b>Timed</b> — any entity, with an expiry in game time. {@link SpellOutlines}.</li>
 * </ul>
 *
 * <p>The client keeps one outline per entity, so the server sends the <em>effective</em> one: the timed layer
 * while it lasts, the permanent layer underneath it, else the clear. Broadcasting a bare clear when a timed
 * outline expired would wipe a debug outline set on the same player before the spell was.
 *
 * <h2>Overlap</h2>
 *
 * <p>A second timed outline on the same entity <b>never shortens</b> the first: it takes the newer colour and
 * the later of the two expiries. Two Revelio casts a second apart therefore read as one reveal lasting until
 * the second one ends, and a short cast cannot cut off a long one. Block highlights follow the same rule —
 * see {@link BlockOutlineService}.
 *
 * <h2>Who sees it</h2>
 *
 * <p>Every change is broadcast to all players, and a joining player is sent the full snapshot: an entity
 * outline is for other people to see. Expiry is decided here; the client is told when an outline ends only so
 * it can fade the last second, and still waits for the clear.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class EntityOutlineService {

    /** Packed ARGB per outlined player. Never holds {@code 0} — that is stored as absence. */
    private static final PlayerScopedState<Integer> PERMANENT =
            PlayerScopedState.create("entity-outlines");

    /**
     * Timed outlines by entity UUID. Not {@link PlayerScopedState}: the keys are arbitrary entities, which
     * have no logout to hang cleanup on — expiry is their lifetime rule. Server thread only.
     */
    private static final Map<UUID, OutlineEntry> TIMED = new HashMap<>();

    private EntityOutlineService() {}

    // ── permanent layer: DebugOutlines only ──

    static void setPermanent(ServerPlayer target, int argb) {
        PERMANENT.put(target.getUUID(), argb);
        broadcast(target.getUUID());
    }

    /** Always sends: the debug "off" is also how a client left with a stale outline is repaired. */
    static void clearPermanent(ServerPlayer target) {
        PERMANENT.remove(target.getUUID());
        broadcast(target.getUUID());
    }

    // ── timed layer: SpellOutlines only ──

    /** One broadcast for the whole batch; nothing at all for an empty one. Client-side entities are skipped. */
    static void setTimed(Collection<? extends Entity> targets, OutlineStyle style) {
        if (style.durationTicks() <= 0 || targets.isEmpty()) {
            return;
        }
        Map<UUID, OutlineEntry> changed = new LinkedHashMap<>();
        for (Entity target : targets) {
            if (!(target.level() instanceof ServerLevel level)) {
                continue;
            }
            UUID id = target.getUUID();
            putTimed(id, style.argb(), gameTime(level.getServer()) + style.durationTicks());
            changed.put(id, effective(id));
        }
        if (!changed.isEmpty()) {
            EntityOutlineS2CPayload.broadcast(changed);
        }
    }

    /** Sends only if there was a timed outline to remove; what is left underneath shows again. */
    static void clearTimed(Entity target) {
        UUID id = target.getUUID();
        if (TIMED.remove(id) != null) {
            broadcast(id);
        }
    }

    // ── sync ──

    /**
     * Drops expired timed outlines and tells every client what is left on those entities, batched into one
     * payload. Runs every server tick, so the common case — nothing timed — returns at once.
     */
    public static void tick(MinecraftServer server) {
        if (TIMED.isEmpty()) {
            return;
        }
        Map<UUID, OutlineEntry> changed = expire(gameTime(server));
        if (!changed.isEmpty()) {
            EntityOutlineS2CPayload.broadcast(changed);
        }
    }

    /** Sends the whole table to a player who has just joined and has an empty client map. */
    public static void syncToPlayer(ServerPlayer player) {
        EntityOutlineS2CPayload.sendSnapshot(player, snapshot());
    }

    /** Effective outline per outlined entity — exactly what a client should hold right now. */
    public static Map<UUID, OutlineEntry> snapshot() {
        Map<UUID, OutlineEntry> snapshot = new HashMap<>();
        PERMANENT.view().forEach((id, argb) -> snapshot.put(id, OutlineEntry.permanent(argb)));
        snapshot.putAll(TIMED);
        return snapshot;
    }

    // ── state seams (package-private for EntityOutlineTest; nothing here touches the network) ──

    /**
     * 24-bit {@code 0xRRGGBB} to opaque ARGB, for the permanent layer's bare-RGB callers.
     *
     * <p>The alpha byte is load-bearing: {@code EntityRenderState.appearsGlowing()} is literally
     * {@code outlineColor != 0}, and the outline shader masks by alpha, so an RGB left at alpha 0 is accepted,
     * stored, synced, and then draws nothing with nothing to debug. It is also what makes pure black usable.
     */
    static int pack(int rgb) {
        return ARGB.opaque(rgb & 0xFFFFFF);
    }

    static void putPermanent(UUID entityId, int argb) {
        PERMANENT.put(entityId, argb);
    }

    /** The overlap rule: newer colour, later expiry. See the class note. */
    static void putTimed(UUID entityId, int argb, long expiresAt) {
        OutlineEntry existing = TIMED.get(entityId);
        long until = existing == null ? expiresAt : Math.max(existing.expiresAt(), expiresAt);
        TIMED.put(entityId, new OutlineEntry(argb, until));
    }

    /** Timed layer first, then permanent, else {@link OutlineEntry#NONE}. */
    static OutlineEntry effective(UUID entityId) {
        OutlineEntry timed = TIMED.get(entityId);
        if (timed != null) {
            return timed;
        }
        Integer permanent = PERMANENT.get(entityId);
        return permanent == null ? OutlineEntry.NONE : OutlineEntry.permanent(permanent);
    }

    /** Removes timed outlines due at {@code gameTime}; returns each one's entity and what it shows now. */
    static Map<UUID, OutlineEntry> expire(long gameTime) {
        Map<UUID, OutlineEntry> changed = new LinkedHashMap<>();
        Iterator<Map.Entry<UUID, OutlineEntry>> it = TIMED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, OutlineEntry> timed = it.next();
            if (gameTime >= timed.getValue().expiresAt()) {
                it.remove();
                changed.put(timed.getKey(), effective(timed.getKey()));
            }
        }
        return changed;
    }

    /** Whether either layer holds anything for this entity. */
    static boolean holds(UUID entityId) {
        return PERMANENT.contains(entityId) || TIMED.containsKey(entityId);
    }

    /** Forgets both layers without broadcasting — for a server that is going away, and for tests. */
    static void reset() {
        PERMANENT.clear();
        TIMED.clear();
    }

    /**
     * Overworld game time. Other dimensions read the same counter through {@code DerivedLevelData}, and it
     * holds still under {@code /tick freeze}, so an outline does not run out while the world is paused.
     * Shared with {@link BlockOutlineService} so both outline kinds run on one clock.
     */
    static long gameTime(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    private static void broadcast(UUID entityId) {
        EntityOutlineS2CPayload.broadcast(Map.of(entityId, effective(entityId)));
    }

    // ── lifecycle ──

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tick(event.getServer());
    }

    /**
     * A player who leaves takes both layers with them, and every other client is told — otherwise they keep
     * the colour and show it again when the player rejoins with the same UUID.
     *
     * <p>HIGHEST so this reads the permanent layer before {@link PlayerScopedState}'s own logout sweep empties
     * it; at equal priority the order is class-scan order, and losing that race would skip the broadcast.
     * Silent for a player with nothing outlined, which is nearly every logout.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        if (!holds(id)) {
            return;
        }
        PERMANENT.remove(id);
        TIMED.remove(id);
        EntityOutlineS2CPayload.broadcast(Map.of(id, OutlineEntry.NONE));
    }

    /**
     * The table is static, so an integrated server would hand the next world this one's entries — and their
     * expiry times, measured on a game clock the next world does not share.
     */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        reset();
    }
}
