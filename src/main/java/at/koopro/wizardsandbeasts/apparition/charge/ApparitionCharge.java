package at.koopro.wizardsandbeasts.apparition.charge;

import at.koopro.wizardsandbeasts.apparition.ApparitionPhase;
import at.koopro.wizardsandbeasts.apparition.ApparitionPoint;
import at.koopro.wizardsandbeasts.apparition.ApparitionTier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * One attempt in flight. Transient and server-owned: the client is told what phase it is in, never asked.
 *
 * <p>The window bounds are frozen at {@link #ApparitionCharge construction} from the proficiency the player
 * had when they started, so a proficiency change mid-charge cannot move the goalposts under them.
 */
@NullMarked
public final class ApparitionCharge {

    private final ApparitionTier tier;
    private final int windowTicks;
    private final int windowOpen;
    private final int windowClose;
    /** The memorised destination for an anchored jump; {@code null} for a blink. */
    private final @Nullable ApparitionPoint anchor;

    private int elapsed;
    private int damageInstances;
    /** Last viable landing spot the raycast found, or {@code null} while the aim is invalid. */
    private @Nullable Vec3 destination;

    public ApparitionCharge(ApparitionTier tier, float proficiency, int windowFloorTicks,
                            @Nullable ApparitionPoint anchor) {
        this.tier = tier;
        this.windowTicks = ApparitionWindow.windowTicks(proficiency, windowFloorTicks);
        this.windowOpen = ApparitionWindow.windowOpen(tier);
        this.windowClose = ApparitionWindow.windowClose(tier, windowTicks);
        this.anchor = anchor;
        this.destination = anchor == null ? null : anchor.position();
    }

    public ApparitionTier tier() {
        return tier;
    }

    public @Nullable ApparitionPoint anchor() {
        return anchor;
    }

    public int windowTicks() {
        return windowTicks;
    }

    public int windowOpen() {
        return windowOpen;
    }

    public int windowClose() {
        return windowClose;
    }

    public int elapsed() {
        return elapsed;
    }

    public int damageInstances() {
        return damageInstances;
    }

    public @Nullable Vec3 destination() {
        return destination;
    }

    public void setDestination(@Nullable Vec3 value) {
        this.destination = value;
    }

    /**
     * Advances the Determination clock — but only once a destination exists.
     *
     * <p>The three Ds are taught in order, and this is that order made mechanical: Destination is fixed
     * first, and only then does Determination begin to run. Practically it means aiming costs nothing. The
     * clock and the aim used to be the same timer, so hunting for a spot burned the window you were hunting
     * it for, and a player who looked around for a second was torn apart for it.
     */
    public void advance() {
        if (destination == null) {
            return;
        }
        elapsed++;
    }

    public void recordDamage() {
        damageInstances++;
    }

    public ApparitionPhase phase() {
        return elapsed < windowOpen ? ApparitionPhase.DETERMINATION : ApparitionPhase.DELIBERATION;
    }

    /**
     * True once the attempt has been held so far past its window that it gives up on its own.
     *
     * <p>Well beyond {@code windowClose}, not one tick past it: a late release is now an ordinary miss that
     * scales with lateness, so this is the backstop for an attempt nobody ever releases rather than the
     * penalty for being slow.
     */
    public boolean isOverdue() {
        return elapsed > windowClose + ApparitionWindow.HARD_CAP_TICKS;
    }

    /** Raw miss for letting go right now, before destabilization inflates it. */
    public int missTicksOnRelease() {
        return ApparitionWindow.missTicks(elapsed, windowOpen, windowClose);
    }
}
