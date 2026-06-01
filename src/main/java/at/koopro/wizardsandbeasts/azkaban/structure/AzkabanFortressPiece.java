package at.koopro.wizardsandbeasts.azkaban.structure;

import at.koopro.wizardsandbeasts.azkaban.biome.AzkabanBiomes;
import at.koopro.wizardsandbeasts.azkaban.data.AzkabanWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import org.jspecify.annotations.NonNull;

/**
 * Procedural piece for the entire Azkaban Fortress island and castle complex.
 *
 * <p>Local coordinate system (all relative to bounding box min corner):
 * <ul>
 *   <li>Local (60, 10, 60) = world origin = island center at surface (= seaLevel + 1)
 *   <li>Local Y 4 = sea level - 5 (deepslate floor)
 *   <li>Local Y 10 = island surface base
 * </ul>
 *
 * <p>Layout (local Y from surface base):
 * <ul>
 *   <li>Y 4–9   — island deepslate/stone base
 *   <li>Y 10    — island surface (gravel/stone mix)
 *   <li>Y 10–21 — outer curtain wall (10 tall + 2-block battlements)
 *   <li>Y 10–23 — corner turrets (14 tall)
 *   <li>Y 10–39 — central keep (30 tall)
 *   <li>Y 10–27 — flanking towers (18 tall)
 * </ul>
 */
public final class AzkabanFortressPiece extends StructurePiece {

    // --- Coordinate constants (local, relative to bounding box minX/minY/minZ) -------

    /** Local Y of island surface base = origin.y (= seaLevel + 1). */
    private static final int SURFACE_LY       = 10;
    /** Local Y of deepslate floor = seaLevel - 5. */
    private static final int DEEPSLATE_FLOOR_LY = 4;
    /** Local X/Z of island and fortress center. */
    private static final int LCX = 60;
    private static final int LCZ = 60;

    /** Curtain wall: 38×38 outer, half-extent = 19. */
    private static final int WALL_HALF  = 19;
    private static final int WALL_H     = 10;
    private static final int WALL_THICK = 2;

    /** Central keep: 16×16 (−8 to +7 from center). */
    private static final int KEEP_LO = -8;
    private static final int KEEP_HI = +7;
    private static final int KEEP_H  = 30;

    /** Flanking towers: 8×8 (−4 to +3 from tower center). */
    private static final int TOWER_H      = 18;
    private static final int TOWER_X_HALF = 7; // half-extent of tower span from LCX (tower spans LCX±18 to LCX±11)

    /** Biome override radius (blocks). */
    private static final int BIOME_RADIUS = 256;

    /** Kept for external reference (referenced in WizardsAndBeastsMod before listener fix). */
    public static final int ISLAND_SURFACE_Y = 65;
    /** Height alias kept for AzkabanCommands compatibility. */
    public static final int FORT_H = KEEP_H;

    // --- Fields -----------------------------------------------------------

    private final BlockPos origin;

    // --- Construction -----------------------------------------------------

    public AzkabanFortressPiece(@NonNull StructurePieceType type, @NonNull BlockPos origin) {
        super(type, 0, makeBoundingBox(origin));
        this.origin = origin;
        AzkabanStructures.cachedFortressCenter = origin;
    }

    private static BoundingBox makeBoundingBox(@NonNull BlockPos origin) {
        return BoundingBox.fromCorners(
                origin.offset(-60, -10, -60),
                origin.offset(60,  40,  60));
    }

    /** Deserialization entry-point called by the piece registry. */
    public static AzkabanFortressPiece load(
            @NonNull StructurePieceSerializationContext ctx,
            @NonNull CompoundTag tag) {
        BlockPos origin = BlockPos.of(tag.getLong("Origin").orElse(0L));
        return new AzkabanFortressPiece(AzkabanStructures.AZKABAN_FORTRESS_PIECE.get(), origin);
    }

    @Override
    protected void addAdditionalSaveData(
            @NonNull StructurePieceSerializationContext ctx,
            @NonNull CompoundTag tag) {
        tag.putLong("Origin", origin.asLong());
    }

    // --- postProcess ------------------------------------------------------

    @Override
    public void postProcess(
            @NonNull WorldGenLevel level,
            @NonNull StructureManager structureManager,
            @NonNull ChunkGenerator generator,
            @NonNull RandomSource random,
            @NonNull BoundingBox writableArea,
            @NonNull ChunkPos chunkPos,
            @NonNull BlockPos pivot) {
        placeIsland(level, writableArea);
        placeFortress(level, writableArea, random);
        overrideBiome(level, chunkPos);
        persistCenter(level);
    }

    // =====================================================================
    // Island
    // =====================================================================

    private void placeIsland(@NonNull WorldGenLevel level, @NonNull BoundingBox writableArea) {
        BlockState air = Blocks.AIR.defaultBlockState();

        for (int dx = -40; dx <= 40; dx++) {
            for (int dz = -40; dz <= 40; dz++) {
                double dist = Math.sqrt(dx * dx * 0.7 + dz * dz) / 38.0;
                if (dist >= 1.0) continue;

                int lx = LCX + dx;
                int lz = LCZ + dz;

                // Deterministic per-column random (repeatable regardless of chunk boundary)
                java.util.Random colRng = new java.util.Random(
                        origin.asLong() ^ (dx * 1000003L + dz * 999983L));
                int variation = colRng.nextInt(7) - 3;    // ±3
                int height    = Math.max(0, (int) (4.0 * (1.0 - dist)) + variation);
                int surfaceLY = SURFACE_LY + height;

                // Surface: 30% gravel, 70% stone
                BlockState surface = (colRng.nextInt(10) < 3)
                        ? Blocks.GRAVEL.defaultBlockState()
                        : Blocks.STONE.defaultBlockState();
                placeBlock(level, surface, lx, surfaceLY, lz, writableArea);

                // Stone mid: 2–3 blocks below surface
                int stoneLayers = 2 + colRng.nextInt(2);
                int stoneFloor  = Math.max(DEEPSLATE_FLOOR_LY, surfaceLY - stoneLayers);
                for (int y = surfaceLY - 1; y >= stoneFloor; y--) {
                    placeBlock(level, Blocks.STONE.defaultBlockState(), lx, y, lz, writableArea);
                }

                // Deepslate base from DEEPSLATE_FLOOR_LY up to stone floor
                for (int y = DEEPSLATE_FLOOR_LY; y < stoneFloor; y++) {
                    placeBlock(level, Blocks.DEEPSLATE.defaultBlockState(), lx, y, lz, writableArea);
                }

                // Clear terrain above surface up to well above the fortress crown
                for (int y = surfaceLY + 1; y <= SURFACE_LY + 28; y++) {
                    placeBlock(level, air, lx, y, lz, writableArea);
                }
            }
        }
    }

    // =====================================================================
    // Fortress
    // =====================================================================

    private void placeFortress(@NonNull WorldGenLevel level, @NonNull BoundingBox writableArea,
                                @NonNull RandomSource random) {
        // Curtain wall outer bounds (local coords)
        int wallX0 = LCX - WALL_HALF;          // 41
        int wallX1 = LCX + WALL_HALF;          // 79
        int wallZ0 = LCZ - WALL_HALF;          // 41
        int wallZ1 = LCZ + WALL_HALF;          // 79
        int wallY0  = SURFACE_LY;              // 10
        int wallY1  = SURFACE_LY + WALL_H - 1; // 19  (10 blocks)
        int battleY = SURFACE_LY + WALL_H;    // 20  (battlement start)

        placeCurtainWall(level, writableArea, random, wallX0, wallZ0, wallX1, wallZ1, wallY0, wallY1, battleY);
        placeCourtyard(level, writableArea, wallX0, wallZ0, wallX1, wallZ1, wallY0, wallY1);

        // Central keep
        int keepX0 = LCX + KEEP_LO; // 52
        int keepX1 = LCX + KEEP_HI; // 67
        int keepZ0 = LCZ + KEEP_LO; // 52
        int keepZ1 = LCZ + KEEP_HI; // 67
        int keepY1 = SURFACE_LY + KEEP_H - 1; // 39
        placeKeep(level, writableArea, keepX0, keepZ0, keepX1, keepZ1, SURFACE_LY, keepY1);

        // Flanking towers: 8×8, centered at LCX ± 14, symmetric in Z about LCZ
        int towerZ0 = LCZ - 4; // 56
        int towerZ1 = LCZ + 3; // 63  (8 blocks)
        int towerY1 = SURFACE_LY + TOWER_H - 1; // 27
        // West tower: X [LCX-18, LCX-11] = [42, 49]
        placeTower(level, writableArea, LCX - 18, towerZ0, LCX - 11, towerZ1, SURFACE_LY, towerY1);
        // East tower: X [LCX+11, LCX+18] = [71, 78]
        placeTower(level, writableArea, LCX + 11, towerZ0, LCX + 18, towerZ1, SURFACE_LY, towerY1);

        // Entrance archway through south curtain wall
        placeEntrance(level, writableArea, wallZ1, wallY0);
    }

    // --- Curtain wall -----------------------------------------------------

    private void placeCurtainWall(@NonNull WorldGenLevel level, @NonNull BoundingBox writableArea,
                                   @NonNull RandomSource random,
                                   int x0, int z0, int x1, int z1,
                                   int y0, int y1, int battleY) {
        BlockState primary = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        BlockState alt     = Blocks.COBBLED_DEEPSLATE.defaultBlockState();
        BlockState air     = Blocks.AIR.defaultBlockState();

        // Solid fill, then carve interior above floor (2-thick walls all round)
        generateBox(level, writableArea, x0, y0, z0, x1, y1, z1, primary, primary, false);
        generateBox(level, writableArea,
                x0 + WALL_THICK, y0 + 1, z0 + WALL_THICK,
                x1 - WALL_THICK, y1,     z1 - WALL_THICK,
                air, air, false);

        // 20% cobbled-deepslate variation on outer faces
        applyWallVariation(level, writableArea, random, x0, z0, x1, z1, y0, y1, primary, alt);

        // Battlements: 2-block merlons every 2 blocks along wall top
        for (int x = x0; x <= x1; x += 2) {
            placeBlock(level, primary, x, battleY,     z0, writableArea);
            placeBlock(level, primary, x, battleY + 1, z0, writableArea);
            placeBlock(level, primary, x, battleY,     z1, writableArea);
            placeBlock(level, primary, x, battleY + 1, z1, writableArea);
        }
        for (int z = z0 + 2; z < z1; z += 2) {
            placeBlock(level, primary, x0, battleY,     z, writableArea);
            placeBlock(level, primary, x0, battleY + 1, z, writableArea);
            placeBlock(level, primary, x1, battleY,     z, writableArea);
            placeBlock(level, primary, x1, battleY + 1, z, writableArea);
        }

        // Corner turrets: 4×4, 14 blocks tall (Y 10–23), crenellated top
        int turretY1   = y0 + 13;    // 23 — last solid turret block
        int crenelY    = turretY1 + 1; // 24 — merlon base
        placeCornerTurret(level, writableArea, primary, x0,      z0,      y0, turretY1, crenelY);
        placeCornerTurret(level, writableArea, primary, x1 - 3,  z0,      y0, turretY1, crenelY);
        placeCornerTurret(level, writableArea, primary, x0,      z1 - 3,  y0, turretY1, crenelY);
        placeCornerTurret(level, writableArea, primary, x1 - 3,  z1 - 3,  y0, turretY1, crenelY);
    }

    private void applyWallVariation(@NonNull WorldGenLevel level, @NonNull BoundingBox writableArea,
                                     @NonNull RandomSource random,
                                     int x0, int z0, int x1, int z1,
                                     int y0, int y1,
                                     BlockState primary, BlockState alt) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                if (random.nextInt(5) == 0) placeBlock(level, alt, x, y, z0, writableArea);
                if (random.nextInt(5) == 0) placeBlock(level, alt, x, y, z1, writableArea);
            }
        }
        for (int z = z0 + 1; z < z1; z++) {
            for (int y = y0; y <= y1; y++) {
                if (random.nextInt(5) == 0) placeBlock(level, alt, x0, y, z, writableArea);
                if (random.nextInt(5) == 0) placeBlock(level, alt, x1, y, z, writableArea);
            }
        }
    }

    private void placeCornerTurret(@NonNull WorldGenLevel level, @NonNull BoundingBox writableArea,
                                    BlockState material,
                                    int tx0, int tz0, int y0, int y1, int crenelY) {
        // Solid 4×4 column, taller than curtain wall
        generateBox(level, writableArea, tx0, y0, tz0, tx0 + 3, y1, tz0 + 3, material, material, false);
        // Crenellations: merlons at alternate perimeter positions on top face
        for (int x = tx0; x <= tx0 + 3; x++) {
            if ((x - tx0) % 2 == 0) {
                placeBlock(level, material, x, crenelY, tz0,     writableArea);
                placeBlock(level, material, x, crenelY, tz0 + 3, writableArea);
            }
        }
        // Interior Z sides (avoid double-placing corners)
        if ((1) % 2 == 0) placeBlock(level, material, tx0,     crenelY, tz0 + 1, writableArea);
        if ((1) % 2 == 0) placeBlock(level, material, tx0 + 3, crenelY, tz0 + 1, writableArea);
        if ((2) % 2 == 0) placeBlock(level, material, tx0,     crenelY, tz0 + 2, writableArea);
        if ((2) % 2 == 0) placeBlock(level, material, tx0 + 3, crenelY, tz0 + 2, writableArea);
    }

    // --- Courtyard --------------------------------------------------------

    private void placeCourtyard(@NonNull WorldGenLevel level, @NonNull BoundingBox writableArea,
                                 int wallX0, int wallZ0, int wallX1, int wallZ1,
                                 int y0, int y1) {
        int ix0 = wallX0 + WALL_THICK;
        int ix1 = wallX1 - WALL_THICK;
        int iz0 = wallZ0 + WALL_THICK;
        int iz1 = wallZ1 - WALL_THICK;

        // Clear interior above floor, up through battlement height + headroom
        generateBox(level, writableArea, ix0, y0 + 1, iz0, ix1, y1 + 4, iz1,
                Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);

        // Deepslate tile slab floor (bottom half = default)
        BlockState floorSlab = Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState();
        for (int x = ix0; x <= ix1; x++) {
            for (int z = iz0; z <= iz1; z++) {
                placeBlock(level, floorSlab, x, y0, z, writableArea);
            }
        }
    }

    // --- Central keep -----------------------------------------------------

    private void placeKeep(@NonNull WorldGenLevel level, @NonNull BoundingBox writableArea,
                            int x0, int z0, int x1, int z1, int y0, int y1) {
        BlockState wall = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        BlockState air  = Blocks.AIR.defaultBlockState();
        // Top-half slab for roof
        BlockState roof = Blocks.DEEPSLATE_BRICK_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.TOP);

        // Solid shell
        generateBox(level, writableArea, x0, y0, z0, x1, y1, z1, wall, wall, false);
        // Hollow interior (keep floor at y0 solid)
        generateBox(level, writableArea,
                x0 + 2, y0 + 1, z0 + 2,
                x1 - 2, y1,     z1 - 2,
                air, air, false);

        // Flat roof: replace solid top layer with top-half slabs
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                placeBlock(level, roof, x, y1, z, writableArea);
            }
        }

        // Arrow slits: 1×2, every 8 blocks H, every 6 blocks V
        placeArrowSlits(level, writableArea, x0, z0, x1, z1, y0, y1);
    }

    private void placeArrowSlits(@NonNull WorldGenLevel level, @NonNull BoundingBox writableArea,
                                  int x0, int z0, int x1, int z1, int y0, int y1) {
        BlockState air = Blocks.AIR.defaultBlockState();
        // North (z0) and south (z1) faces — vary X
        for (int x = x0 + 4; x < x1; x += 8) {
            for (int y = y0 + 3; y < y1 - 1; y += 6) {
                placeBlock(level, air, x, y,     z0, writableArea);
                placeBlock(level, air, x, y + 1, z0, writableArea);
                placeBlock(level, air, x, y,     z1, writableArea);
                placeBlock(level, air, x, y + 1, z1, writableArea);
            }
        }
        // West (x0) and east (x1) faces — vary Z
        for (int z = z0 + 4; z < z1; z += 8) {
            for (int y = y0 + 3; y < y1 - 1; y += 6) {
                placeBlock(level, air, x0, y,     z, writableArea);
                placeBlock(level, air, x0, y + 1, z, writableArea);
                placeBlock(level, air, x1, y,     z, writableArea);
                placeBlock(level, air, x1, y + 1, z, writableArea);
            }
        }
    }

    // --- Flanking towers --------------------------------------------------

    private void placeTower(@NonNull WorldGenLevel level, @NonNull BoundingBox writableArea,
                             int x0, int z0, int x1, int z1, int y0, int y1) {
        BlockState wall = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        BlockState air  = Blocks.AIR.defaultBlockState();

        generateBox(level, writableArea, x0, y0, z0, x1, y1, z1, wall, wall, false);
        generateBox(level, writableArea,
                x0 + 2, y0 + 1, z0 + 2,
                x1 - 2, y1,     z1 - 2,
                air, air, false);
        placeArrowSlits(level, writableArea, x0, z0, x1, z1, y0, y1);
    }

    // --- Entrance archway -------------------------------------------------

    private void placeEntrance(@NonNull WorldGenLevel level, @NonNull BoundingBox writableArea,
                                int wallZ1, int y0) {
        BlockState air = Blocks.AIR.defaultBlockState();

        // 4-wide × 6-tall opening through both layers of south wall
        for (int y = y0; y <= y0 + 5; y++) {
            for (int x = LCX - 2; x <= LCX + 1; x++) {
                placeBlock(level, air, x, y, wallZ1,     writableArea);
                placeBlock(level, air, x, y, wallZ1 - 1, writableArea);
            }
        }

        // Deepslate brick stair frame
        BlockState stairEast = Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.EAST)
                .setValue(StairBlock.HALF, Half.BOTTOM)
                .setValue(StairBlock.SHAPE, StairsShape.STRAIGHT);
        BlockState stairWest = Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.WEST)
                .setValue(StairBlock.HALF, Half.BOTTOM)
                .setValue(StairBlock.SHAPE, StairsShape.STRAIGHT);
        BlockState stairDown = Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.SOUTH)
                .setValue(StairBlock.HALF, Half.TOP)
                .setValue(StairBlock.SHAPE, StairsShape.STRAIGHT);

        // Left pillar (X = LCX-3 = 57)
        for (int y = y0; y <= y0 + 5; y++) {
            placeBlock(level, stairEast, LCX - 3, y, wallZ1, writableArea);
        }
        // Right pillar (X = LCX+2 = 62)
        for (int y = y0; y <= y0 + 5; y++) {
            placeBlock(level, stairWest, LCX + 2, y, wallZ1, writableArea);
        }
        // Lintel (Y = y0+6 = 16)
        for (int x = LCX - 2; x <= LCX + 1; x++) {
            placeBlock(level, stairDown, x, y0 + 6, wallZ1, writableArea);
        }
    }

    // =====================================================================
    // Biome override — write azkaban_sea into all chunk sections within radius
    // =====================================================================

    @SuppressWarnings("unchecked")
    private void overrideBiome(@NonNull WorldGenLevel level, @NonNull ChunkPos chunkPos) {
        BlockPos center = AzkabanStructures.cachedFortressCenter;
        if (center == null) return;

        Holder<Biome> azkabanSea;
        try {
            azkabanSea = level.registryAccess()
                    .lookupOrThrow(Registries.BIOME)
                    .getOrThrow(AzkabanBiomes.AZKABAN_SEA);
        } catch (Exception e) {
            return; // biome not in RegistryAccess — skip silently
        }

        ChunkAccess chunk = level.getChunk(chunkPos.x, chunkPos.z);
        LevelChunkSection[] sections = chunk.getSections();

        for (int si = 0; si < sections.length; si++) {
            LevelChunkSection section = sections[si];
            if (section == null) continue;
            PalettedContainerRO<Holder<Biome>> biomesRO = section.getBiomes();
            if (!(biomesRO instanceof PalettedContainer<?> raw)) continue;
            @SuppressWarnings("unchecked")
            PalettedContainer<Holder<Biome>> biomes = (PalettedContainer<Holder<Biome>>) raw;

            for (int bx = 0; bx < 4; bx++) {
                for (int bz = 0; bz < 4; bz++) {
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

    // =====================================================================
    // Persist center to SavedData (requires ServerLevel)
    // =====================================================================

    private void persistCenter(@NonNull WorldGenLevel level) {
        BlockPos center = AzkabanStructures.cachedFortressCenter;
        if (center == null) return;
        if (level instanceof ServerLevelAccessor sla && sla.getLevel() instanceof ServerLevel sl) {
            AzkabanWorldData data = AzkabanWorldData.get(sl);
            if (!data.isGenerated()) {
                data.setFortressCenter(center);
            }
        }
    }
}
