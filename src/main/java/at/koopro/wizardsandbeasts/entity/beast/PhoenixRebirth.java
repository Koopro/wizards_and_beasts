package at.koopro.wizardsandbeasts.entity.beast;

/**
 * The phoenix's burning: a server-side state machine, deliberately free of Minecraft types so the
 * transitions can be tested on their own.
 *
 * <p>{@code ALIVE → ASHES → RISING → ALIVE}. {@link #begin()} is the only way in and refuses unless the
 * bird is alive, so however many times the entity's death hook fires, one burning starts. {@link #tick()}
 * is the only clock: the phoenix calls it once per server tick and acts on the transition it returns,
 * so no timer lives anywhere else and nothing happens twice. The entity persists
 * {@link #phase()}/{@link #ticksLeft()} and restores them with {@link #restore}, so a burning that is saved
 * mid-way (a chunk unloaded, a world closed) resumes where it was rather than restarting or skipping.
 *
 * <p>Canon (Fantastic Beasts; Chamber of Secrets): when its body fails the phoenix bursts into flame and a
 * chick rises from the ashes. How long each stage takes is a gameplay choice: long enough to be seen and
 * to read as a death, short enough that the bird is back before a player loses interest.
 */
public final class PhoenixRebirth {

    public enum Phase { ALIVE, ASHES, RISING }

    /** What the owner of the machine must do on this tick. */
    public enum Transition { NONE, RISE, REBORN }

    /** The ash pile lies still this long. */
    public static final int ASHES_TICKS = 100;
    /** The bird unfolds out of the ashes over this long. */
    public static final int RISING_TICKS = 30;

    private Phase phase = Phase.ALIVE;
    private int ticksLeft;

    /** Starts a burning. False, and no change, unless the phoenix is alive: a burning cannot start twice. */
    public boolean begin() {
        if (phase != Phase.ALIVE) {
            return false;
        }
        phase = Phase.ASHES;
        ticksLeft = ASHES_TICKS;
        return true;
    }

    /** Advances one tick and reports the transition that happened on it, if any. */
    public Transition tick() {
        if (phase == Phase.ALIVE) {
            return Transition.NONE;
        }
        if (--ticksLeft > 0) {
            return Transition.NONE;
        }
        if (phase == Phase.ASHES) {
            phase = Phase.RISING;
            ticksLeft = RISING_TICKS;
            return Transition.RISE;
        }
        phase = Phase.ALIVE;
        ticksLeft = 0;
        return Transition.REBORN;
    }

    public Phase phase() {
        return phase;
    }

    public boolean burning() {
        return phase != Phase.ALIVE;
    }

    public int ticksLeft() {
        return ticksLeft;
    }

    /** Resumes a saved burning. Out-of-range values clamp, so a damaged save cannot wedge the bird. */
    public void restore(Phase saved, int ticks) {
        phase = saved;
        int cap = saved == Phase.ASHES ? ASHES_TICKS : saved == Phase.RISING ? RISING_TICKS : 0;
        ticksLeft = saved == Phase.ALIVE ? 0 : Math.max(1, Math.min(cap, ticks));
    }
}
