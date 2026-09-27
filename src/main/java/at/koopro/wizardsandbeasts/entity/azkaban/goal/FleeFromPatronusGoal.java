package at.koopro.wizardsandbeasts.entity.azkaban.goal;

import at.koopro.wizardsandbeasts.entity.azkaban.DementorEntity;
import at.koopro.wizardsandbeasts.entity.spell.PatronusEntity;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import at.koopro.wizardsandbeasts.spell.patronus.PatronusDetection;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/**
 * Driven off by a Patronus: when a Patronus's ward ({@link PatronusDetection}) covers the Dementor, it is thrown back
 * and flees directly away from the Patronus — out past the edge of the ward and up — for {@link #LOCK_TICKS}. The
 * highest-priority goal, so it interrupts a pursuit, a drain or a Kiss mid-windup.
 *
 * <p>Previously it fled in a random direction (often straight through the Patronus) and counted its lockout down
 * twice a tick.
 */
public final class FleeFromPatronusGoal extends Goal {

    static final int LOCK_TICKS = 60;
    private static final double FLEE_SPEED = 1.4;

    private final DementorEntity dementor;
    private int lockout;
    private @Nullable Vec3 fleeTarget;

    public FleeFromPatronusGoal(DementorEntity dementor) {
        this.dementor = dementor;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        return !dementor.isDissipating() && PatronusDetection.wardingPatronus(dementor.level(), dementor.position()) != null;
    }

    @Override
    public boolean canContinueToUse() {
        return lockout > 0 && !dementor.isDissipating();
    }

    @Override
    public void start() {
        PatronusEntity patronus = PatronusDetection.wardingPatronus(dementor.level(), dementor.position());
        lockout = LOCK_TICKS;
        fleeTarget = patronus == null ? null : fleePoint(dementor.position(), patronus.position(),
                PatronusDetection.repelRadius(patronus));
        if (fleeTarget != null) {
            // Flung back first — it wheels round slowly, so the shove is what carries it out of the light.
            Vec3 shove = fleeTarget.subtract(dementor.position()).normalize().scale(0.7);
            dementor.setDeltaMovement(shove.x, 0.25, shove.z);
            dementor.hurtMarked = true;
        }
        dementor.setState(DementorEntity.State.REPELLED);
        dementor.playSound(ModSounds.DEMENTOR_REPELLED.get(), 1.0f, 0.9f + dementor.getRandom().nextFloat() * 0.2f);
    }

    /** Straight away from the Patronus, eight blocks past its ward, and a few blocks up. */
    static Vec3 fleePoint(Vec3 from, Vec3 patronus, double wardRadius) {
        Vec3 away = new Vec3(from.x - patronus.x, 0, from.z - patronus.z);
        if (away.lengthSqr() < 1.0e-4) {
            away = new Vec3(1, 0, 0);
        }
        away = away.normalize();
        double go = Math.max(0.0, wardRadius - from.distanceTo(patronus)) + 8.0;
        return from.add(away.scale(go)).add(0, 4, 0);
    }

    @Override
    public void stop() {
        dementor.clearState(DementorEntity.State.REPELLED);
        lockout = 0;
        fleeTarget = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        lockout--;
        if (fleeTarget != null) {
            dementor.getMoveControl().setWantedPosition(fleeTarget.x, fleeTarget.y, fleeTarget.z, FLEE_SPEED);
        }
    }
}
