package at.koopro.wizardsandbeasts.ministry.trace;

import net.minecraft.core.BlockPos;
import org.jspecify.annotations.NullMarked;

/**
 * The geometry and timing of a posted case. Pure, so where a culprit hides and what a scene tells an Auror are
 * pinned by tests rather than by a run of the game.
 */
@NullMarked
public final class NpcCaseRules {

    /** Cases on the board at once. */
    public static final int BOARD_SIZE = 3;
    /** How long a case stays posted before the Office gives up on it: five in-game days. */
    public static final long POSTING_TICKS = 120000L;
    /** The fewest ticks between two postings, so a board emptied at once does not refill at once. */
    public static final long POSTING_INTERVAL_TICKS = 1200L;
    /** How far from the scene the culprit goes to ground. */
    public static final int HIDEOUT_MIN = 24;
    public static final int HIDEOUT_MAX = 48;
    /** How far from where the Auror stands a case may be posted. */
    public static final int POST_MIN = 200;
    public static final int POST_MAX = 600;
    /** How close the holder must be for the scene's height to be read off the ground. */
    public static final double SETTLE_REACH = 128.0;
    /** How close the holder must be to the hideout for the culprit to be there. */
    public static final double SPAWN_REACH = 48.0;
    /** Villagers this close to the scene saw it, and may be questioned. */
    public static final double WITNESS_RADIUS = 32.0;

    private NpcCaseRules() {}

    /** Where the culprit hid, {@code distance} blocks from the scene along {@code angle} radians. */
    public static BlockPos hideout(BlockPos scene, double angle, int distance) {
        int clamped = Math.max(HIDEOUT_MIN, Math.min(HIDEOUT_MAX, distance));
        return new BlockPos(scene.getX() + (int) Math.round(Math.cos(angle) * clamped), scene.getY(),
                scene.getZ() + (int) Math.round(Math.sin(angle) * clamped));
    }

    /**
     * The compass point from one place to another, as a lang suffix: {@code north}, {@code north_east}, … Minecraft's
     * north is negative Z.
     */
    public static String bearing(int dx, int dz) {
        if (dx == 0 && dz == 0) {
            return "here";
        }
        double degrees = Math.toDegrees(Math.atan2(dx, -dz));
        int sector = (int) Math.floorMod(Math.round(degrees / 45.0), 8L);
        return switch (sector) {
            case 0 -> "north";
            case 1 -> "north_east";
            case 2 -> "east";
            case 3 -> "south_east";
            case 4 -> "south";
            case 5 -> "south_west";
            case 6 -> "west";
            default -> "north_west";
        };
    }

    /** How far something is, in words a witness would use, as a lang suffix. */
    public static String distanceBand(double distance) {
        if (distance < 32.0) {
            return "close";
        }
        if (distance < 96.0) {
            return "near";
        }
        if (distance < 256.0) {
            return "far";
        }
        return "distant";
    }
}
