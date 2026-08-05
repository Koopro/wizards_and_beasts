package at.koopro.wizardsandbeasts.apparition.charge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Whether a spot can be arrived at. A wizard aiming at the inside of a mountain does not lock a destination
 * — the aim simply never becomes viable, which is why an invalid target produces no attempt rather than a
 * splinch.
 */
@NullMarked
public final class ApparitionLanding {

    /** How far below the aimed block to look for footing, so aiming at the air above a floor still works. */
    private static final int GROUND_SEARCH_DEPTH = 3;

    private ApparitionLanding() {}

    /**
     * Resolves an aimed point to a spot the player actually fits in, or {@code null} when there is none.
     *
     * @return the centre of the viable block, or {@code null} if unloaded, obstructed or unsupported
     */
    public static @Nullable Vec3 resolve(ServerLevel level, ServerPlayer player, Vec3 aim) {
        BlockPos aimed = BlockPos.containing(aim);
        if (!level.isLoaded(aimed)) {
            return null;
        }
        for (int dy = 0; dy >= -GROUND_SEARCH_DEPTH; dy--) {
            BlockPos foot = aimed.offset(0, dy, 0);
            if (!level.isLoaded(foot)) {
                return null;
            }
            Vec3 candidate = new Vec3(foot.getX() + 0.5, foot.getY(), foot.getZ() + 0.5);
            if (!fits(level, player, candidate)) {
                continue;
            }
            if (!isSupported(level, foot.below())) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    /** True when the player's own hitbox clears everything at {@code position} — headroom included. */
    public static boolean fits(ServerLevel level, ServerPlayer player, Vec3 position) {
        AABB box = player.getBoundingBox().move(position.subtract(player.position()));
        return level.noCollision(player, box);
    }

    /** True when {@code below} can be stood on, so a wizard does not arrive in mid-air over a chasm. */
    private static boolean isSupported(ServerLevel level, BlockPos below) {
        return !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
    }
}
