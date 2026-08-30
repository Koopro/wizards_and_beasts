package at.koopro.wizardsandbeasts.map;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import at.koopro.wizardsandbeasts.network.map.MapSyncS2CPayload;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The sweep behind the moving dots.
 *
 * <h2>What the map is allowed to see</h2>
 * Everything alive within {@link MapSession#SENSE_RADIUS} of the holder, invisibility included.
 * That is the artefact: it is the one thing this map does that no other map does, and a Marauder's
 * Map that can be fooled by a Disillusionment Charm is not a Marauder's Map.
 *
 * <p>The privacy rule is therefore drawn somewhere else, in two places, and both of them are hard:
 * the radius is small enough that using it is an act of local surveillance rather than a
 * server-wide player list, and the map only sweeps for someone on its own trusted list. Spectators
 * are excluded because they are not in the world to be sensed.
 *
 * <p>None of this touches the parchment. Terrain and markers come from the atlas and reveal only
 * what has been walked; the dots are live and reveal only what is close.
 *
 * <h2>Scaling</h2>
 * Three dials, all pure functions so they can be reasoned about and tested without a server: the
 * sweep slows down as viewers are added, each viewer's entity cap shrinks, and the number of
 * viewers served per tick is capped so a hundred open maps cannot stall a tick.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class MaraudersMapTracker {

    private static final int BASE_SYNC_INTERVAL_TICKS = 5;
    private static final int MAX_SYNC_INTERVAL_TICKS = 12;
    private static final int MAX_ENTITIES_PER_SYNC = 200;
    private static final int MIN_ENTITIES_PER_SYNC = 80;
    private static final int MAX_VIEWERS_PER_TICK = 10;

    private static int tickCounter;
    private static int viewerRoundRobinOffset;

    private MaraudersMapTracker() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (MapSessions.isEmpty()) {
            return;
        }
        List<Map.Entry<UUID, MapSession>> snapshot = new ArrayList<>(MapSessions.entries());
        if (snapshot.isEmpty()) {
            return;
        }
        int syncInterval = computeSyncInterval(snapshot.size());
        if (++tickCounter < syncInterval) {
            return;
        }
        tickCounter = 0;

        MinecraftServer server = event.getServer();
        int viewersToProcess = computeViewersToProcess(snapshot.size());
        int start = Math.floorMod(viewerRoundRobinOffset, snapshot.size());
        viewerRoundRobinOffset = (start + viewersToProcess) % snapshot.size();
        int perViewerCap = computePerViewerEntityCap(snapshot.size());

        for (int i = 0; i < viewersToProcess; i++) {
            Map.Entry<UUID, MapSession> entry = snapshot.get((start + i) % snapshot.size());
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                MapSessions.close(entry.getKey());
                continue;
            }

            MapSession session = entry.getValue();
            ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, session.dimension());
            ServerLevel level = server.getLevel(dimKey);
            if (level == null) {
                MapSessions.close(entry.getKey());
                continue;
            }

            // The sweep is centred on the holder, every time. The old implementation centred it on
            // wherever the map had first been unfolded, which is why walking away from that spot
            // emptied the map and never refilled it.
            List<TrackedEntityEntry> entities = gatherEntities(
                    level, player, MapSession.SENSE_RADIUS, perViewerCap);
            PacketDistributor.sendToPlayer(player, new MapSyncS2CPayload(entities));
        }
    }

    private static List<TrackedEntityEntry> gatherEntities(
            ServerLevel level, ServerPlayer holder, int radius, int maxEntries) {

        int cap = Math.min(maxEntries, PacketCodecUtils.MAX_MAP_ENTRIES);
        List<TrackedEntityEntry> entries = new ArrayList<>(Math.min(cap, 64));
        AABB area = new AABB(
                holder.getX() - radius, level.getMinY(), holder.getZ() - radius,
                holder.getX() + radius, level.getMaxY(), holder.getZ() + radius);

        for (Entity entity : level.getEntitiesOfClass(LivingEntity.class, area)) {
            byte category;
            String name;

            if (entity instanceof Player p) {
                if (p.isSpectator()) {
                    continue;
                }
                category = TrackedEntityEntry.PLAYER;
                name = p.getName().getString();
            } else if (entity instanceof Monster) {
                category = TrackedEntityEntry.HOSTILE;
                name = entity.getType().getDescription().getString();
            } else if (entity instanceof Mob) {
                category = TrackedEntityEntry.PASSIVE;
                name = entity.getType().getDescription().getString();
            } else {
                continue;
            }

            entries.add(new TrackedEntityEntry(
                    entity.getUUID(),
                    entity.getX(),
                    entity.getY(),
                    entity.getZ(),
                    entity.getYRot(),
                    name,
                    category));

            if (entries.size() >= cap) break;
        }

        return entries;
    }

    static int computeSyncInterval(int viewers) {
        int extra = Math.max(0, viewers - 2);
        return Math.min(MAX_SYNC_INTERVAL_TICKS, BASE_SYNC_INTERVAL_TICKS + extra);
    }

    static int computePerViewerEntityCap(int viewers) {
        int reduction = Math.max(0, viewers - 1) * 15;
        return Math.max(MIN_ENTITIES_PER_SYNC, MAX_ENTITIES_PER_SYNC - reduction);
    }

    static int computeViewersToProcess(int viewerCount) {
        return Math.max(0, Math.min(MAX_VIEWERS_PER_TICK, viewerCount));
    }
}
