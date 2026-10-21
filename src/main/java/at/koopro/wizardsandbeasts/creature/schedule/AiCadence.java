package at.koopro.wizardsandbeasts.creature.schedule;

import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NullMarked;

/**
 * How often a piece of creature behaviour runs. Every behaviour picks one tier and says so; the tier decides its
 * cost (documentation/CREATURE_AI_SCHEDULING.md).
 *
 * <ul>
 *   <li>{@link #FAST} — every tick. Movement-critical state only: steering, the look target, a pursuit's next step.
 *       No world queries; read what the slower tiers left behind.</li>
 *   <li>{@link #MEDIUM} — once a second. Sensing: one bounded entity query, classified in one pass.</li>
 *   <li>{@link #SLOW} — every five seconds. Environment: block searches, shelter, home, anything that touches many
 *       block states.</li>
 *   <li>{@link #EVENT} — never on a clock. Player interaction, damage, harvest, bonding, a spell or a death nearby:
 *       the event marks the creature awake ({@code AiWake}) and the next goal evaluation reacts.</li>
 * </ul>
 *
 * <p><b>Staggering.</b> A clocked tier is due when {@code (tickCount + entityId + offset) % period == 0}. Entity ids
 * are consecutive for creatures spawned or loaded together, so a herd or a freshly loaded chunk spreads its sensing
 * evenly over the period instead of all looking around on the same tick. {@link #SLOW} is offset half a
 * {@link #MEDIUM} period, so one creature never senses and searches on the same tick.
 *
 * <p><b>Goal parity.</b> Vanilla evaluates a mob's goals ({@code canUse}, {@code canContinueToUse}) only on ticks
 * where {@code tickCount + id} is even. Every clocked period and offset here is even, so a tier checked from a goal is
 * never silently skipped.
 */
@NullMarked
public enum AiCadence {

    FAST(1, 0),
    MEDIUM(20, 0),
    SLOW(100, 10),
    EVENT(0, 0);

    private final int period;
    private final int offset;

    AiCadence(int period, int offset) {
        this.period = period;
        this.offset = offset;
    }

    /** Ticks between runs; 0 for {@link #EVENT}, which never runs on a clock. */
    public int period() {
        return period;
    }

    public int offset() {
        return offset;
    }

    /** Whether this tier runs for {@code entity} on its current tick. */
    public boolean due(Entity entity) {
        return due(entity.tickCount, entity.getId());
    }

    /** Pure form of {@link #due(Entity)}. */
    public boolean due(int tickCount, int entityId) {
        if (period == 0) {
            return false;
        }
        return period == 1 || Math.floorMod(tickCount + entityId + offset, period) == 0;
    }

    /**
     * A staggered gate for any other period (an ability's 10- or 30-tick beat), half a period out of phase with
     * sensing so the two do not land together. Replaces {@code tickCount % period == 0}, which fires every creature
     * loaded in the same tick on the same tick.
     */
    public static boolean every(Entity entity, int period) {
        return every(entity.tickCount, entity.getId(), period);
    }

    /** Pure form of {@link #every(Entity, int)}. */
    public static boolean every(int tickCount, int entityId, int period) {
        if (period <= 1) {
            return true;
        }
        return Math.floorMod(tickCount + entityId + period / 2, period) == 0;
    }
}
