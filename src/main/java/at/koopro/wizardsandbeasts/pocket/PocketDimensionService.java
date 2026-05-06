package at.koopro.wizardsandbeasts.pocket;

import at.koopro.wizardsandbeasts.network.PocketStatusS2CPacket;
import at.koopro.wizardsandbeasts.registry.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.Set;
import java.util.UUID;

public final class PocketDimensionService {
    private static final int GRID_SPACING = 512;
    private static final int FLOOR_Y = 64;

    private PocketDimensionService() {
    }

    public static PocketRecord getOrCreatePocket(ServerPlayer player,
                                                 UUID caseId,
                                                 PocketArchetype archetype,
                                                 String templateId,
                                                 PocketAccessMode accessMode) {
        if (!(player.level() instanceof ServerLevel playerLevel)) {
            throw new IllegalStateException("Pocket operations require ServerLevel context");
        }
        PocketSavedData data = PocketSavedData.get(playerLevel);

        PocketRecord existing = data.getCasePocket(caseId).orElse(null);
        if (existing != null) {
            if (existing.owner().equals(player.getUUID())) {
                data.updateRecord(existing.pocketId(), record ->
                        record.withArchetype(archetype).withTemplateId(templateId).withAccessMode(accessMode));
                return data.getPocket(existing.pocketId()).orElse(existing);
            }
            return existing;
        }

        BlockPos spawn = computePocketSpawn(caseId);
        PocketRecord created = PocketRecord.create(player.getUUID(), archetype, templateId, spawn);
        created = created.withAccessMode(accessMode);
        data.putRecord(created);
        data.bindCase(caseId, created.pocketId());
        return created;
    }

    public static void enterPocket(ServerPlayer player, PocketRecord record) {
        if (!(player.level() instanceof ServerLevel sourceLevel)) {
            return;
        }
        ServerLevel target = sourceLevel.getServer().getLevel(ModDimensions.POCKET_REALM);
        if (target == null) {
            PocketStatusS2CPacket.send(player, "Pocket realm is missing from datapacks.");
            return;
        }
        if (!record.canAccess(player.getUUID())) {
            PocketStatusS2CPacket.send(player, "This case is bound to a private pocket.");
            return;
        }

        ensurePocketShell(target, record);
        BlockPos spawn = findSafeSpawn(target, record.spawnPos());
        TeleportTransition transition = new TeleportTransition(
                target,
                Vec3.atCenterOf(spawn),
                Vec3.ZERO,
                player.getYRot(),
                player.getXRot(),
                Set.of(),
                entity -> {});
        player.teleport(transition);
        PocketStatusS2CPacket.send(player, "Entering " + record.pocketName());
    }

    public static void exitPocket(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel sourceLevel)) {
            return;
        }
        ServerLevel overworld = sourceLevel.getServer().overworld();
        BlockPos sharedSpawn = BlockPos.ZERO;
        BlockPos safe = findSafeSpawn(overworld, sharedSpawn);
        TeleportTransition transition = new TeleportTransition(
                overworld,
                Vec3.atCenterOf(safe),
                Vec3.ZERO,
                player.getYRot(),
                player.getXRot(),
                Set.of(),
                entity -> {});
        player.teleport(transition);
        PocketStatusS2CPacket.send(player, "Returning from pocket realm");
    }

    private static BlockPos computePocketSpawn(UUID pocketId) {
        int xIndex = Math.floorMod((int) pocketId.getMostSignificantBits(), 2048);
        int zIndex = Math.floorMod((int) pocketId.getLeastSignificantBits(), 2048);
        return new BlockPos(xIndex * GRID_SPACING, FLOOR_Y + 2, zIndex * GRID_SPACING);
    }

    private static BlockPos findSafeSpawn(ServerLevel level, BlockPos origin) {
        BlockPos.MutableBlockPos cursor = origin.mutable();
        for (int i = 0; i < 16; i++) {
            BlockState feet = level.getBlockState(cursor);
            BlockState head = level.getBlockState(cursor.above());
            if (feet.isAir() && head.isAir()) {
                return cursor.immutable();
            }
            cursor.move(0, 1, 0);
        }
        return origin;
    }

    private static void ensurePocketShell(ServerLevel level, PocketRecord record) {
        BlockPos center = record.spawnPos();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int halfSize = switch (record.archetype()) {
            case SCAMANDER_SANCTUARY -> 20;
            case ROOM_OF_REQUIREMENT -> 14;
            case CUSTOM_PLAYER_TEMPLATE -> 12;
        };

        BlockState floor = switch (record.archetype()) {
            case SCAMANDER_SANCTUARY -> Blocks.MOSS_BLOCK.defaultBlockState();
            case ROOM_OF_REQUIREMENT -> Blocks.POLISHED_ANDESITE.defaultBlockState();
            case CUSTOM_PLAYER_TEMPLATE -> Blocks.SMOOTH_STONE.defaultBlockState();
        };

        for (int x = -halfSize; x <= halfSize; x++) {
            for (int z = -halfSize; z <= halfSize; z++) {
                cursor.set(center.getX() + x, center.getY() - 1, center.getZ() + z);
                if (level.getBlockState(cursor).isAir()) {
                    level.setBlock(cursor, floor, 3);
                }
            }
        }

        // Room-of-Requirement style adaptive utility ring.
        if (record.archetype() == PocketArchetype.ROOM_OF_REQUIREMENT) {
            placeUtilityRing(level, center, halfSize - 2);
        }
    }

    private static void placeUtilityRing(ServerLevel level, BlockPos center, int radius) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                    continue;
                }
                cursor.set(center.getX() + dx, center.getY(), center.getZ() + dz);
                if (level.getBlockState(cursor).isAir()) {
                    level.setBlock(cursor, Blocks.BOOKSHELF.defaultBlockState(), 3);
                }
            }
        }
    }
}
