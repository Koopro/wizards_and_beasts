package at.koopro.wizardsandbeasts.azkaban.structure;

import at.koopro.wizardsandbeasts.azkaban.AzkabanDamageTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import org.jspecify.annotations.NonNull;

/**
 * Single structural piece that programmatically generates the entire Azkaban Fortress
 * island plus the 10-floor fortress tower.
 *
 * <p>Coordinate system: all generate*() calls use local coords (0,0,0 = SW island corner)
 * which are translated to world space by {@link #getWorldPos}.
 *
 * <p>Layout:
 * <ul>
 *   <li>Y 0      — sea floor fill (stone)
 *   <li>Y 0–3   — island base (deepslate)
 *   <li>Y 3      — island surface (cobbled deepslate, gravel patches)
 *   <li>Y 3–5   — dock on south face
 *   <li>Y 3–88  — fortress tower (14×14 footprint at island center)
 *   <li>Y 89–90 — roof parapet
 * </ul>
 */
public final class AzkabanFortressPiece extends StructurePiece {

    // Island is 36×36 blocks
    private static final int ISLAND = 36;
    // Fortress footprint within island (centred)
    private static final int FORT_X0 = 11;
    private static final int FORT_Z0 = 11;
    private static final int FORT_W  = 14;
    private static final int FORT_H  = 86; // floors 1–10 + roof
    // Floor heights (local Y from island surface at Y=3)
    private static final int[] FLOOR_Y = {3, 10, 17, 24, 31, 38, 45, 52, 59, 66, 73, 80};
    // sea level in world coords stored so postProcess can translate
    private static final int SEA_Y = 63;

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
                origin.getX(),         SEA_Y - 10,          origin.getZ(),
                origin.getX() + ISLAND, SEA_Y + FORT_H + 4, origin.getZ() + ISLAND);
    }

    @Override
    protected void addAdditionalSaveData(
            @NonNull StructurePieceSerializationContext ctx, @NonNull CompoundTag tag) {
        tag.putLong("Origin", BlockPos.containing(
                boundingBox.minX(), boundingBox.minY() + 10, boundingBox.minZ()).asLong());
    }

    @Override
    public void postProcess(
            @NonNull WorldGenLevel level,
            net.minecraft.world.level.StructureManager structureManager,
            @NonNull ChunkGenerator generator,
            @NonNull RandomSource random,
            @NonNull BoundingBox chunkBox,
            @NonNull ChunkPos chunkPos,
            @NonNull BlockPos pivot) {

        generateIsland(level, random, chunkBox);
        generateFortress(level, random, chunkBox);
        generateDock(level, chunkBox);
    }

    // -------------------------------------------------------------------------
    // Island
    // -------------------------------------------------------------------------

    private void generateIsland(WorldGenLevel level, RandomSource random, BoundingBox chunkBox) {
        // Fill seabed up to island base with stone
        for (int x = 0; x < ISLAND; x++) {
            for (int z = 0; z < ISLAND; z++) {
                // Fill from world seabed to island top
                for (int y = -10; y < 3; y++) {
                    placeBlock(level, Blocks.DEEPSLATE.defaultBlockState(), x, y, z, chunkBox);
                }
                // Island surface
                BlockState surface = (random.nextInt(8) == 0)
                        ? Blocks.GRAVEL.defaultBlockState()
                        : Blocks.COBBLED_DEEPSLATE.defaultBlockState();
                placeBlock(level, surface, x, 3, z, chunkBox);
                // Replace any water above island surface with air
                for (int y = 4; y <= 6; y++) {
                    placeBlock(level, Blocks.AIR.defaultBlockState(), x, y, z, chunkBox);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Dock (south face, protruding 6 blocks into sea)
    // -------------------------------------------------------------------------

    private void generateDock(WorldGenLevel level, BoundingBox chunkBox) {
        int dockCenterX = ISLAND / 2;
        int dockY = 3;
        for (int dz = ISLAND; dz < ISLAND + 6; dz++) {
            for (int dx = dockCenterX - 2; dx <= dockCenterX + 2; dx++) {
                placeBlock(level, Blocks.COBBLESTONE.defaultBlockState(), dx, dockY - 1, dz, chunkBox);
                placeBlock(level, Blocks.COBBLESTONE.defaultBlockState(), dx, dockY, dz, chunkBox);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Fortress tower
    // -------------------------------------------------------------------------

    private void generateFortress(WorldGenLevel level, RandomSource rng, BoundingBox chunkBox) {
        int x0 = FORT_X0, z0 = FORT_Z0;
        int x1 = x0 + FORT_W - 1, z1 = z0 + FORT_W - 1;

        // Solid foundation (floors 0-3 at island surface)
        generateBox(level, chunkBox, x0, 3, z0, x1, 5, z1,
                Blocks.DEEPSLATE_BRICKS.defaultBlockState(),
                Blocks.DEEPSLATE_BRICKS.defaultBlockState(), false);

        // 10 floors
        generateFloor(level, rng, chunkBox, 1, FLOOR_Y[1], FLOOR_Y[2] - 1, x0, z0, x1, z1, false);
        generateFloor(level, rng, chunkBox, 2, FLOOR_Y[2], FLOOR_Y[3] - 1, x0, z0, x1, z1, false);
        generateFloor(level, rng, chunkBox, 3, FLOOR_Y[3], FLOOR_Y[4] - 1, x0, z0, x1, z1, false);
        generateFloor(level, rng, chunkBox, 4, FLOOR_Y[4], FLOOR_Y[5] - 1, x0, z0, x1, z1, false);
        generateFloor(level, rng, chunkBox, 5, FLOOR_Y[5], FLOOR_Y[6] - 1, x0, z0, x1, z1, false);
        generateFloor(level, rng, chunkBox, 6, FLOOR_Y[6], FLOOR_Y[7] - 1, x0, z0, x1, z1, false);
        generateFloor(level, rng, chunkBox, 7, FLOOR_Y[7], FLOOR_Y[8] - 1, x0, z0, x1, z1, false);
        generateFloor(level, rng, chunkBox, 8, FLOOR_Y[8], FLOOR_Y[9] - 1, x0, z0, x1, z1, false);
        generateFloor(level, rng, chunkBox, 9, FLOOR_Y[9], FLOOR_Y[10] - 1, x0, z0, x1, z1, false);
        generateFloor(level, rng, chunkBox, 10, FLOOR_Y[10], FLOOR_Y[11] - 1, x0, z0, x1, z1, true);

        // Roof at floor index 11
        generateRoof(level, chunkBox, x0, FLOOR_Y[11], z0, x1, z1);

        // Entry door on north face (floor 1)
        placeBlock(level, Blocks.IRON_DOOR.defaultBlockState(), x0 + FORT_W / 2, FLOOR_Y[1], z0, chunkBox);
        placeBlock(level, Blocks.IRON_DOOR.defaultBlockState(), x0 + FORT_W / 2, FLOOR_Y[1] + 1, z0, chunkBox);

        // Weathering pass: randomly replace some bricks with cracked variant
        for (int lx = x0; lx <= x1; lx++) {
            for (int lz = z0; lz <= z1; lz++) {
                for (int ly = 3; ly <= FLOOR_Y[11]; ly++) {
                    if (rng.nextInt(12) == 0) {
                        placeBlock(level, Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState(), lx, ly, lz, chunkBox);
                    }
                }
            }
        }
    }

    private void generateFloor(WorldGenLevel level, RandomSource rng, BoundingBox chunkBox,
                                int floorNum, int yBottom, int yTop,
                                int x0, int z0, int x1, int z1, boolean highSecurity) {
        BlockState wall   = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        BlockState floor  = Blocks.COBBLED_DEEPSLATE.defaultBlockState();
        BlockState air    = Blocks.AIR.defaultBlockState();
        BlockState bars   = Blocks.IRON_BARS.defaultBlockState();
        BlockState slab   = Blocks.STONE_SLAB.defaultBlockState();

        // Outer shell
        generateBox(level, chunkBox, x0, yBottom, z0, x1, yTop, z1, wall, air, false);

        // Ceiling / floor plate
        generateBox(level, chunkBox, x0, yBottom, z0, x1, yBottom, z1, floor, floor, false);
        generateBox(level, chunkBox, x0, yTop, z0, x1, yTop, z1, floor, floor, false);

        // Slit windows — 2 per face, iron-barred
        if (floorNum > 1) {
            int mid = yBottom + (yTop - yBottom) / 2;
            placeBlock(level, bars, x0,        mid, z0 + 3, chunkBox); // west
            placeBlock(level, bars, x0,        mid, z0 + 9, chunkBox);
            placeBlock(level, bars, x1,        mid, z0 + 3, chunkBox); // east
            placeBlock(level, bars, x1,        mid, z0 + 9, chunkBox);
            placeBlock(level, bars, x0 + 3, mid, z0,        chunkBox); // north
            placeBlock(level, bars, x0 + 9, mid, z0,        chunkBox);
            placeBlock(level, bars, x0 + 3, mid, z1,        chunkBox); // south
            placeBlock(level, bars, x0 + 9, mid, z1,        chunkBox);
        }

        // Cell corridor: 6 cells arranged around 3×3 central stairwell
        int sx = x0 + FORT_W / 2 - 1;
        int sz = z0 + FORT_W / 2 - 1;
        // Clear stairwell column
        for (int y = yBottom + 1; y < yTop; y++) {
            placeBlock(level, air, sx, y, sz, chunkBox);
            placeBlock(level, air, sx + 1, y, sz, chunkBox);
            placeBlock(level, air, sx, y, sz + 1, chunkBox);
            placeBlock(level, air, sx + 1, y, sz + 1, chunkBox);
        }
        // Staircase
        placeBlock(level, Blocks.COBBLESTONE_STAIRS.defaultBlockState(), sx,     yBottom + 1, sz,     chunkBox);
        placeBlock(level, Blocks.COBBLESTONE_STAIRS.defaultBlockState(), sx,     yBottom + 2, sz + 1, chunkBox);
        placeBlock(level, Blocks.COBBLESTONE_STAIRS.defaultBlockState(), sx + 1, yBottom + 3, sz + 1, chunkBox);
        placeBlock(level, Blocks.COBBLESTONE_STAIRS.defaultBlockState(), sx + 1, yBottom + 4, sz,     chunkBox);

        if (floorNum >= 2 && floorNum <= 5) {
            generateCells(level, rng, chunkBox, yBottom, yTop, x0, z0, x1, z1, highSecurity, floorNum == 4 || floorNum == 5);
        } else if (floorNum == 6) {
            generateRecordsFloor(level, chunkBox, yBottom, x0, z0);
        } else if (floorNum >= 7 && floorNum <= 9) {
            generateCells(level, rng, chunkBox, yBottom, yTop, x0, z0, x1, z1, false, false);
        } else if (floorNum == 10) {
            generateHighSecurityFloor(level, chunkBox, yBottom, yTop, x0, z0, x1, z1);
        }
    }

    private void generateCells(WorldGenLevel level, RandomSource rng, BoundingBox chunkBox,
                                int yBottom, int yTop,
                                int x0, int z0, int x1, int z1,
                                boolean highSec, boolean hasSpawner) {
        BlockState bars  = Blocks.IRON_BARS.defaultBlockState();
        BlockState slab  = Blocks.STONE_SLAB.defaultBlockState();
        BlockState door  = Blocks.IRON_DOOR.defaultBlockState();
        BlockState chain = Blocks.IRON_CHAIN.defaultBlockState();

        int cellH = yTop - yBottom - 1;
        // 6 cells: 3 west side, 3 east side
        int[] cellZs = {z0 + 1, z0 + 5, z0 + 9};
        for (int i = 0; i < 3; i++) {
            int cz = cellZs[i];
            // West cell
            generateBox(level, chunkBox, x0 + 1, yBottom + 1, cz, x0 + 3, yBottom + cellH, cz + 2,
                    Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            placeBlock(level, bars, x0 + 3, yBottom + 1, cz + 1, chunkBox);
            placeBlock(level, bars, x0 + 3, yBottom + 2, cz + 1, chunkBox);
            placeBlock(level, slab, x0 + 1, yBottom + 1, cz + 1, chunkBox); // bunk
            // East cell
            generateBox(level, chunkBox, x1 - 3, yBottom + 1, cz, x1 - 1, yBottom + cellH, cz + 2,
                    Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            placeBlock(level, bars, x1 - 3, yBottom + 1, cz + 1, chunkBox);
            placeBlock(level, bars, x1 - 3, yBottom + 2, cz + 1, chunkBox);
            placeBlock(level, slab, x1 - 1, yBottom + 1, cz + 1, chunkBox);
        }

        // One cell per floor with placeholder spawner
        if (hasSpawner) {
            placeBlock(level, Blocks.SPAWNER.defaultBlockState(), x0 + 2, yBottom + 1, z0 + 5, chunkBox);
        }
    }

    private void generateRecordsFloor(WorldGenLevel level, BoundingBox chunkBox, int yBottom, int x0, int z0) {
        placeBlock(level, Blocks.BOOKSHELF.defaultBlockState(), x0 + 2, yBottom + 1, z0 + 2, chunkBox);
        placeBlock(level, Blocks.BOOKSHELF.defaultBlockState(), x0 + 2, yBottom + 2, z0 + 2, chunkBox);
        placeBlock(level, Blocks.BOOKSHELF.defaultBlockState(), x0 + 3, yBottom + 1, z0 + 2, chunkBox);
        placeBlock(level, Blocks.LECTERN.defaultBlockState(),   x0 + 5, yBottom + 1, z0 + 5, chunkBox);
        placeBlock(level, Blocks.OAK_SLAB.defaultBlockState(),  x0 + 7, yBottom + 1, z0 + 7, chunkBox); // warden's desk
        // Placeholder loot chest
        placeBlock(level, Blocks.CHEST.defaultBlockState(), x0 + 8, yBottom + 1, z0 + 8, chunkBox);
        // TODO: wire chest loot table to minecraft:chests/simple_dungeon
    }

    private void generateHighSecurityFloor(WorldGenLevel level, BoundingBox chunkBox,
                                           int yBottom, int yTop, int x0, int z0, int x1, int z1) {
        BlockState door  = Blocks.IRON_DOOR.defaultBlockState();
        BlockState slab  = Blocks.STONE_SLAB.defaultBlockState();
        BlockState chain = Blocks.IRON_CHAIN.defaultBlockState();

        // 4 sealed stone cells with solid iron-bound doors
        int[][] cells = {{x0 + 1, z0 + 1}, {x0 + 8, z0 + 1}, {x0 + 1, z0 + 8}, {x0 + 8, z0 + 8}};
        for (int[] c : cells) {
            int cx = c[0], cz = c[1];
            generateBox(level, chunkBox, cx, yBottom + 1, cz, cx + 3, yTop - 1, cz + 3,
                    Blocks.DEEPSLATE_BRICKS.defaultBlockState(),
                    Blocks.AIR.defaultBlockState(), false);
            placeBlock(level, door, cx + 1, yBottom + 1, cz + 3, chunkBox);
            placeBlock(level, door, cx + 1, yBottom + 2, cz + 3, chunkBox);
            placeBlock(level, slab, cx + 1, yBottom + 1, cz + 1, chunkBox); // stone bunk
            placeBlock(level, chain, cx + 2, yTop - 1, cz + 2, chunkBox);
        }
    }

    private void generateRoof(WorldGenLevel level, BoundingBox chunkBox, int x0, int yRoof, int z0, int x1, int z1) {
        // Flat stone roof
        generateBox(level, chunkBox, x0, yRoof, z0, x1, yRoof, z1,
                Blocks.STONE.defaultBlockState(), Blocks.STONE.defaultBlockState(), false);
        // Low parapet
        for (int lx = x0; lx <= x1; lx++) {
            placeBlock(level, Blocks.STONE_BRICK_WALL.defaultBlockState(), lx, yRoof + 1, z0, chunkBox);
            placeBlock(level, Blocks.STONE_BRICK_WALL.defaultBlockState(), lx, yRoof + 1, z1, chunkBox);
        }
        for (int lz = z0 + 1; lz < z1; lz++) {
            placeBlock(level, Blocks.STONE_BRICK_WALL.defaultBlockState(), x0, yRoof + 1, lz, chunkBox);
            placeBlock(level, Blocks.STONE_BRICK_WALL.defaultBlockState(), x1, yRoof + 1, lz, chunkBox);
        }
    }
}
