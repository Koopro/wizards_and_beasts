package at.koopro.wizardsandbeasts.map;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.map.MapSyncS2CPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public class MaraudersMapTracker {

    private static final int BASE_SYNC_INTERVAL_TICKS = 5;
    private static final int MAX_SYNC_INTERVAL_TICKS = 12;
    private static final int MAX_ENTITIES_PER_SYNC = 200;
    private static final int MIN_ENTITIES_PER_SYNC = 80;
    private static final int MAX_VIEWERS_PER_TICK = 10;

    private static final Map<UUID, MapViewData> activeViewers = new HashMap<>();
    private static int tickCounter;
    private static int viewerRoundRobinOffset;

    public record MapViewData(BlockPos center, int radius, Identifier dimension) {
    }

    public static void addPlayer(ServerPlayer player, BlockPos center, int radius, Identifier dimension) {
        activeViewers.put(player.getUUID(), new MapViewData(center, radius, dimension));
    }

    public static void removePlayer(UUID uuid) {
        activeViewers.remove(uuid);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        removePlayer(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        removePlayer(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        removePlayer(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (activeViewers.isEmpty()) return;
        int syncInterval = computeSyncInterval(activeViewers.size());
        if (++tickCounter < syncInterval) return;
        tickCounter = 0;

        MinecraftServer server = event.getServer();
        List<Map.Entry<UUID, MapViewData>> snapshot = new ArrayList<>(activeViewers.entrySet());
        if (snapshot.isEmpty()) return;
        int viewersToProcess = computeViewersToProcess(snapshot.size());
        int start = Math.floorMod(viewerRoundRobinOffset, snapshot.size());
        viewerRoundRobinOffset = (start + viewersToProcess) % snapshot.size();

        for (int i = 0; i < viewersToProcess; i++) {
            int idx = (start + i) % snapshot.size();
            Map.Entry<UUID, MapViewData> entry = snapshot.get(idx);
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                activeViewers.remove(entry.getKey());
                continue;
            }

            MapViewData data = entry.getValue();
            ResourceKey<Level> dimKey = ResourceKey.create(
                    Registries.DIMENSION, data.dimension());
            ServerLevel level = server.getLevel(dimKey);
            if (level == null) {
                activeViewers.remove(entry.getKey());
                continue;
            }

            int perViewerCap = computePerViewerEntityCap(activeViewers.size());
            List<TrackedEntityEntry> entities = gatherEntities(level, data.center(), data.radius(), perViewerCap);
            PacketDistributor.sendToPlayer(player, new MapSyncS2CPayload(entities));
        }
    }

    private static List<TrackedEntityEntry> gatherEntities(
            ServerLevel level, BlockPos center, int radius, int maxEntries) {

        List<TrackedEntityEntry> entries = new ArrayList<>();
        AABB area = new AABB(
                center.getX() - radius, level.getMinY(),
                center.getZ() - radius,
                center.getX() + radius, level.getMaxY(),
                center.getZ() + radius);

        for (Entity entity : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, area)) {
            byte category;
            String name;

            if (entity instanceof Player p) {
                category = TrackedEntityEntry.PLAYER;
                name = p.getName().getString();
            } else if (entity instanceof Monster) {
                category = TrackedEntityEntry.HOSTILE;
                name = entity.getType().getDescription().getString();
            } else if (entity instanceof net.minecraft.world.entity.Mob) {
                category = TrackedEntityEntry.PASSIVE;
                name = entity.getType().getDescription().getString();
            } else {
                continue;
            }

            entries.add(new TrackedEntityEntry(
                    entity.getUUID(),
                    entity.getX(),
                    entity.getZ(),
                    entity.getYRot(),
                    name,
                    category));

            if (entries.size() >= maxEntries) break;
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
