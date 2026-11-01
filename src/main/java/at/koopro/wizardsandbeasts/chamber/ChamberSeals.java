package at.koopro.wizardsandbeasts.chamber;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A seal or gate is several blocks acting as one: the doorway's blocks touching each other, face to face. What opens one
 * opens them all.
 */
@NullMarked
final class ChamberSeals {

    /** Far more than any doorway in a Chamber; bounds the walk if someone builds a wall of them. */
    static final int MAX_GROUP = 64;

    private ChamberSeals() {}

    /** The blocks of {@code block} connected face to face with {@code origin}, origin first. */
    static List<BlockPos> group(Level level, BlockPos origin, Block block) {
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin.immutable());
        seen.add(origin.immutable());
        while (!queue.isEmpty() && out.size() < MAX_GROUP) {
            BlockPos pos = queue.poll();
            out.add(pos);
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (seen.add(next) && level.getBlockState(next).is(block)) {
                    queue.add(next);
                }
            }
        }
        return out;
    }
}
