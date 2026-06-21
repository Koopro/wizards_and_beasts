package at.koopro.wizardsandbeasts.entity.creature.ai;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * Basilisk-style death gaze for creatures carrying {@code Trait.DEATH_GAZE}. Players within range
 * with line of sight suffer a petrifying stare; a player who <em>meets</em> the gaze (looking back at
 * the creature) takes the full lethal effect (wither + heavy slow + blind + weakness). Looking away
 * downgrades it to petrify-lite — the canonical "avert your eyes" counterplay.
 *
 * <p>Reusable trait, no per-creature class. Applied to the basilisk; future cockatrice/gorgon-type
 * beasts can opt in via the same trait.
 */
public final class DeathGazeGoal extends Goal {

    private static final double RANGE = 12.0;
    private static final double MEET_DOT = 0.6;      // player looking roughly at the creature
    private static final double PERIPHERAL_DOT = 0.1;
    private static final int INTERVAL = 20;

    private final GenericBeastEntity mob;
    private int tick;

    public DeathGazeGoal(GenericBeastEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        return !nearbyPlayers().isEmpty();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void tick() {
        if (++tick % INTERVAL != 0) {
            return;
        }
        Vec3 mobEye = mob.getEyePosition();
        for (Player player : nearbyPlayers()) {
            if (!mob.hasLineOfSight(player)) {
                continue;
            }
            Vec3 toMob = mobEye.subtract(player.getEyePosition()).normalize();
            double dot = player.getViewVector(1.0f).dot(toMob);
            if (dot > MEET_DOT) {
                // Eyes met — lethal stare.
                player.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 1));
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 4));
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0));
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 1));
            } else if (dot > PERIPHERAL_DOT) {
                // Caught in the corner of the eye — petrify-lite.
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 3));
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0));
            }
        }
    }

    private List<Player> nearbyPlayers() {
        return mob.level().getEntitiesOfClass(Player.class,
                mob.getBoundingBox().inflate(RANGE),
                p -> p.isAlive() && !p.isSpectator());
    }
}
