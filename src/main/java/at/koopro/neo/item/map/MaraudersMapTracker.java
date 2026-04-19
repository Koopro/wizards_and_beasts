package at.koopro.neo.item.map;

import at.koopro.neo.Neo;
import at.koopro.neo.network.MapSyncPacket;
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
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

@EventBusSubscriber(modid = Neo.MODID)
public class MaraudersMapTracker {

    private static final int SYNC_INTERVAL_TICKS = 5;
    private static final int MAX_ENTITIES_PER_SYNC = 200;

    private static final Map<UUID, MapViewData> activeViewers = new HashMap<>();
    private static int tickCounter;

    public record MapViewData(BlockPos center, int radius, Identifier dimension) {
    }

    public static void addPlayer(ServerPlayer player, BlockPos center, int radius, Identifier dimension) {
        activeViewers.put(player.getUUID(), new MapViewData(center, radius, dimension));
    }

    public static void removePlayer(UUID uuid) {
        activeViewers.remove(uuid);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (activeViewers.isEmpty()) return;
        if (++tickCounter < SYNC_INTERVAL_TICKS) return;
        tickCounter = 0;

        MinecraftServer server = event.getServer();
        Iterator<Map.Entry<UUID, MapViewData>> it = activeViewers.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<UUID, MapViewData> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }

            MapViewData data = entry.getValue();
            ResourceKey<Level> dimKey = ResourceKey.create(
                    Registries.DIMENSION, data.dimension());
            ServerLevel level = server.getLevel(dimKey);
            if (level == null) continue;

            List<TrackedEntityEntry> entities = gatherEntities(level, data.center(), data.radius());
            PacketDistributor.sendToPlayer(player, new MapSyncPacket(entities));
        }
    }

    private static List<TrackedEntityEntry> gatherEntities(
            ServerLevel level, BlockPos center, int radius) {

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

            if (entries.size() >= MAX_ENTITIES_PER_SYNC) break;
        }

        return entries;
    }
}
