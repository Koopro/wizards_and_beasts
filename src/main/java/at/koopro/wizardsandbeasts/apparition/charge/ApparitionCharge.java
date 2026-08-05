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

    public void advance() {
        elapsed++;
    }

    public void recordDamage() {
        damageInstances++;
    }

    public ApparitionPhase phase() {
        return elapsed < windowOpen ? ApparitionPhase.DETERMINATION : ApparitionPhase.DELIBERATION;
    }

    /** True once the window has closed unreleased — the attempt discharges itself. */
    public boolean isOverdue() {
        return elapsed > windowClose;
    }

    /** Raw miss for letting go right now, before destabilization inflates it. */
    public int missTicksOnRelease() {
        return ApparitionWindow.missTicks(elapsed, windowOpen, windowClose);
    }
}
