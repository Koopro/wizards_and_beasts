package at.koopro.wizardsandbeasts.creature.bond;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.function.Predicate;

/**
 * Follow the bonded owner once the bond is past the species' {@code followThreshold}.
 *
 * <p>The generic form of {@code NifflerFollowBondedPlayerGoal}, whose start/stop distances (6 and 2
 * blocks) and speed (1.0) it keeps. Those numbers are not in the {@link BondProfile}: they are how
 * a companion should trail a player, not a fact about the species, and every creature that has
 * wanted them so far has wanted the same ones.
 *
 * <p>{@link #canUse()} re-reads the module flag every time it is asked, so switching
 * {@code Module.CREATURES} off stops the following without the goal having to be unregistered —
 * the same self-gating contract the ability goals hold.
 *
 * @param <T> the creature, which must be both a {@link PathfinderMob} and a {@link BondableBeast}
 */
@NullMarked
public class FollowBondedOwnerGoal<T extends PathfinderMob & BondableBeast> extends Goal {

    private static final double STOP_DISTANCE = 2.0;
    private static final double START_DISTANCE = 6.0;
    private static final double FOLLOW_SPEED = 1.0;

    private final T beast;

    /**
     * Extra condition on top of the bond, for creatures with a state in which following makes no
     * sense — a pocketed Niffler is inside the player and must not also be pathing to them.
     */
    private final Predicate<T> extraCondition;

    @Nullable
    private Player owner;

    public FollowBondedOwnerGoal(T beast) {
        this(beast, b -> true);
    }

    public FollowBondedOwnerGoal(T beast, Predicate<T> extraCondition) {
        this.beast = beast;
        this.extraCondition = extraCondition;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!ModuleManager.isEnabled(Module.CREATURES) || !extraCondition.test(beast)) {
            return false;
        }
        if (!beast.followsOwner()) {
            return false;
        }
        owner = beast.resolveBondOwner();
        if (owner == null || !owner.isAlive() || owner.isSpectator()) {
            return false;
        }
        return beast.distanceToSqr(owner) > START_DISTANCE * START_DISTANCE;
    }

    @Override
    public boolean canContinueToUse() {
        if (owner == null || !owner.isAlive() || !extraCondition.test(beast)) {
            return false;
        }
        return beast.distanceToSqr(owner) > STOP_DISTANCE * STOP_DISTANCE;
    }

    @Override
    public void tick() {
        if (owner == null) {
            return;
        }
        beast.getLookControl().setLookAt(owner, 10.0f, beast.getMaxHeadXRot());
        beast.getNavigation().moveTo(owner, FOLLOW_SPEED);
    }

    @Override
    public void stop() {
        owner = null;
        beast.getNavigation().stop();
    }
}
