package at.koopro.wizardsandbeasts.entity.creature;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;

/**
 * Walks like a land animal and swims like a fish, and vanilla has a control for each: this picks one.
 *
 * <p>Every call goes to a {@link SmoothSwimmingMoveControl} while the mob is in water and to a plain {@link MoveControl}
 * on land. Nothing is reimplemented — the sibling of {@code WingedWalkerMoveControl}, for water instead of air. Paired
 * with vanilla's {@code AmphibiousPathNavigation}, which already plans across both.
 *
 * <p>A single swimming control (what {@code GenericAquaticBeastEntity} gives every swimmer) cannot walk: on land it
 * never sets forward speed with gravity in mind and the creature slides or stands; a single ground control sinks
 * like a stone in water.
 */
public final class AmphibiousMoveControl extends MoveControl {

    private final MoveControl land;
    private final SmoothSwimmingMoveControl water;
    private boolean wasInWater;

    public AmphibiousMoveControl(Mob mob) {
        super(mob);
        this.land = new MoveControl(mob);
        this.water = new SmoothSwimmingMoveControl(mob, 85, 10, 0.1f, 0.5f, false);
    }

    private MoveControl active() {
        return mob.isInWater() ? water : land;
    }

    @Override
    public void setWantedPosition(double x, double y, double z, double speed) {
        active().setWantedPosition(x, y, z, speed);
    }

    @Override
    public void strafe(float forward, float strafe) {
        land.strafe(forward, strafe);
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
        boolean inWater = mob.isInWater();
        if (inWater != wasInWater) {
            // Hand the destination over, so crossing the waterline does not drop the route.
            MoveControl from = wasInWater ? water : land;
            MoveControl to = inWater ? water : land;
            if (from.hasWanted()) {
                to.setWantedPosition(from.getWantedX(), from.getWantedY(), from.getWantedZ(), from.getSpeedModifier());
            }
            mob.setNoGravity(false);
            wasInWater = inWater;
        }
        active().tick();
    }
}
