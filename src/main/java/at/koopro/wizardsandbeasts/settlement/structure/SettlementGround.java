package at.koopro.wizardsandbeasts.settlement.structure;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jspecify.annotations.NullMarked;

import java.util.function.Predicate;

/**
 * The ground a settlement stands on.
 *
 * <p>A site is chosen before caves and ravines are carved, so no site check can see them. They are met after placing
 * instead, as a Woodland Mansion meets them. But a hamlet is not a mansion: filling every column to the bottom would plug
 * a ravine with a block of stone. The rule, for every floor cell with nothing under it:
 * <ul>
 *   <li>the floor gets a plate {@link #PLATE_DEPTH} deep, so nothing a person can stand on hangs, and gravel cannot fall;</li>
 *   <li>on a {@link #PIER_SPACING}-block grid a pier carries the plate down to rock, if rock lies within
 *       {@link #PIER_REACH} of the floor;</li>
 *   <li>past that no pier is begun: the plate spans the gap as a bridge does, and the void stays a void. A column that
 *       stops in mid-air, or one a hundred blocks tall, would be worse than neither.</li>
 * </ul>
 */
@NullMarked
public final class SettlementGround {

    public static final TagKey<Structure> SETTLEMENTS = TagKey.create(Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "settlement"));
    /** Depth of masonry under every floor cell with nothing beneath it. */
    public static final int PLATE_DEPTH = 2;
    public static final int PIER_SPACING = 4;
    /** {@link #footed}'s answer for a column with no rock within reach. */
    public static final int NO_ROCK = Integer.MAX_VALUE;
    /** The deepest gap a pier is built down into, counted from the floor. */
    public static final int PIER_REACH = 24;

    private SettlementGround() {}

    /**
     * Whether {@code pos} is claimed by a settlement. A structure's references cover its box and the margin its terrain
     * adaptation adds, twelve blocks all round, so anything a feature can reach from outside them cannot touch a piece.
     */
    public static boolean reserved(WorldGenLevel level, BlockPos pos) {
        var references = level.getChunk(pos).getAllReferences();
        if (references.isEmpty()) {
            return false;
        }
        Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        for (Structure structure : references.keySet()) {
            if (registry.wrapAsHolder(structure).is(SETTLEMENTS)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether masonry goes in this column at {@code depth} blocks under the floor (1 is directly beneath).
     *
     * @param rock how far below the floor this column reaches something solid; see {@link #NO_ROCK}
     */
    public static boolean footed(int x, int z, int depth, int rock) {
        return depth <= PLATE_DEPTH
                || (rock <= PIER_REACH && Math.floorMod(x, PIER_SPACING) == 0 && Math.floorMod(z, PIER_SPACING) == 0);
    }

    /** How far below {@code floorY} this column first meets something solid, looking no further than a pier could go. */
    private static int rockBelow(WorldGenLevel level, BlockPos.MutableBlockPos pos, int floorY) {
        if (Math.floorMod(pos.getX(), PIER_SPACING) != 0 || Math.floorMod(pos.getZ(), PIER_SPACING) != 0) {
            return NO_ROCK;
        }
        for (int depth = PLATE_DEPTH + 1; depth <= PIER_REACH && floorY - depth > level.getMinY(); depth++) {
            pos.setY(floorY - depth);
            if (!open(level.getBlockState(pos))) {
                return depth;
            }
        }
        return NO_ROCK;
    }

    /** Whether a block is no footing: air, a fluid, or something a floor would crush, like a fern. */
    public static boolean open(BlockState state) {
        return state.isAir() || state.liquid() || state.canBeReplaced();
    }

    /**
     * Foots every floor cell in {@code area}.
     *
     * @param floorY  the level of the floor being footed
     * @param floored whether a column has a floor of the hamlet's in it
     */
    public static void foot(WorldGenLevel level, BoundingBox area, int floorY, Predicate<BlockPos> floored) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = area.minX(); x <= area.maxX(); x++) {
            for (int z = area.minZ(); z <= area.maxZ(); z++) {
                pos.set(x, floorY, z);
                if (!floored.test(pos) || open(level.getBlockState(pos))) {
                    continue;
                }
                pos.setY(floorY - 1);
                if (!open(level.getBlockState(pos))) {
                    continue;
                }
                int rock = rockBelow(level, pos, floorY);
                for (int y = floorY - 1; y > level.getMinY(); y--) {
                    pos.setY(y);
                    if (!open(level.getBlockState(pos)) || !footed(x, z, floorY - y, rock)) {
                        break;
                    }
                    level.setBlock(pos, (x + y + z) % 5 == 0 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                            : Blocks.COBBLESTONE.defaultBlockState(), 2);
                }
            }
        }
    }
}
