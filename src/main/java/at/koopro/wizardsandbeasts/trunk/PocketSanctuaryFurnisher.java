package at.koopro.wizardsandbeasts.trunk;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * SCAMANDER_SANCTUARY furnishing for pocket dimension shells: habitat zone floors,
 * the central shed, irregular ceiling lighting and the eight zone feature builds.
 *
 * <p>Extracted from {@link PocketShellGenerator}, which remains the only caller and
 * keeps the archetype-agnostic shell work (floor, walls, ceiling, markers, biome paint).
 */
final class PocketSanctuaryFurnisher {

    private PocketSanctuaryFurnisher() {}

    /** Returns habitat zone 1-8 for a given floor offset, or 0 for the shed footprint. */
    static int getSanctuaryZone(int dx, int dz, int thirdR) {
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

    static void placeSanctuaryFloor(ServerLevel level, BlockPos center, int radius, long seed) {
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

    static void placeSanctuaryCeilingLights(ServerLevel level, BlockPos center, int radius, int wallTop) {
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

    static void placeSanctuaryShed(ServerLevel level, BlockPos center) {
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

    static void placeSanctuaryZoneFeatures(ServerLevel level, BlockPos center,
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
