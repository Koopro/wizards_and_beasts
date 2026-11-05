package at.koopro.wizardsandbeasts.creature.ecology;

import at.koopro.wizardsandbeasts.creature.schedule.AiMetrics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Finds water to drink and food to forage without scanning the world.
 *
 * <p><b>Cost</b> (the SLOW tier, documentation/CREATURE_AI_SCHEDULING.md). A search reads at most
 * {@link #COLUMNS} columns — a grid every {@link #STRIDE} blocks out to {@link #RADIUS}, shifted by a random offset
 * each time so repeated searches cover the gaps, nearest columns first so the nearest find wins — each
 * {@link #DEPTH} blocks tall: at most 392 block reads, never into an unloaded chunk. It runs only when a need is close
 * to acting and nothing is known yet. A hit
 * is remembered for {@link #MEMORY} ticks and re-checked on arrival; a miss backs off (the goal's {@code AiBackoff}).
 * A creature standing by a lake finds it in one or two searches; a creature in a desert gives up quickly and tries
 * again later, which is what an animal does.
 *
 * <p>The search itself is pure over a {@link Probe}, so its geometry is unit-tested on a fake grid
 * ({@code EcologyResourcesTest}); {@link #probe} is the level-backed one.
 */
@NullMarked
public final class EcologyResources {

    public static final int RADIUS = 12;
    /** Blocks between sampled columns: water or food four blocks across is never missed. */
    public static final int STRIDE = 4;
    /** Column offsets of the grid, nearest first. */
    private static final int[][] GRID = grid();
    public static final int COLUMNS = GRID.length;
    /** Rows per column: three above the creature's feet to four below. */
    public static final int DEPTH = 8;
    /** How long a found place is trusted before it is looked for again. */
    public static final int MEMORY = 2400;

    /** What a resource search sees at one block. */
    public enum Cell {
        /** Not loaded: never read, never stood on. */
        UNLOADED,
        /** Air, or something a creature walks through (grass, flowers) that it does not want. */
        OPEN,
        /** Something to stand on. */
        SOLID,
        WATER,
        /** Wanted food a creature can stand in (short grass, flowers). */
        FOOD_OPEN,
        /** Wanted food it stands on (a grass block). */
        FOOD_GROUND,
        /** Wanted food it stands beside (leaves, a trunk). */
        FOOD_SOLID
    }

    /** One block of the world, as a search sees it. */
    @FunctionalInterface
    public interface Probe {
        Cell at(int x, int y, int z);
    }

    /** A place to stand, and the block it is for (the water or the food). */
    public record Find(BlockPos stand, BlockPos target) {}

    private EcologyResources() {}

    /** A drinking spot: a dry, standable block beside water. */
    public static @Nullable Find findWater(BlockPos origin, RandomSource random, Probe probe) {
        return search(origin, random, probe, Cell.WATER);
    }

    /** A foraging spot: food it can stand in, or a standable block beside (or on) food. */
    public static @Nullable Find findFood(BlockPos origin, RandomSource random, Probe probe) {
        return search(origin, random, probe, Cell.FOOD_OPEN);
    }

    private static @Nullable Find search(BlockPos origin, RandomSource random, Probe probe, Cell wanted) {
        AiMetrics.inc(AiMetrics.Metric.RESOURCE_SEARCH);
        int jx = random.nextInt(STRIDE) - STRIDE / 2;
        int jz = random.nextInt(STRIDE) - STRIDE / 2;
        for (int[] offset : GRID) {
            int x = origin.getX() + offset[0] + jx;
            int z = origin.getZ() + offset[1] + jz;
            for (int dy = 3; dy > 3 - DEPTH; dy--) {
                int y = origin.getY() + dy;
                Cell cell = probe.at(x, y, z);
                if (cell == Cell.UNLOADED) {
                    break;
                }
                Find found = wanted == Cell.WATER
                        ? (cell == Cell.WATER ? shore(origin, x, y, z, probe) : null)
                        : food(cell, x, y, z, probe);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static int[][] grid() {
        int n = RADIUS / STRIDE;
        java.util.List<int[]> out = new java.util.ArrayList<>();
        for (int gx = -n; gx <= n; gx++) {
            for (int gz = -n; gz <= n; gz++) {
                out.add(new int[] {gx * STRIDE, gz * STRIDE});
            }
        }
        out.sort(java.util.Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1]));
        return out.toArray(new int[0][]);
    }

    private static @Nullable Find food(Cell cell, int x, int y, int z, Probe probe) {
        if (cell == Cell.FOOD_OPEN && standable(x, y, z, probe)) {
            return new Find(new BlockPos(x, y, z), new BlockPos(x, y, z));
        }
        if (cell == Cell.FOOD_GROUND && standable(x, y + 1, z, probe)) { // a grass block: stand on it
            return new Find(new BlockPos(x, y + 1, z), new BlockPos(x, y, z));
        }
        if (cell == Cell.FOOD_SOLID) { // leaves, a trunk: stand by it
            return beside(x, y, z, probe);
        }
        return null;
    }

    /**
     * A bank to drink from, for water found at (x, y, z). Most of a lake's surface is far from its edge, so a hit in
     * open water walks back toward the creature — which stands on land — one block at a time until it reaches the
     * last water block before the bank. At most {@code 2 × RADIUS} steps of four reads.
     */
    private static @Nullable Find shore(BlockPos origin, int x, int y, int z, Probe probe) {
        Find direct = beside(x, y, z, probe);
        if (direct != null) {
            return direct;
        }
        int cx = x;
        int cz = z;
        for (int step = 0; step < RADIUS * 2 && (cx != origin.getX() || cz != origin.getZ()); step++) {
            int dx = origin.getX() - cx;
            int dz = origin.getZ() - cz;
            if (Math.abs(dx) >= Math.abs(dz)) {
                cx += Integer.signum(dx);
            } else {
                cz += Integer.signum(dz);
            }
            Cell cell = probe.at(cx, y, cz);
            if (cell == Cell.UNLOADED) {
                return null;
            }
            if (cell != Cell.WATER) {
                return null; // left the water without finding a bank: a cliff or a wall, not a shore
            }
            Find bank = beside(cx, y, cz, probe);
            if (bank != null) {
                return bank;
            }
        }
        return null;
    }

    /** A standable block next to (x, y, z): on the bank one up (water at ground level), level with it, or one down. */
    private static @Nullable Find beside(int x, int y, int z, Probe probe) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int sx = x + d.getStepX();
            int sz = z + d.getStepZ();
            for (int sy = y + 1; sy >= y - 1; sy--) {
                if (standable(sx, sy, sz, probe)) {
                    return new Find(new BlockPos(sx, sy, sz), new BlockPos(x, y, z));
                }
            }
        }
        return null;
    }

    /** Open at feet and head, solid below. */
    static boolean standable(int x, int y, int z, Probe probe) {
        Cell feet = probe.at(x, y, z);
        return (feet == Cell.OPEN || feet == Cell.FOOD_OPEN)
                && open(probe.at(x, y + 1, z))
                && floor(probe.at(x, y - 1, z));
    }

    private static boolean floor(Cell cell) {
        return cell == Cell.SOLID || cell == Cell.FOOD_GROUND || cell == Cell.FOOD_SOLID;
    }

    private static boolean open(Cell cell) {
        return cell == Cell.OPEN || cell == Cell.FOOD_OPEN;
    }

    // ── the level ───────────────────────────────────────────────────────────────────────────────

    /** The level as a search for {@code profile}'s food (or water) sees it. One mutable position, reused. */
    public static Probe probe(ServerLevel level, EcologyProfile profile) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        return (x, y, z) -> {
            cursor.set(x, y, z);
            if (!level.isLoaded(cursor)) {
                return Cell.UNLOADED;
            }
            BlockState state = level.getBlockState(cursor);
            if (state.getFluidState().is(FluidTags.WATER)) {
                return Cell.WATER;
            }
            Cell food = foodCell(profile, state);
            if (food != null) {
                return food;
            }
            return state.blocksMotion() ? Cell.SOLID : (state.getFluidState().isEmpty() ? Cell.OPEN : Cell.SOLID);
        };
    }

    /** Whether {@code state} is food for {@code profile}, and how it is reached; {@code null} if not food. */
    public static @Nullable Cell foodCell(EcologyProfile profile, BlockState state) {
        if (profile.forageBlocks().isPresent()) {
            return state.is(profile.forageBlocks().get()) ? (state.blocksMotion() ? Cell.FOOD_SOLID : Cell.FOOD_OPEN) : null;
        }
        if (profile.has(EcologyCapability.GRAZER)) {
            if (state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN) || state.is(BlockTags.SMALL_FLOWERS)) {
                return Cell.FOOD_OPEN;
            }
            if (state.is(Blocks.GRASS_BLOCK)) {
                return Cell.FOOD_GROUND;
            }
        }
        if (profile.has(EcologyCapability.BROWSER) && state.is(BlockTags.LEAVES)) {
            return Cell.FOOD_SOLID;
        }
        if (profile.has(EcologyCapability.INSECTIVORE) && state.is(BlockTags.LOGS)) {
            return Cell.FOOD_SOLID;
        }
        return null;
    }
}
