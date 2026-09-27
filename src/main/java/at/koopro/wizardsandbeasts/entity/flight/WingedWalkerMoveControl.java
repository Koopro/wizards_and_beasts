package at.koopro.wizardsandbeasts.entity.flight;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.MoveControl;

/**
 * A winged walker walks like a horse and flies like a bird, and vanilla has a control for each: this picks one.
 *
 * <p>Every call is forwarded to the ground {@link MoveControl} unless {@link WingedWalker#isFlying()}, when it goes
 * to a {@link FlyingMoveControl}. Nothing is reimplemented — the two vanilla controls do all the steering; the point
 * is that a walking hippogriff or thestral has gravity and a flying one does not, and a single
 * {@code FlyingMoveControl} (what every generic flier uses) would float it over every ledge it walked off.
 */
public final class WingedWalkerMoveControl<T extends Mob & WingedWalker> extends MoveControl {

    private final T creature;
    private final MoveControl ground;
    private final FlyingMoveControl air;
    private boolean wasFlying;

    public WingedWalkerMoveControl(T creature) {
        super(creature);
        this.creature = creature;
        this.ground = new MoveControl(creature);
        this.air = new FlyingMoveControl(creature, 10, false);
    }

    private MoveControl active() {
        return creature.isFlying() ? air : ground;
    }

    @Override
    public void setWantedPosition(double x, double y, double z, double speed) {
        active().setWantedPosition(x, y, z, speed);
    }

    @Override
    public void strafe(float forward, float strafe) {
        ground.strafe(forward, strafe);
    }

    @Override
    public boolean hasWanted() {
        return active().hasWanted();
    }

    @Override
    public double getSpeedModifier() {
        return active().getSpeedModifier();
    }

    @Override
    public double getWantedX() {
        return active().getWantedX();
    }

    @Override
    public double getWantedY() {
        return active().getWantedY();
    }

    @Override
    public double getWantedZ() {
        return active().getWantedZ();
    }

    @Override
    public void tick() {
        boolean flying = creature.isFlying();
        if (wasFlying && !flying) {
            // Leaving the air: gravity back on, and the flight control's last target forgotten.
            creature.setNoGravity(false);
            creature.setYya(0.0F);
        }
        wasFlying = flying;
        active().tick();
    }
}
