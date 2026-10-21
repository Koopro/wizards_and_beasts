package at.koopro.wizardsandbeasts.creature.schedule;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.NullMarked;

/**
 * Paces re-pathing towards a moving target, the way vanilla's {@code MeleeAttackGoal} does: a new path only once a
 * short delay has passed (longer when the target is far away, where a path's last steps matter least) <em>and</em>
 * the target has moved at least a block since the last one or the current path has run out; a search that found no
 * path waits {@link #FAILED_PATH_PENALTY} ticks more.
 *
 * <p>Without it, {@code navigation.moveTo(target)} every tick computes a fresh path whenever the target crosses a
 * block boundary — several A* searches a second per chasing creature. The chase a player sees is the same: the
 * pursuer still closes, turns and bites; it simply steers along a path up to a few ticks old, as every vanilla
 * hostile mob does.
 *
 * <p><b>Cost:</b> four fields, no allocation, no world access; at most one path request every 4–20 ticks per pursuer
 * (more after a search that found nothing).
 */
@NullMarked
public final class AiPursuit {

    /** Shortest wait between two paths towards a target that keeps moving. */
    public static final int MIN_DELAY = 4;
    /** Extra wait after a search that found no path (vanilla's figure). */
    public static final int FAILED_PATH_PENALTY = 15;

    private long nextAt;
    private double lastX = Double.NaN;
    private double lastY;
    private double lastZ;

    /**
     * Whether {@code mob} should ask its navigation for a new path to {@code target} now. Call every tick while
     * pursuing; when it answers true, call {@code navigation.moveTo(target, speed)}, and {@link #failed} if that
     * returns false.
     */
    public boolean shouldRepath(Mob mob, Entity target, long now) {
        boolean first = Double.isNaN(lastX);
        if (!first && now < nextAt) {
            return false; // the delay holds whatever the path is doing, as in vanilla
        }
        boolean moved = first || target.distanceToSqr(lastX, lastY, lastZ) >= 1.0;
        if (!moved && !mob.getNavigation().isDone()) {
            return false; // still on a good path to where the target is
        }
        lastX = target.getX();
        lastY = target.getY();
        lastZ = target.getZ();
        nextAt = now + delay(mob.distanceToSqr(target), mob.getId());
        AiMetrics.inc(AiMetrics.Metric.PATH_REQUEST);
        return true;
    }

    /**
     * The navigation found no path: wait {@link #FAILED_PATH_PENALTY} ticks more before the next try, as vanilla
     * does, instead of re-running a search that just failed.
     */
    public void failed(long now) {
        nextAt = Math.max(nextAt, now) + FAILED_PATH_PENALTY;
    }

    /** Forget the last target, so a new pursuit paths at once. */
    public void reset() {
        lastX = Double.NaN;
        nextAt = 0L;
    }

    /** The wait after a path: vanilla's 4–10 ticks (here spread by entity id, not a random roll), +5 past 16 blocks, +10 past 32. Pure. */
    public static int delay(double distanceSqr, int entityId) {
        int delay = MIN_DELAY + Math.floorMod(entityId, 7);
        if (distanceSqr > 32 * 32) {
            delay += 10;
        } else if (distanceSqr > 16 * 16) {
            delay += 5;
        }
        return delay;
    }
}
