package at.koopro.wizardsandbeasts.trunk;

import at.koopro.wizardsandbeasts.event.trunk.PocketDimensionEvents;
import at.koopro.wizardsandbeasts.network.trunk.PocketStatusS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ExtensionCharmService {

    private static final Logger LOGGER = LogManager.getLogger(ExtensionCharmService.class);

    static final int GRID_SPACING = 512;
    static final int FLOOR_Y = 64;

    private static final Climate.Sampler DUMMY_SAMPLER = new Climate.Sampler(
            DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(),
            DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(),
            java.util.List.of());

    private ExtensionCharmService() {}

    public static TrunkRecord getOrCreatePocket(ServerPlayer player,
                                                UUID caseId,
                                                TrunkArchetype archetype,
                                                String templateId,
                                                TrunkAccessMode accessMode,
                                                boolean muggleWorthy,
                                                boolean lockedExternally,
                                                TrunkTier tier) {
        if (!(player.level() instanceof ServerLevel playerLevel)) {
            throw new IllegalStateException("Pocket operations require ServerLevel context");
        }
        TrunkRegistryData data = TrunkRegistryData.get(playerLevel);

        TrunkRecord existing = data.getCasePocket(caseId).orElse(null);
        if (existing != null) {
            if (existing.owner().equals(player.getUUID())) {
                data.updateRecord(existing.pocketId(), record ->
                        record.withArchetype(archetype).withTemplateId(templateId).withAccessMode(accessMode)
                              .withMuggleWorthy(muggleWorthy).withLockedExternally(lockedExternally));
                return data.getPocket(existing.pocketId()).orElse(existing);
            }
            return existing;
        }

        BlockPos spawn = computePocketSpawn(caseId);
        TrunkRecord created = TrunkRecord.create(player.getUUID(), archetype, templateId, spawn, tier.maxRadius());
        created = created.withAccessMode(accessMode);
        data.putRecord(created);
        data.bindCase(caseId, created.pocketId());
        return created;
    }

    public static void enterPocket(ServerPlayer player, TrunkRecord record) {
        if (!(player.level() instanceof ServerLevel sourceLevel)) return;

        TrunkRegistryData data = TrunkRegistryData.get(sourceLevel);
        data.saveReturnPosition(player.getUUID(), player.blockPosition(), sourceLevel.dimension());

        ServerLevel target = sourceLevel.getServer().getLevel(ModDimensions.EXTENSION_REALM);
        if (target == null) {
            PocketStatusS2CPayload.send(player, "Extension realm is missing from datapacks.");
            return;
        }
        if (!record.canAccess(player.getUUID())) {
            PocketStatusS2CPayload.send(player, "This case is bound to a private pocket.");
            return;
        }

        ensurePocketShell(target, record, data);

        // SCAMANDER: spawn on shed roof trapdoor (cy+5); others: floor trapdoor (cy).
        BlockPos spawn = record.archetype() == TrunkArchetype.SCAMANDER_SANCTUARY
                ? new BlockPos(record.spawnPos().getX(), record.spawnPos().getY() + 5, record.spawnPos().getZ())
                : record.spawnPos();
        TeleportTransition transition = new TeleportTransition(
                target, Vec3.atCenterOf(spawn), Vec3.ZERO,
                0.0f, 0.0f, Set.of(), entity -> {});
        player.teleport(transition);

        // Play trapdoor open immediately; ladder creak follows 2 ticks later via tick handler.
        target.playSound(null, spawn, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.0f, 1.0f);
        PocketDimensionEvents.scheduleLadderSound(player.getUUID(), 2);

        PocketStatusS2CPayload.send(player, "Entering " + record.pocketName());
    }

    public static void exitPocket(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel sourceLevel)) return;

        // Play closing sound while player is still in the realm.
        sourceLevel.playSound(null, player.blockPosition(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.0f, 1.0f);

        TrunkRegistryData data = TrunkRegistryData.get(sourceLevel);
        ServerLevel returnLevel = sourceLevel.getServer().overworld();
        BlockPos returnPos = BlockPos.ZERO;

        String dimStr = data.getReturnDimension(player.getUUID()).orElse(null);
        BlockPos savedPos = data.getReturnPosition(player.getUUID()).orElse(null);
        if (dimStr != null && savedPos != null) {
            try {
                ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimStr));
                ServerLevel candidate = sourceLevel.getServer().getLevel(dimKey);
                if (candidate != null) {
                    returnLevel = candidate;
                    returnPos = savedPos;
                }
            } catch (Exception ex) {
                LOGGER.warn("[WizardsAndBeasts] Failed to parse saved return dimension '{}' for {} — falling back to overworld spawn",
                        dimStr, player.getUUID(), ex);
            }
        }
        data.clearReturnData(player.getUUID());
        PocketDimensionEvents.cancelLadderSound(player.getUUID());

        BlockPos safe = findSafeSpawn(returnLevel, returnPos);
        TeleportTransition transition = new TeleportTransition(
                returnLevel, Vec3.atCenterOf(safe), Vec3.ZERO,
                player.getYRot(), player.getXRot(), Set.of(), entity -> {});
        player.teleport(transition);
        setInventoryLatchSecured(player, true);
        PocketStatusS2CPayload.send(player, "Returning from extension realm");
    }

    // Called by creature release interactions — wire to creature entity AI
    // when creature entities are implemented.
    public static void releaseToWorld(ServerPlayer player, TrunkRecord trunk) {
        if (!(player.level() instanceof ServerLevel sourceLevel)) return;

        TrunkRegistryData data = TrunkRegistryData.get(sourceLevel);
        ServerLevel returnLevel = sourceLevel.getServer().overworld();
        BlockPos returnPos = BlockPos.ZERO;

        String dimStr = data.getReturnDimension(player.getUUID()).orElse(null);
        BlockPos savedPos = data.getReturnPosition(player.getUUID()).orElse(null);
        if (dimStr != null && savedPos != null) {
            try {
                ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimStr));
                ServerLevel candidate = sourceLevel.getServer().getLevel(dimKey);
                if (candidate != null) {
                    returnLevel = candidate;
                    returnPos = savedPos;
                }
            } catch (Exception ex) {
                LOGGER.warn("[WizardsAndBeasts] Failed to parse saved return dimension '{}' for {} — falling back to overworld spawn",
                        dimStr, player.getUUID(), ex);
            }
        }
        data.clearReturnData(player.getUUID());
        PocketDimensionEvents.cancelLadderSound(player.getUUID());

        BlockPos safe = findSafeSpawn(returnLevel, returnPos);
        TeleportTransition transition = new TeleportTransition(
                returnLevel, Vec3.atCenterOf(safe), Vec3.ZERO,
                player.getYRot(), player.getXRot(), Set.of(), entity -> {});
        player.teleport(transition);

        // Spawn PORTAL particles at exit location to mark the release point.
        returnLevel.sendParticles(ParticleTypes.PORTAL,
                safe.getX() + 0.5, safe.getY() + 1.0, safe.getZ() + 0.5,
                30, 0.3, 0.5, 0.3, 0.1);
        returnLevel.playSound(null, safe, SoundEvents.ENDER_EYE_DEATH, SoundSource.NEUTRAL, 1.0f, 0.9f);

        PocketStatusS2CPayload.send(player, "Released from extension realm");
    }

    /** Sets latchSecured on all EnchantedTrunkItem stacks in the player's inventory. */
    static void setInventoryLatchSecured(ServerPlayer player, boolean secured) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack stack = player.getInventory().getItem(i);
            if (stack.has(at.koopro.wizardsandbeasts.registry.ModDataComponents.POCKET_CASE_ID.get())) {
                stack.set(at.koopro.wizardsandbeasts.registry.ModDataComponents.POCKET_LATCH_SECURED.get(), secured);
            }
        }
    }

    private static BlockPos computePocketSpawn(UUID pocketId) {
        int xIndex = Math.floorMod((int) pocketId.getMostSignificantBits(), 2048);
        int zIndex = Math.floorMod((int) pocketId.getLeastSignificantBits(), 2048);
        return new BlockPos(xIndex * GRID_SPACING, FLOOR_Y + 2, zIndex * GRID_SPACING);
    }

    private static BlockPos findSafeSpawn(ServerLevel level, BlockPos origin) {
        BlockPos.MutableBlockPos cursor = origin.mutable();
        for (int i = 0; i < 16; i++) {
            if (level.getBlockState(cursor).isAir() && level.getBlockState(cursor.above()).isAir()) {
                return cursor.immutable();
            }
            cursor.move(0, 1, 0);
        }
        return origin;
    }

    private static void ensurePocketShell(ServerLevel level, TrunkRecord record, TrunkRegistryData data) {
        if (data.isInitialized(record.pocketId())) return;

        BlockPos center = record.spawnPos();
        int radius = record.pocketRadius();
        TrunkArchetype archetype = record.archetype();
        boolean circular = archetype.isCircular();
        long seed = record.seed();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        // Floor — SCAMANDER_SANCTUARY uses zone-based habitat floors
        if (archetype == TrunkArchetype.SCAMANDER_SANCTUARY) {
            placeSanctuaryFloor(level, center, radius, seed);
        } else {
            BlockState floor = switch (archetype) {
                case FIELD_CAMP -> Blocks.COARSE_DIRT.defaultBlockState();
                case MINISTRY_STANDARD -> Blocks.POLISHED_ANDESITE.defaultBlockState();
                case SAFEHOUSE -> Blocks.SMOOTH_STONE.defaultBlockState();
                case ASTRONOMERS_RETREAT -> Blocks.DARK_PRISMARINE.defaultBlockState();
                default -> Blocks.SMOOTH_STONE.defaultBlockState();
            };
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (circular && x * x + z * z > radius * radius) continue;
                    cursor.set(center.getX() + x, center.getY() - 1, center.getZ() + z);
                    level.setBlock(cursor, floor, 3);
                }
            }
            // FIELD_CAMP: scatter occasional dirt and rooted dirt patches
            if (archetype == TrunkArchetype.FIELD_CAMP) {
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        if (x * x + z * z > radius * radius) continue;
                        long hash = seed ^ ((long) x * 0x9E3779B97F4A7C15L) ^ ((long) z * 0x6C62272E07BB0142L);
                        int r = (int) Math.floorMod(hash, 6);
                        if (r == 0) {
                            cursor.set(center.getX() + x, center.getY() - 1, center.getZ() + z);
                            level.setBlock(cursor, Blocks.DIRT.defaultBlockState(), 3);
                        } else if (r == 1) {
                            cursor.set(center.getX() + x, center.getY() - 1, center.getZ() + z);
                            level.setBlock(cursor, Blocks.ROOTED_DIRT.defaultBlockState(), 3);
                        }
                    }
                }
            }
        }

        // Perimeter wall from floor to ceiling
        int wallBottom = center.getY() - 1;
        int wallTop    = center.getY() + 8;
        BlockState wall = archetype.wallBlock();
        for (int x = -(radius + 1); x <= radius + 1; x++) {
            for (int z = -(radius + 1); z <= radius + 1; z++) {
                boolean isWall;
                if (circular) {
                    double dist = Math.sqrt((double) (x * x + z * z));
                    isWall = dist >= radius && dist <= radius + 1.5;
                } else {
                    isWall = (Math.abs(x) == radius + 1 || Math.abs(z) == radius + 1)
                            && Math.abs(x) <= radius + 1 && Math.abs(z) <= radius + 1;
                }
                if (!isWall) continue;
                for (int y = wallBottom; y <= wallTop; y++) {
                    cursor.set(center.getX() + x, y, center.getZ() + z);
                    level.setBlock(cursor, wall, 3);
                }
                // Bedrock cap seals the top of the wall
                cursor.set(center.getX() + x, wallTop + 1, center.getZ() + z);
                level.setBlock(cursor, Blocks.BEDROCK.defaultBlockState(), 3);
            }
        }

        // SCAMANDER_SANCTUARY: irregular lantern clusters per habitat zone spec
        if (archetype == TrunkArchetype.SCAMANDER_SANCTUARY) {
            placeSanctuaryCeilingLights(level, center, radius, wallTop);
        } else if (archetype == TrunkArchetype.MINISTRY_STANDARD) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    cursor.set(center.getX() + x, wallTop + 1, center.getZ() + z);
                    level.setBlock(cursor, Blocks.STONE_BRICKS.defaultBlockState(), 3);
                }
            }
            for (int x = -radius; x <= radius; x += 4) {
                for (int z = -radius; z <= radius; z += 4) {
                    cursor.set(center.getX() + x, wallTop + 2, center.getZ() + z);
                    level.setBlock(cursor, Blocks.CANDLE.defaultBlockState(), 3);
                }
            }
        } else {
            // Ceiling lights every 4 blocks (shape filter matches floor)
            for (int x = -radius; x <= radius; x += 4) {
                for (int z = -radius; z <= radius; z += 4) {
                    if (circular && x * x + z * z > radius * radius) continue;
                    cursor.set(center.getX() + x, wallTop, center.getZ() + z);
                    level.setBlock(cursor, archetype.ceilingBlock(), 3);
                }
            }
        }

        if (archetype.hasUtilityRing()) {
            placeUtilityRing(level, center, radius - 2);
        }

        if (archetype.hasCentralShed()) {
            placeSanctuaryShed(level, center);
            placeSanctuaryZoneFeatures(level, center, radius, seed, wallTop);
        }

        if (archetype == TrunkArchetype.SCAMANDER_SANCTUARY) {
            // Shed already placed its own exit trapdoor and south door; no generic markers.
        } else {
            placeEntryExitMarkers(level, center, wallTop);
        }

        applyBiome(level, record);
        data.markInitialized(record.pocketId());
    }

    /**
     * Places the iron trapdoor entry hatch at floor center, the iron trapdoor exit hatch
     * at ceiling center, and the spruce door exit one block north of center.
     * Called last so it overwrites any floor/ceiling blocks at center (e.g. the
     * SCAMANDER_SANCTUARY shed's spruce trapdoor or a ceiling light at offset 0,0).
     */
    private static void placeEntryExitMarkers(ServerLevel level, BlockPos center, int wallTop) {
        // Floor entry hatch — iron trapdoor flush with the floor, closed.
        level.setBlock(new BlockPos(center.getX(), center.getY() - 1, center.getZ()),
                Blocks.IRON_TRAPDOOR.defaultBlockState()
                        .setValue(BlockStateProperties.HALF, Half.BOTTOM)
                        .setValue(BlockStateProperties.OPEN, false)
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), 3);

        // Ceiling exit hatch — iron trapdoor on the underside of the ceiling, closed.
        // Right-clicking this triggers exit (handled by PocketDimensionEvents).
        level.setBlock(new BlockPos(center.getX(), wallTop, center.getZ()),
                Blocks.IRON_TRAPDOOR.defaultBlockState()
                        .setValue(BlockStateProperties.HALF, Half.TOP)
                        .setValue(BlockStateProperties.OPEN, false)
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), 3);

        // Exit door — spruce door one block north of center at floor level.
        // Right-clicking either half triggers exit (handled by PocketDimensionEvents).
        BlockPos doorLower = new BlockPos(center.getX(), center.getY() - 1, center.getZ() - 1);
        level.setBlock(doorLower,
                Blocks.SPRUCE_DOOR.defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
                        .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER)
                        .setValue(BlockStateProperties.DOOR_HINGE, DoorHingeSide.LEFT)
                        .setValue(BlockStateProperties.OPEN, false), 3);
        level.setBlock(doorLower.above(),
                Blocks.SPRUCE_DOOR.defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
                        .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER)
                        .setValue(BlockStateProperties.DOOR_HINGE, DoorHingeSide.LEFT)
                        .setValue(BlockStateProperties.OPEN, false), 3);
    }

    @SuppressWarnings("unchecked")
    public static void applyBiome(ServerLevel level, TrunkRecord record) {
        List<String> zones = record.biomeZones().isEmpty()
                ? record.archetype().defaultBiomeZones()
                : record.biomeZones();
        int n = zones.size();

        Holder<Biome>[] holders = new Holder[n];
        for (int i = 0; i < n; i++) {
            try {
                ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, Identifier.parse(zones.get(i)));
                holders[i] = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(key);
            } catch (Exception e) {
                holders[i] = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
            }
        }

        BlockPos center = record.spawnPos();
        int centerBX = center.getX() >> 2;
        int centerBZ = center.getZ() >> 2;

        BiomeResolver resolver = (x, y, z, sampler) -> {
            if (n == 1) return holders[0];
            double angle = Math.atan2(z - centerBZ, x - centerBX);
            int idx = (int) Math.floor((angle + Math.PI) / (2.0 * Math.PI) * n);
            idx = Math.max(0, Math.min(idx, n - 1));
            return holders[idx];
        };

        int radius = record.pocketRadius();
        int chunkMinX = (center.getX() - radius) >> 4;
        int chunkMaxX = (center.getX() + radius) >> 4;
        int chunkMinZ = (center.getZ() - radius) >> 4;
        int chunkMaxZ = (center.getZ() + radius) >> 4;

        for (int cx = chunkMinX; cx <= chunkMaxX; cx++) {
            for (int cz = chunkMinZ; cz <= chunkMaxZ; cz++) {
                LevelChunk chunk = level.getChunk(cx, cz);
                chunk.fillBiomesFromNoise(resolver, DUMMY_SAMPLER);
                chunk.markUnsaved();
            }
        }
    }

    private static void placeUtilityRing(ServerLevel level, BlockPos center, int radius) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                cursor.set(center.getX() + dx, center.getY(), center.getZ() + dz);
                if (level.getBlockState(cursor).isAir()) {
                    level.setBlock(cursor, Blocks.BOOKSHELF.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void placeSanctuaryShed(ServerLevel level, BlockPos center) {
        int cx = center.getX();
        int cy = center.getY(); // spawn Y — floor blocks are at cy-1
        int cz = center.getZ();

        // Dark Oak Planks floor inside shed (7 wide × 5 deep, overwrites zone floor)
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                level.setBlock(new BlockPos(cx + dx, cy - 1, cz + dz),
                        Blocks.DARK_OAK_PLANKS.defaultBlockState(), 3);
            }
        }

        // Oak Plank walls, 4 blocks tall (y=cy to cy+3), 7×5 footprint
        // South face (dz=+2) has 1-wide 2-tall door opening at dx=0, y=cy and cy+1
        for (int y = cy; y <= cy + 3; y++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) < 3 && Math.abs(dz) < 2) continue; // interior air
                    if (dz == 2 && dx == 0 && y <= cy + 1) continue;    // door opening
                    level.setBlock(new BlockPos(cx + dx, y, cz + dz),
                            Blocks.OAK_PLANKS.defaultBlockState(), 3);
                }
            }
        }

        // Spruce Stairs pitched roof — pitch runs N-S, ridge along X axis
        // dz=±2: lower slope at y=cy+4; dz=±1: upper slope at y=cy+5; dz=0: oak slab peak at y=cy+5
        for (int dx = -3; dx <= 3; dx++) {
            level.setBlock(new BlockPos(cx + dx, cy + 4, cz - 2),
                    Blocks.SPRUCE_STAIRS.defaultBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
                            .setValue(BlockStateProperties.HALF, Half.BOTTOM), 3);
            level.setBlock(new BlockPos(cx + dx, cy + 5, cz - 1),
                    Blocks.SPRUCE_STAIRS.defaultBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
                            .setValue(BlockStateProperties.HALF, Half.BOTTOM), 3);
            level.setBlock(new BlockPos(cx + dx, cy + 4, cz + 2),
                    Blocks.SPRUCE_STAIRS.defaultBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                            .setValue(BlockStateProperties.HALF, Half.BOTTOM), 3);
            level.setBlock(new BlockPos(cx + dx, cy + 5, cz + 1),
                    Blocks.SPRUCE_STAIRS.defaultBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                            .setValue(BlockStateProperties.HALF, Half.BOTTOM), 3);
            // Peak slab (skip center — iron trapdoor goes there)
            if (dx != 0) {
                level.setBlock(new BlockPos(cx + dx, cy + 5, cz),
                        Blocks.OAK_SLAB.defaultBlockState()
                                .setValue(BlockStateProperties.SLAB_TYPE,
                                        net.minecraft.world.level.block.state.properties.SlabType.TOP), 3);
            }
        }
        // Gable-end infill at dx=±3 for the middle pitch rows
        for (int gDz = -1; gDz <= 1; gDz++) {
            level.setBlock(new BlockPos(cx - 3, cy + 4, cz + gDz), Blocks.OAK_PLANKS.defaultBlockState(), 3);
            level.setBlock(new BlockPos(cx + 3, cy + 4, cz + gDz), Blocks.OAK_PLANKS.defaultBlockState(), 3);
            level.setBlock(new BlockPos(cx - 3, cy + 5, cz + gDz), Blocks.OAK_PLANKS.defaultBlockState(), 3);
            level.setBlock(new BlockPos(cx + 3, cy + 5, cz + gDz), Blocks.OAK_PLANKS.defaultBlockState(), 3);
        }

        // Iron Trapdoor at roof peak centre — entry/exit marker
        level.setBlock(new BlockPos(cx, cy + 5, cz),
                Blocks.IRON_TRAPDOOR.defaultBlockState()
                        .setValue(BlockStateProperties.HALF, Half.BOTTOM)
                        .setValue(BlockStateProperties.OPEN, false)
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), 3);

        // Ladder descending from just below trapdoor down to shed floor
        for (int y = cy; y <= cy + 4; y++) {
            level.setBlock(new BlockPos(cx, y, cz),
                    Blocks.LADDER.defaultBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), 3);
        }

        // Spruce Door on south face — exit trigger (lower at cy, upper at cy+1)
        level.setBlock(new BlockPos(cx, cy, cz + 2),
                Blocks.SPRUCE_DOOR.defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
                        .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER)
                        .setValue(BlockStateProperties.DOOR_HINGE, DoorHingeSide.LEFT)
                        .setValue(BlockStateProperties.OPEN, false), 3);
        level.setBlock(new BlockPos(cx, cy + 1, cz + 2),
                Blocks.SPRUCE_DOOR.defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
                        .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER)
                        .setValue(BlockStateProperties.DOOR_HINGE, DoorHingeSide.LEFT)
                        .setValue(BlockStateProperties.OPEN, false), 3);

        // Bookshelves × 3 along north interior wall
        level.setBlock(new BlockPos(cx - 1, cy, cz - 1), Blocks.BOOKSHELF.defaultBlockState(), 3);
        level.setBlock(new BlockPos(cx,     cy, cz - 1), Blocks.BOOKSHELF.defaultBlockState(), 3);
        level.setBlock(new BlockPos(cx + 1, cy, cz - 1), Blocks.BOOKSHELF.defaultBlockState(), 3);

        // Crafting Table (NW interior corner)
        level.setBlock(new BlockPos(cx - 2, cy, cz - 1), Blocks.CRAFTING_TABLE.defaultBlockState(), 3);

        // Chest (NE interior corner)
        level.setBlock(new BlockPos(cx + 2, cy, cz - 1), Blocks.CHEST.defaultBlockState(), 3);

        // Barrel × 2 (west interior wall)
        level.setBlock(new BlockPos(cx - 2, cy, cz),     Blocks.BARREL.defaultBlockState(), 3);
        level.setBlock(new BlockPos(cx - 2, cy, cz + 1), Blocks.BARREL.defaultBlockState(), 3);

        // Lectern facing south door (represents typewriter/writing desk)
        level.setBlock(new BlockPos(cx + 1, cy, cz + 1),
                Blocks.LECTERN.defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), 3);

        // Flower Pot with Fern (east interior)
        level.setBlock(new BlockPos(cx + 2, cy, cz), Blocks.POTTED_FERN.defaultBlockState(), 3);

        // Hanging Lantern at interior ceiling (cy+3) — warm light
        level.setBlock(new BlockPos(cx, cy + 3, cz - 1),
                Blocks.LANTERN.defaultBlockState()
                        .setValue(BlockStateProperties.HANGING, true), 3);
    }

    // -------------------------------------------------------------------------
    // SCAMANDER_SANCTUARY — habitat zones
    // -------------------------------------------------------------------------

    /** Returns habitat zone 1-8 for a given floor offset, or 0 for the shed footprint. */
    private static int getSanctuaryZone(int dx, int dz, int thirdR) {
        if (dx >= -3 && dx <= 3 && dz >= -2 && dz <= 2) return 0; // shed footprint
        if (dz <= -3) {
            if (dx < -thirdR) return 3;  // NW: Snowy
            if (dx > thirdR)  return 2;  // NE: Bamboo
            return 1;                     // N center: Desert
        }
        if (dz >= 3) {
            if (dx < -thirdR) return 7;  // SW: Mooncalf
            if (dx > thirdR)  return 5;  // SE: Erumpent
            return 6;                     // S: Rocky/Graphorn
        }
        if (dx > 3) return 4;            // E: Rainforest
        if (dx < -3) return 8;           // W: Grindylow
        return 0;
    }

    private static void placeSanctuaryFloor(ServerLevel level, BlockPos center, int radius, long seed) {
        int cx = center.getX();
        int cy = center.getY();
        int cz = center.getZ();
        int thirdR = Math.max(4, radius / 3);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int zone = getSanctuaryZone(dx, dz, thirdR);
                if (zone == 0) continue; // shed footprint handled by placeSanctuaryShed
                long hash = seed ^ ((long) dx * 0x9E3779B97F4A7C15L) ^ ((long) dz * 0x6C62272E07BB0142L);
                int r = (int) Math.floorMod(hash, 6);
                BlockState floor = switch (zone) {
                    case 1 -> r < 2 ? Blocks.SAND.defaultBlockState()
                                    : r < 5 ? Blocks.RED_SAND.defaultBlockState()
                                            : Blocks.SANDSTONE.defaultBlockState();
                    case 2 -> r < 2 ? Blocks.DIRT.defaultBlockState()
                                    : Blocks.BAMBOO_MOSAIC.defaultBlockState();
                    case 3 -> r < 2 ? Blocks.PACKED_ICE.defaultBlockState()
                                    : Blocks.SNOW_BLOCK.defaultBlockState();
                    case 4 -> Blocks.MOSS_BLOCK.defaultBlockState();
                    case 5 -> Blocks.COARSE_DIRT.defaultBlockState();
                    case 6 -> r < 3 ? Blocks.STONE.defaultBlockState()
                                    : Blocks.GRAVEL.defaultBlockState();
                    case 7 -> r < 2 ? Blocks.DIRT.defaultBlockState()
                                    : Blocks.GRASS_BLOCK.defaultBlockState();
                    case 8 -> Blocks.STONE_BRICKS.defaultBlockState();
                    default -> Blocks.DIRT.defaultBlockState();
                };
                cursor.set(cx + dx, cy - 1, cz + dz);
                level.setBlock(cursor, floor, 3);
            }
        }
    }

    private static void placeSanctuaryCeilingLights(ServerLevel level, BlockPos center, int radius, int wallTop) {
        int cx = center.getX();
        int cz = center.getZ();
        int thirdR = Math.max(4, radius / 3);
        int sideOffset = thirdR + (radius - thirdR) / 2;

        // Dense cluster above shed (5 lanterns)
        for (int[] off : new int[][]{{0,0},{-1,0},{1,0},{0,-1},{0,1}}) {
            level.setBlock(new BlockPos(cx + off[0], wallTop, cz + off[1]),
                    Blocks.LANTERN.defaultBlockState(), 3);
        }
        // Sparse pair above bamboo zone (NE)
        level.setBlock(new BlockPos(cx + sideOffset,     wallTop, cz - sideOffset),
                Blocks.LANTERN.defaultBlockState(), 3);
        level.setBlock(new BlockPos(cx + sideOffset + 2, wallTop, cz - sideOffset),
                Blocks.LANTERN.defaultBlockState(), 3);
        // Single Soul Lantern above water zone (W) — cooler tone
        level.setBlock(new BlockPos(cx - sideOffset, wallTop, cz),
                Blocks.SOUL_LANTERN.defaultBlockState(), 3);
        // Single lantern above rainforest zone (E)
        level.setBlock(new BlockPos(cx + sideOffset, wallTop, cz),
                Blocks.LANTERN.defaultBlockState(), 3);
        // NO lanterns above snowy zone (NW) or desert zone (N)
    }

    private static void placeSanctuaryZoneFeatures(ServerLevel level, BlockPos center,
                                                    int radius, long seed, int wallTop) {
        int cx = center.getX();
        int cy = center.getY();
        int cz = center.getZ();
        int thirdR = Math.max(4, radius / 3);
        int sideOffset = thirdR + (radius - thirdR) / 2;

        // ── Zone 1: Thunderbird Desert — rock formation in far north corner ──
        int rockX = cx + thirdR / 2;
        int rockZ = cz - radius + 3;
        level.setBlock(new BlockPos(rockX,     cy,     rockZ),     Blocks.SANDSTONE.defaultBlockState(), 3);
        level.setBlock(new BlockPos(rockX + 1, cy,     rockZ),     Blocks.RED_SANDSTONE.defaultBlockState(), 3);
        level.setBlock(new BlockPos(rockX,     cy,     rockZ + 1), Blocks.RED_TERRACOTTA.defaultBlockState(), 3);
        level.setBlock(new BlockPos(rockX - 1, cy,     rockZ),     Blocks.TERRACOTTA.defaultBlockState(), 3);
        level.setBlock(new BlockPos(rockX,     cy + 1, rockZ),     Blocks.SANDSTONE.defaultBlockState(), 3);
        level.setBlock(new BlockPos(rockX + 1, cy + 1, rockZ),     Blocks.SANDSTONE.defaultBlockState(), 3);
        level.setBlock(new BlockPos(rockX,     cy + 1, rockZ + 1), Blocks.RED_SANDSTONE.defaultBlockState(), 3);
        level.setBlock(new BlockPos(rockX,     cy + 2, rockZ),     Blocks.CUT_SANDSTONE.defaultBlockState(), 3);
        level.setBlock(new BlockPos(rockX,     cy + 2, rockZ + 1), Blocks.SANDSTONE.defaultBlockState(), 3);
        level.setBlock(new BlockPos(rockX,     cy + 3, rockZ),     Blocks.CHISELED_SANDSTONE.defaultBlockState(), 3);
        level.setBlock(new BlockPos(rockX + 1, cy + 3, rockZ),     Blocks.SANDSTONE.defaultBlockState(), 3);
        level.setBlock(new BlockPos(rockX,     cy + 4, rockZ),     Blocks.SANDSTONE.defaultBlockState(), 3);

        // ── Zone 2: Bamboo Forest — bamboo plants, barrel, demiguise post, floating platform ──
        int bamX = cx + sideOffset;
        int bamZ = cz - sideOffset;
        int[][] bambooOffsets = {{0,0},{2,1},{-1,2},{3,-1},{1,3},{-2,1}};
        for (int[] off : bambooOffsets) {
            int bx = bamX + off[0];
            int bz = bamZ + off[1];
            long hash = seed ^ ((long) bx * 0xDEADBEEFL) ^ ((long) bz * 0xCAFEBABEL);
            int height = 2 + (int) Math.floorMod(hash, 3);
            for (int h = 0; h < height; h++) {
                level.setBlock(new BlockPos(bx, cy + h, bz), Blocks.BAMBOO.defaultBlockState(), 3);
            }
        }
        level.setBlock(new BlockPos(bamX - 2, cy, bamZ + 2), Blocks.BARREL.defaultBlockState(), 3);
        // Demiguise post: oak fence + log + stone button on top
        level.setBlock(new BlockPos(bamX + 1, cy,     bamZ - 2), Blocks.OAK_FENCE.defaultBlockState(), 3);
        level.setBlock(new BlockPos(bamX + 1, cy + 1, bamZ - 2), Blocks.OAK_LOG.defaultBlockState(), 3);
        level.setBlock(new BlockPos(bamX + 1, cy + 2, bamZ - 2),
                Blocks.STONE_BUTTON.defaultBlockState()
                        .setValue(BlockStateProperties.ATTACH_FACE,
                                net.minecraft.world.level.block.state.properties.AttachFace.FLOOR), 3);

        // Floating platform: 4×4 Rooted Dirt, wallTop-4 height
        int platY = wallTop - 4;
        for (int pdx = 0; pdx < 4; pdx++) {
            for (int pdz = 0; pdz < 4; pdz++) {
                level.setBlock(new BlockPos(bamX + pdx, platY, bamZ + pdz),
                        Blocks.ROOTED_DIRT.defaultBlockState(), 3);
            }
        }
        // Oak log + leaves on platform surface
        level.setBlock(new BlockPos(bamX + 1, platY + 1, bamZ + 1), Blocks.OAK_LOG.defaultBlockState(), 3);
        level.setBlock(new BlockPos(bamX + 2, platY + 1, bamZ + 1),
                Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true), 3);
        level.setBlock(new BlockPos(bamX + 1, platY + 1, bamZ + 2),
                Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true), 3);
        level.setBlock(new BlockPos(bamX + 2, platY + 2, bamZ + 2),
                Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true), 3);
        // Shroomlights on underside (warm ambient light)
        level.setBlock(new BlockPos(bamX,     platY - 1, bamZ),     Blocks.SHROOMLIGHT.defaultBlockState(), 3);
        level.setBlock(new BlockPos(bamX + 2, platY - 1, bamZ + 3), Blocks.SHROOMLIGHT.defaultBlockState(), 3);
        level.setBlock(new BlockPos(bamX + 3, platY - 1, bamZ + 1), Blocks.SHROOMLIGHT.defaultBlockState(), 3);
        // Iron bars hanging from underside
        level.setBlock(new BlockPos(bamX + 1, platY - 1, bamZ),     Blocks.IRON_BARS.defaultBlockState(), 3);
        level.setBlock(new BlockPos(bamX + 3, platY - 1, bamZ + 2), Blocks.IRON_BARS.defaultBlockState(), 3);
        // Dirt column with ladder (doesn't touch floor — 2-block gap)
        int colX = bamX + 1;
        int colZ = bamZ + 3;
        for (int y = cy + 2; y < platY; y++) {
            level.setBlock(new BlockPos(colX, y, colZ), Blocks.DIRT.defaultBlockState(), 3);
        }
        for (int y = cy + 2; y < platY; y++) {
            level.setBlock(new BlockPos(colX, y, colZ + 1),
                    Blocks.LADDER.defaultBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), 3);
        }

        // ── Zone 3: Snowy Habitat — white wool curtain walls, gap facing shed ──
        int snowEastBound = cx - thirdR;
        int gapDx = cx - thirdR - 2; // 2 blocks into zone from east boundary (closest to path)
        // South boundary (dz = cz-3, faces shed): dx from -(radius-1) to -(thirdR+1)
        for (int x = cx - radius + 1; x < snowEastBound; x++) {
            if (x == gapDx) continue; // entrance gap
            level.setBlock(new BlockPos(x, cy,     cz - 3), Blocks.WHITE_WOOL.defaultBlockState(), 3);
            level.setBlock(new BlockPos(x, cy + 1, cz - 3), Blocks.WHITE_WOOL.defaultBlockState(), 3);
        }
        // East boundary (dx = snowEastBound, faces zone 1): dz from -(radius-1) to -4
        for (int z = cz - radius + 1; z <= cz - 4; z++) {
            level.setBlock(new BlockPos(snowEastBound, cy,     z), Blocks.WHITE_WOOL.defaultBlockState(), 3);
            level.setBlock(new BlockPos(snowEastBound, cy + 1, z), Blocks.WHITE_WOOL.defaultBlockState(), 3);
        }

        // ── Zone 4: Rainforest — moss floor + jungle leaves + stone cluster (Nundu) ──
        int rfX = cx + sideOffset;
        int rfZ = cz;
        int[][] leafOffsets = {{0,1},{0,-1},{1,0},{1,1},{-1,-1}};
        for (int[] off : leafOffsets) {
            long h = seed ^ ((long)(rfX+off[0]) * 0x5A4A7C15L) ^ ((long)(rfZ+off[1]) * 0x2272E07BL);
            if (Math.floorMod(h, 3) != 0) {
                level.setBlock(new BlockPos(rfX + off[0], cy, rfZ + off[1]),
                        Blocks.JUNGLE_LEAVES.defaultBlockState()
                                .setValue(BlockStateProperties.PERSISTENT, true), 3);
            }
        }
        // Oak leaves cluster (2-3 tall)
        level.setBlock(new BlockPos(rfX - 1, cy,     rfZ - 2),
                Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true), 3);
        level.setBlock(new BlockPos(rfX - 1, cy + 1, rfZ - 2),
                Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true), 3);
        level.setBlock(new BlockPos(rfX - 1, cy + 2, rfZ - 2),
                Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true), 3);
        // Nundu stone outcrop
        level.setBlock(new BlockPos(rfX, cy,     rfZ + 3), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(new BlockPos(rfX + 1, cy, rfZ + 3), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(new BlockPos(rfX, cy + 1, rfZ + 3), Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), 3);

        // ── Zone 5: Erumpent Enclosure — dead bushes + broken stone brick wall ──
        int eruX = cx + sideOffset;
        int eruZ = cz + sideOffset;
        int[][] bushOffsets = {{0,0},{2,-1},{-1,2},{1,-2},{-2,1}};
        for (int[] off : bushOffsets) {
            level.setBlock(new BlockPos(eruX + off[0], cy, eruZ + off[1]),
                    Blocks.DEAD_BUSH.defaultBlockState(), 3);
        }
        // Broken wall section on outer (east) boundary
        for (int w = 0; w < 3; w++) {
            level.setBlock(new BlockPos(cx + radius - 1, cy,     eruZ + w - 1),
                    Blocks.STONE_BRICK_WALL.defaultBlockState(), 3);
            level.setBlock(new BlockPos(cx + radius - 1, cy + 1, eruZ + w - 1),
                    Blocks.STONE_BRICK_WALL.defaultBlockState(), 3);
        }

        // ── Zone 7: Mooncalf Habitat — dirt mound + grass top + oak sapling ──
        int mcX = cx - sideOffset;
        int mcZ = cz + sideOffset;
        for (int ddx = -1; ddx <= 1; ddx++) {
            for (int ddz = -1; ddz <= 1; ddz++) {
                level.setBlock(new BlockPos(mcX + ddx, cy,     mcZ + ddz), Blocks.DIRT.defaultBlockState(), 3);
                level.setBlock(new BlockPos(mcX + ddx, cy + 1, mcZ + ddz), Blocks.DIRT.defaultBlockState(), 3);
            }
        }
        level.setBlock(new BlockPos(mcX, cy + 2, mcZ), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        level.setBlock(new BlockPos(mcX, cy + 3, mcZ), Blocks.OAK_SAPLING.defaultBlockState(), 3);

        // ── Zone 8: Grindylow Water Square — 5×5 pool edged with stone bricks ──
        int poolX = cx - sideOffset;
        int poolZ = cz;
        // Water in 5×5 interior
        for (int pdx = -2; pdx <= 2; pdx++) {
            for (int pdz = -2; pdz <= 2; pdz++) {
                level.setBlock(new BlockPos(poolX + pdx, cy - 1, poolZ + pdz),
                        Blocks.WATER.defaultBlockState(), 3);
                level.setBlock(new BlockPos(poolX + pdx, cy, poolZ + pdz),
                        Blocks.AIR.defaultBlockState(), 3);
            }
        }
        // Stone brick containment walls (1-block tall rim around the 5×5)
        for (int pdx = -3; pdx <= 3; pdx++) {
            level.setBlock(new BlockPos(poolX + pdx, cy - 1, poolZ - 3),
                    Blocks.STONE_BRICKS.defaultBlockState(), 3);
            level.setBlock(new BlockPos(poolX + pdx, cy,     poolZ - 3),
                    Blocks.STONE_BRICKS.defaultBlockState(), 3);
            level.setBlock(new BlockPos(poolX + pdx, cy - 1, poolZ + 3),
                    Blocks.STONE_BRICKS.defaultBlockState(), 3);
            level.setBlock(new BlockPos(poolX + pdx, cy,     poolZ + 3),
                    Blocks.STONE_BRICKS.defaultBlockState(), 3);
        }
        for (int pdz = -2; pdz <= 2; pdz++) {
            level.setBlock(new BlockPos(poolX - 3, cy - 1, poolZ + pdz),
                    Blocks.STONE_BRICKS.defaultBlockState(), 3);
            level.setBlock(new BlockPos(poolX - 3, cy,     poolZ + pdz),
                    Blocks.STONE_BRICKS.defaultBlockState(), 3);
            level.setBlock(new BlockPos(poolX + 3, cy - 1, poolZ + pdz),
                    Blocks.STONE_BRICKS.defaultBlockState(), 3);
            level.setBlock(new BlockPos(poolX + 3, cy,     poolZ + pdz),
                    Blocks.STONE_BRICKS.defaultBlockState(), 3);
        }
    }
}
