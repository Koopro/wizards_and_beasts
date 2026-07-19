package at.koopro.wizardsandbeasts.entity.creature.ai;

import at.koopro.wizardsandbeasts.creature.Trait;
import at.koopro.wizardsandbeasts.effect.BasiliskGazeLockEffect;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.registry.MiscItemRegistry;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * Basilisk-style death gaze for creatures carrying {@code Trait.DEATH_GAZE}. Players within range
 * with line of sight suffer a petrifying stare; a player who <em>meets</em> the gaze (looking back at
 * the creature) takes the full lethal effect. Looking away downgrades it to petrify-lite — the
 * canonical "avert your eyes" counterplay. A player with Blindness, a worn Blindfold, or an active
 * Protego ward is immune outright — they can't be gazed at (see {@link #isGazeImmune}).
 *
 * <p>Creatures additionally carrying {@code Trait.LETHAL_GAZE} (currently only the basilisk) get the
 * full canon split. Rather than triggering death/petrification the instant the gaze is met, a windup
 * ({@link BasiliskGazeLockEffect}) is applied first — an escalating heartbeat telegraph that gives a
 * player a window to break line of sight or don a Blindfold before it resolves on natural expiry (see
 * {@code BasiliskGazeWindupHandler}). Creatures without the trait (the lethifold) keep the original
 * instant debuff-only behaviour unchanged — the windup is specifically a lethal-gaze mechanic.
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
            if (isGazeImmune(player) || !mob.hasLineOfSight(player)) {
                continue;
            }
            Vec3 toMob = mobEye.subtract(player.getEyePosition()).normalize();
            double dot = player.getViewVector(1.0f).dot(toMob);
            boolean lethal = mob.has(Trait.LETHAL_GAZE);
            if (dot > MEET_DOT) {
                if (lethal) {
                    // Eyes met — begin the windup; BasiliskGazeWindupHandler resolves it to instant
                    // death on natural expiry (re-checking immunity once more at that point).
                    player.addEffect(new MobEffectInstance(ModEffects.BASILISK_GAZE_LOCK,
                            BasiliskGazeLockEffect.WINDUP_TICKS, 1, false, false));
                } else {
                    // Eyes met — lethal stare (debuff-only, e.g. lethifold).
                    player.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 1));
                    player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 4));
                    player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0));
                    player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 1));
                }
            } else if (dot > PERIPHERAL_DOT) {
                if (lethal) {
                    // Caught in the corner of the eye — begin the windup toward petrification.
                    player.addEffect(new MobEffectInstance(ModEffects.BASILISK_GAZE_LOCK,
                            BasiliskGazeLockEffect.WINDUP_TICKS, 0, false, false));
                } else {
                    // Caught in the corner of the eye — petrify-lite.
                    player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 3));
                    player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0));
                }
            }
        }
    }

    /** Blindness, a worn Blindfold, or an active Protego ward all make a player un-gazeable. */
    public static boolean isGazeImmune(Player player) {
        if (player.hasEffect(MobEffects.BLINDNESS) || player.hasEffect(ModEffects.PROTEGO_SHIELD)) {
            return true;
        }
        return player.getItemBySlot(EquipmentSlot.HEAD).is(MiscItemRegistry.BLINDFOLD.get());
    }

    private List<Player> nearbyPlayers() {
        return mob.level().getEntitiesOfClass(Player.class,
                mob.getBoundingBox().inflate(RANGE),
                p -> p.isAlive() && !p.isSpectator());
    }
}
