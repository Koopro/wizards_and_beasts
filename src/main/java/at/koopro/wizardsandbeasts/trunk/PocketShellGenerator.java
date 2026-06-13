package at.koopro.wizardsandbeasts.trunk;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
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
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * One-time worldgen for pocket dimension shells: floors, perimeter walls, ceilings,
 * lighting, archetype furnishings (utility ring, sanctuary shed and habitat zones),
 * entry/exit markers and biome painting.
 *
 * <p>Extracted from {@link ExtensionCharmService}, which retains the teleport and
 * lifecycle logic and is the only intended caller besides biome re-application.
 */
final class PocketShellGenerator {

    private static final Climate.Sampler DUMMY_SAMPLER = new Climate.Sampler(
            DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(),
            DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(),
            java.util.List.of());

    private PocketShellGenerator() {}

    static void ensurePocketShell(@NonNull ServerLevel level, @NonNull TrunkRecord record,
                                  @NonNull TrunkRegistryData data) {
        if (data.isInitialized(record.pocketId())) return;

        BlockPos center = record.spawnPos();
        int radius = record.pocketRadius();
        TrunkArchetype archetype = record.archetype();
        boolean circular = archetype.isCircular();
        long seed = record.seed();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        // Floor — SCAMANDER_SANCTUARY uses zone-based habitat floors
        if (archetype == TrunkArchetype.SCAMANDER_SANCTUARY) {
            PocketSanctuaryFurnisher.placeSanctuaryFloor(level, center, radius, seed);
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
            PocketSanctuaryFurnisher.placeSanctuaryCeilingLights(level, center, radius, wallTop);
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
            PocketSanctuaryFurnisher.placeSanctuaryShed(level, center);
            PocketSanctuaryFurnisher.placeSanctuaryZoneFeatures(level, center, radius, seed, wallTop);
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
    static void applyBiome(@NonNull ServerLevel level, @NonNull TrunkRecord record) {
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
}