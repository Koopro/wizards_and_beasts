package at.koopro.wizardsandbeasts.azkaban.structure;

import at.koopro.wizardsandbeasts.azkaban.biome.AzkabanBiomes;
import at.koopro.wizardsandbeasts.azkaban.data.AzkabanWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import org.jspecify.annotations.NonNull;

/**
 * Single structural piece that generates the entire Azkaban Fortress island and 10-floor tower.
 *
 * <p>All block writes go through {@link #placeBlock} and {@link #generateBox}, which both
 * clip to the {@code writableArea} BoundingBox parameter before calling {@code level.setBlock}.
 * This means the piece is safe across chunk boundaries without any extra guards.
 *
 * <p>Layout (local Y, island surface = Y 3):
 * <ul>
 *   <li>Y 0–3   — island base (deepslate fill)
 *   <li>Y 3      — island surface (cobbled deepslate / gravel)
 *   <li>Y 3–88  — fortress tower (14×14 footprint centred on island)
 *   <li>Y 89–90 — roof parapet
 * </ul>
 */
public final class AzkabanFortressPiece extends StructurePiece {

    // Island is 36×36 blocks
    static final int ISLAND = 36;
    // Fortress footprint within island (centred)
    private static final int FORT_X0 = 11;
    private static final int FORT_Z0 = 11;
    private static final int FORT_W  = 14;
    private static final int FORT_H  = 86; // floors 1–10 + roof
    // Floor Y offsets (local Y from piece origin, island surface = Y 3)
    private static final int[] FLOOR_Y = {3, 10, 17, 24, 31, 38, 45, 52, 59, 66, 73, 80};
    private static final int SEA_Y = 63; // world sea level used for bounding box

    // Biome override radius (blocks)
    private static final int BIOME_RADIUS = 64;

    public AzkabanFortressPiece(StructurePieceType type, BlockPos origin) {
        super(type, 0, makeBoundingBox(origin));
        AzkabanStructures.cachedFortressCenter = origin.offset(ISLAND / 2, 3, ISLAND / 2);
    }

    /** Deserialization constructor. */
    public static AzkabanFortressPiece load(StructurePieceSerializationContext ctx, CompoundTag tag) {
        return new AzkabanFortressPiece(
                AzkabanStructures.AZKABAN_FORTRESS_PIECE.get(),
                BlockPos.of(tag.getLongOr("Origin", 0L)));
    }

    private static BoundingBox makeBoundingBox(BlockPos origin) {
        return new BoundingBox(
                origin.getX(),              SEA_Y - 10,              origin.getZ(),
                origin.getX() + ISLAND,     SEA_Y + FORT_H + 4,      origin.getZ() + ISLAND);
    }

    @Override
    protected void addAdditionalSaveData(
            @NonNull StructurePieceSerializationContext ctx, @NonNull CompoundTag tag) {
        tag.putLong("Origin", BlockPos.containing(
                boundingBox.minX(), boundingBox.minY() + 10, boundingBox.minZ()).asLong());
    }

    // -------------------------------------------------------------------------
    // postProcess — called once per intersecting chunk by the generation pipeline
    // -------------------------------------------------------------------------

    @Override
    public void postProcess(
            @NonNull WorldGenLevel level,
            @NonNull StructureManager structureManager,
            @NonNull ChunkGenerator generator,
            @NonNull RandomSource random,
            @NonNull BoundingBox writableArea,
            @NonNull ChunkPos chunkPos,
            @NonNull BlockPos pivot) {

        generateIsland(level, random, writableArea);
        generateFortress(level, random, writableArea);
        generateDock(level, writableArea);
        overrideBiome(level, chunkPos);
        persistCenter(level);
    }

    // -------------------------------------------------------------------------
    // Biome override — write azkaban_sea into all sections within BIOME_RADIUS
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private void overrideBiome(WorldGenLevel level, ChunkPos chunkPos) {
        BlockPos center = AzkabanStructures.cachedFortressCenter;
        if (center == null) return;

        Holder<Biome> azkabanSea;
        try {
            azkabanSea = level.registryAccess()
                    .lookupOrThrow(Registries.BIOME)
                    .getOrThrow(AzkabanBiomes.AZKABAN_SEA);
        } catch (Exception e) {
            return; // biome not registered — skip silently
        }

        ChunkAccess chunk = level.getChunk(chunkPos.x, chunkPos.z);
        LevelChunkSection[] sections = chunk.getSections();

        for (int si = 0; si < sections.length; si++) {
            LevelChunkSection section = sections[si];
            if (section == null) continue;
            PalettedContainerRO<Holder<Biome>> biomesRO = section.getBiomes();
            if (!(biomesRO instanceof PalettedContainer<?> raw)) continue;
            PalettedContainer<Holder<Biome>> biomes = (PalettedContainer<Holder<Biome>>) raw;

            // Bottom block Y of this section
            int sectionBlockY = level.getSectionYFromSectionIndex(si) << 4;

            for (int bx = 0; bx < 4; bx++) {
                for (int bz = 0; bz < 4; bz++) {
                    // Use the centre of the 4-block quart for distance check
                    double dx = (chunkPos.getMinBlockX() + bx * 4 + 2) - center.getX();
                    double dz = (chunkPos.getMinBlockZ() + bz * 4 + 2) - center.getZ();
                    if (dx * dx + dz * dz <= (double) BIOME_RADIUS * BIOME_RADIUS) {
                        for (int by = 0; by < 4; by++) {
                            biomes.set(bx, by, bz, azkabanSea);
                        }
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Persist fortress center to SavedData (requires ServerLevel)
    // -------------------------------------------------------------------------

    private void persistCenter(WorldGenLevel level) {
        BlockPos center = AzkabanStructures.cachedFortressCenter;
        if (center == null) return;
        if (level instanceof ServerLevelAccessor sla && sla.getLevel() instanceof ServerLevel sl) {
            AzkabanWorldData data = AzkabanWorldData.get(sl);
            if (!data.isGenerated()) {
                data.setFortressCenter(center);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Island
    // -------------------------------------------------------------------------

    private void generateIsland(WorldGenLevel level, RandomSource random, BoundingBox writableArea) {
        for (int x = 0; x < ISLAND; x++) {
            for (int z = 0; z < ISLAND; z++) {
                for (int y = -10; y < 3; y++) {
                    placeBlock(level, Blocks.DEEPSLATE.defaultBlockState(), x, y, z, writableArea);
                }
                BlockState surface = (random.nextInt(8) == 0)
                        ? Blocks.GRAVEL.defaultBlockState()
                        : Blocks.COBBLED_DEEPSLATE.defaultBlockState();
                placeBlock(level, surface, x, 3, z, writableArea);
                // Clear water above island surface
                for (int y = 4; y <= 6; y++) {
                    placeBlock(level, Blocks.AIR.defaultBlockState(), x, y, z, writableArea);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Dock (south face, 6-block pier)
    // -------------------------------------------------------------------------

    private void generateDock(WorldGenLevel level, BoundingBox writableArea) {
        int dockCenterX = ISLAND / 2;
        int dockY = 3;
        for (int dz = ISLAND; dz < ISLAND + 6; dz++) {
            for (int dx = dockCenterX - 2; dx <= dockCenterX + 2; dx++) {
                placeBlock(level, Blocks.COBBLESTONE.defaultBlockState(), dx, dockY - 1, dz, writableArea);
                placeBlock(level, Blocks.COBBLESTONE.defaultBlockState(), dx, dockY, dz, writableArea);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Fortress tower
    // -------------------------------------------------------------------------

    private void generateFortress(WorldGenLevel level, RandomSource rng, BoundingBox writableArea) {
        int x0 = FORT_X0, z0 = FORT_Z0;
        int x1 = x0 + FORT_W - 1, z1 = z0 + FORT_W - 1;

        // Solid deepslate foundation
        generateBox(level, writableArea, x0, 3, z0, x1, 5, z1,
                Blocks.DEEPSLATE_BRICKS.defaultBlockState(),
                Blocks.DEEPSLATE_BRICKS.defaultBlockState(), false);

        // 10 floors
        for (int i = 1; i <= 10; i++) {
            generateFloor(level, rng, writableArea, i, FLOOR_Y[i], FLOOR_Y[i + 1] - 1, x0, z0, x1, z1);
        }

        // Roof
        generateRoof(level, writableArea, x0, FLOOR_Y[11], z0, x1, z1);

        // Entry door on south face (floor 1)
        placeBlock(level, Blocks.IRON_DOOR.defaultBlockState(), x0 + FORT_W / 2, FLOOR_Y[1],     z1, writableArea);
        placeBlock(level, Blocks.IRON_DOOR.defaultBlockState(), x0 + FORT_W / 2, FLOOR_Y[1] + 1, z1, writableArea);

        // Weathering pass
        for (int lx = x0; lx <= x1; lx++) {
            for (int lz = z0; lz <= z1; lz++) {
                for (int ly = 3; ly <= FLOOR_Y[11]; ly++) {
                    if (rng.nextInt(12) == 0) {
                        placeBlock(level, Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState(), lx, ly, lz, writableArea);
                    }
                }
            }
        }
    }

    private void generateFloor(WorldGenLevel level, RandomSource rng, BoundingBox writableArea,
                                int floorNum, int yBottom, int yTop,
                                int x0, int z0, int x1, int z1) {
        BlockState wall  = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        BlockState floor = Blocks.COBBLED_DEEPSLATE.defaultBlockState();
        BlockState air   = Blocks.AIR.defaultBlockState();
        BlockState bars  = Blocks.IRON_BARS.defaultBlockState();

        // Outer shell (hollow box)
        generateBox(level, writableArea, x0, yBottom, z0, x1, yTop, z1, wall, air, false);

        // Floor plate + ceiling plate
        generateBox(level, writableArea, x0, yBottom, z0, x1, yBottom, z1, floor, floor, false);
        generateBox(level, writableArea, x0, yTop,    z0, x1, yTop,    z1, floor, floor, false);

        // Slit windows on upper floors
        if (floorNum > 1) {
            int mid = yBottom + (yTop - yBottom) / 2;
            placeBlock(level, bars, x0,        mid, z0 + 3, writableArea);
            placeBlock(level, bars, x0,        mid, z0 + 9, writableArea);
            placeBlock(level, bars, x1,        mid, z0 + 3, writableArea);
            placeBlock(level, bars, x1,        mid, z0 + 9, writableArea);
            placeBlock(level, bars, x0 + 3, mid, z0,        writableArea);
            placeBlock(level, bars, x0 + 9, mid, z0,        writableArea);
            placeBlock(level, bars, x0 + 3, mid, z1,        writableArea);
            placeBlock(level, bars, x0 + 9, mid, z1,        writableArea);
        }

        // Central 3×3 stairwell
        int sx = x0 + FORT_W / 2 - 1;
        int sz = z0 + FORT_W / 2 - 1;
        for (int y = yBottom + 1; y < yTop; y++) {
            placeBlock(level, air, sx,     y, sz,     writableArea);
            placeBlock(level, air, sx + 1, y, sz,     writableArea);
            placeBlock(level, air, sx,     y, sz + 1, writableArea);
            placeBlock(level, air, sx + 1, y, sz + 1, writableArea);
        }
        placeBlock(level, Blocks.COBBLESTONE_STAIRS.defaultBlockState(), sx,     yBottom + 1, sz,     writableArea);
        placeBlock(level, Blocks.COBBLESTONE_STAIRS.defaultBlockState(), sx,     yBottom + 2, sz + 1, writableArea);
        placeBlock(level, Blocks.COBBLESTONE_STAIRS.defaultBlockState(), sx + 1, yBottom + 3, sz + 1, writableArea);
        placeBlock(level, Blocks.COBBLESTONE_STAIRS.defaultBlockState(), sx + 1, yBottom + 4, sz,     writableArea);

        // Floor-specific content
        if (floorNum >= 2 && floorNum <= 5) {
            generateCells(level, rng, writableArea, yBottom, yTop, x0, z0, x1,
                    floorNum == 4 || floorNum == 5);
        } else if (floorNum == 6) {
            generateRecordsFloor(level, writableArea, yBottom, x0, z0);
        } else if (floorNum >= 7 && floorNum <= 9) {
            generateCells(level, rng, writableArea, yBottom, yTop, x0, z0, x1, false);
        } else if (floorNum == 10) {
            generateHighSecurityFloor(level, writableArea, yBottom, yTop, x0, z0, x1, z1);
        }
    }

    private void generateCells(WorldGenLevel level, RandomSource rng, BoundingBox writableArea,
                                int yBottom, int yTop,
                                int x0, int z0, int x1,
                                boolean hasSpawner) {
        BlockState bars = Blocks.IRON_BARS.defaultBlockState();
        BlockState slab = Blocks.STONE_SLAB.defaultBlockState();
        int cellH = yTop - yBottom - 1;

        int[] cellZs = {z0 + 1, z0 + 5, z0 + 9};
        for (int i = 0; i < 3; i++) {
            int cz = cellZs[i];
            // West cell
            generateBox(level, writableArea, x0 + 1, yBottom + 1, cz, x0 + 3, yBottom + cellH, cz + 2,
                    Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            placeBlock(level, bars, x0 + 3, yBottom + 1, cz + 1, writableArea);
            placeBlock(level, bars, x0 + 3, yBottom + 2, cz + 1, writableArea);
            placeBlock(level, slab, x0 + 1, yBottom + 1, cz + 1, writableArea);
            // East cell
            generateBox(level, writableArea, x1 - 3, yBottom + 1, cz, x1 - 1, yBottom + cellH, cz + 2,
                    Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            placeBlock(level, bars, x1 - 3, yBottom + 1, cz + 1, writableArea);
            placeBlock(level, bars, x1 - 3, yBottom + 2, cz + 1, writableArea);
            placeBlock(level, slab, x1 - 1, yBottom + 1, cz + 1, writableArea);
        }

        if (hasSpawner) {
            placeBlock(level, Blocks.SPAWNER.defaultBlockState(), x0 + 2, yBottom + 1, z0 + 5, writableArea);
        }
    }

    private void generateRecordsFloor(WorldGenLevel level, BoundingBox writableArea, int yBottom, int x0, int z0) {
        placeBlock(level, Blocks.BOOKSHELF.defaultBlockState(), x0 + 2, yBottom + 1, z0 + 2, writableArea);
        placeBlock(level, Blocks.BOOKSHELF.defaultBlockState(), x0 + 2, yBottom + 2, z0 + 2, writableArea);
        placeBlock(level, Blocks.BOOKSHELF.defaultBlockState(), x0 + 3, yBottom + 1, z0 + 2, writableArea);
        placeBlock(level, Blocks.LECTERN.defaultBlockState(),   x0 + 5, yBottom + 1, z0 + 5, writableArea);
        placeBlock(level, Blocks.OAK_SLAB.defaultBlockState(),  x0 + 7, yBottom + 1, z0 + 7, writableArea);
        // TODO: wire chest loot table to minecraft:chests/simple_dungeon
        placeBlock(level, Blocks.CHEST.defaultBlockState(), x0 + 8, yBottom + 1, z0 + 8, writableArea);
    }

    private void generateHighSecurityFloor(WorldGenLevel level, BoundingBox writableArea,
                                           int yBottom, int yTop, int x0, int z0, int x1, int z1) {
        BlockState slab  = Blocks.STONE_SLAB.defaultBlockState();
        BlockState chain = Blocks.IRON_CHAIN.defaultBlockState();
        BlockState door  = Blocks.IRON_DOOR.defaultBlockState();

        int[][] cells = {{x0 + 1, z0 + 1}, {x0 + 8, z0 + 1}, {x0 + 1, z0 + 8}, {x0 + 8, z0 + 8}};
        for (int[] c : cells) {
            int cx = c[0], cz = c[1];
            generateBox(level, writableArea, cx, yBottom + 1, cz, cx + 3, yTop - 1, cz + 3,
                    Blocks.DEEPSLATE_BRICKS.defaultBlockState(),
                    Blocks.AIR.defaultBlockState(), false);
            placeBlock(level, door,  cx + 1, yBottom + 1, cz + 3, writableArea);
            placeBlock(level, door,  cx + 1, yBottom + 2, cz + 3, writableArea);
            placeBlock(level, slab,  cx + 1, yBottom + 1, cz + 1, writableArea);
            placeBlock(level, chain, cx + 2, yTop - 1,    cz + 2, writableArea);
        }
    }

    private void generateRoof(WorldGenLevel level, BoundingBox writableArea,
                               int x0, int yRoof, int z0, int x1, int z1) {
        generateBox(level, writableArea, x0, yRoof, z0, x1, yRoof, z1,
                Blocks.STONE.defaultBlockState(), Blocks.STONE.defaultBlockState(), false);
        for (int lx = x0; lx <= x1; lx++) {
            placeBlock(level, Blocks.STONE_BRICK_WALL.defaultBlockState(), lx, yRoof + 1, z0, writableArea);
            placeBlock(level, Blocks.STONE_BRICK_WALL.defaultBlockState(), lx, yRoof + 1, z1, writableArea);
        }
        for (int lz = z0 + 1; lz < z1; lz++) {
            placeBlock(level, Blocks.STONE_BRICK_WALL.defaultBlockState(), x0, yRoof + 1, lz, writableArea);
            placeBlock(level, Blocks.STONE_BRICK_WALL.defaultBlockState(), x1, yRoof + 1, lz, writableArea);
        }
    }
}
