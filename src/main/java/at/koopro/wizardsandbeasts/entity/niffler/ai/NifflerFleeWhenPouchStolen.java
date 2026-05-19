package at.koopro.wizardsandbeasts.entity.niffler.ai;

import at.koopro.wizardsandbeasts.entity.niffler.NifflerEntity;
import at.koopro.wizardsandbeasts.event.bestiary.niffler.NifflerAngryEvent;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;

import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Forces the Niffler to flee when its pouch is accessed by an unbonded player (bond < 50).
 * Active within 60 ticks of the last unauthorized pouch access.
 */
public class NifflerFleeWhenPouchStolen extends Goal {

    private static final double FLEE_RADIUS = 20.0;

    private final NifflerEntity niffler;
    @Nullable private Player fleeTarget;

    public NifflerFleeWhenPouchStolen(NifflerEntity niffler) {
        this.niffler = niffler;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (niffler.getBondLevel() >= 50) return false;
        if (niffler.getTicksSincePouchAccess() <= 0 || niffler.getTicksSincePouchAccess() > 60) return false;
        fleeTarget = findOffendingPlayer();
        return fleeTarget != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (fleeTarget == null || !fleeTarget.isAlive()) return false;
        return niffler.distanceToSqr(fleeTarget) < FLEE_RADIUS * FLEE_RADIUS;
    }

    @Override
    public void start() {
        NifflerAngryEvent event = new NifflerAngryEvent(niffler, fleeTarget);
        if (!NeoForge.EVENT_BUS.post(event).isCanceled()) {
            fleeAwayFrom(fleeTarget);
        }
    }

    @Override
    public void tick() {
        fleeAwayFrom(fleeTarget);
    }

    @Override
    public void stop() {
        fleeTarget = null;
        niffler.getNavigation().stop();
    }

    private void fleeAwayFrom(@Nullable Player player) {
        if (player == null) return;
        double dx = niffler.getX() - player.getX();
        double dz = niffler.getZ() - player.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.01) return;
        double targetX = niffler.getX() + (dx / len) * FLEE_RADIUS;
        double targetZ = niffler.getZ() + (dz / len) * FLEE_RADIUS;
        niffler.getNavigation().moveTo(targetX, niffler.getY(), targetZ, 1.5);
    }

    @Nullable
    private Player findOffendingPlayer() {
        AABB area = niffler.getBoundingBox().inflate(FLEE_RADIUS);
        List<Player> players = niffler.level().getEntitiesOfClass(Player.class, area,
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator());
        return players.stream()
                .min(Comparator.comparingDouble(niffler::distanceToSqr))
                .orElse(null);
    }
}
