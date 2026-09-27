package at.koopro.wizardsandbeasts.entity.azkaban.goal;

import at.koopro.wizardsandbeasts.azkaban.AzkabanDamageTypes;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.entity.azkaban.DementorAura;
import at.koopro.wizardsandbeasts.entity.azkaban.DementorEntity;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/**
 * The Dementor's Kiss — a server-side state machine.
 *
 * <p>{@code WINDUP} ({@value #WINDUP_TICKS} ticks): it closes in, hood lowering, hands either side of the victim's
 * head. {@code RESOLVE} ({@value #RESOLVE_TICKS} ticks): the hood closes over the face. Then the soul goes:
 * {@code SOUL_DRAINED}, which nothing cures, and the Kiss's damage. Canon calls it worse than death, so it is kept
 * rare on purpose:
 * <ul>
 *   <li>only a creature with a soul (a player or a person), already weakened — low on health, under the deepest chill
 *       (a swarm at arm's length), asleep, or a marked Azkaban trespasser;</li>
 *   <li>never one already Kissed, and never one another Dementor has claimed — two Dementors cannot Kiss one
 *       victim, and nothing is applied twice;</li>
 *   <li>a Patronus warding either of them breaks it off at once ({@link FleeFromPatronusGoal} outranks this goal), as
 *       does the victim getting out of reach;</li>
 *   <li>after a Kiss, {@link DementorEntity#KISS_COOLDOWN}; after a broken-off one, a shorter pause.</li>
 * </ul>
 */
public final class DementorKissGoal extends Goal {

    static final int WINDUP_TICKS = 30;
    static final int RESOLVE_TICKS = 15;
    /** How close it must be when the windup ends. */
    static final double KISS_REACH = 1.8;
    /** Interrupted Kisses wait this long before another attempt. */
    static final int BROKEN_OFF_PAUSE = 100;
    /** Essentially permanent; a Kiss is not undone. Kept finite so the effect still ticks. */
    public static final int SOUL_DRAIN_DURATION = Integer.MAX_VALUE / 20;
    static final float KISS_DAMAGE = 4.0f;

    private enum Phase { NONE, WINDUP, RESOLVE, DONE }

    private final DementorEntity dementor;
    private @Nullable LivingEntity target;
    private Phase phase = Phase.NONE;
    private int timer;

    public DementorKissGoal(DementorEntity dementor) {
        this.dementor = dementor;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (dementor.kissCooldown() > 0 || dementor.isDissipating()) return false;
        LivingEntity candidate = dementor.sensed();
        if (candidate == null || dementor.distanceTo(candidate) > DementorAura.DRAIN_RADIUS
                || !canBeKissed(candidate) || dementor.kissClaimedByAnother(candidate)) {
            return false;
        }
        target = candidate;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity t = target;
        return (phase == Phase.WINDUP || phase == Phase.RESOLVE) && t != null && dementor.canFeel(t)
                && dementor.distanceTo(t) <= DementorAura.DRAIN_RADIUS + 1.0 && !dementor.isDissipating();
    }

    @Override
    public void start() {
        phase = Phase.WINDUP;
        timer = WINDUP_TICKS;
        dementor.claimKiss(target);
        dementor.setState(DementorEntity.State.KISS_WINDUP);
        dementor.playSound(ModSounds.DEMENTOR_KISS.get(), 1.0f, 0.8f);
    }

    @Override
    public void stop() {
        if (phase != Phase.DONE && dementor.kissCooldown() == 0) {
            dementor.startKissCooldown(BROKEN_OFF_PAUSE);
        }
        phase = Phase.NONE;
        target = null;
        dementor.claimKiss(null);
        dementor.clearState(DementorEntity.State.KISS_WINDUP);
        dementor.clearState(DementorEntity.State.KISS_RESOLVE);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity t = target;
        if (t == null) return;
        dementor.getLookControl().setLookAt(t, 10f, 10f);
        if (phase == Phase.WINDUP) {
            Vec3 close = closeIn(dementor.position(), t.position());
            dementor.getMoveControl().setWantedPosition(close.x, close.y, close.z, 0.6);
            if (--timer <= 0) {
                if (dementor.distanceTo(t) <= KISS_REACH) {
                    phase = Phase.RESOLVE;
                    timer = RESOLVE_TICKS;
                    dementor.setState(DementorEntity.State.KISS_RESOLVE);
                } else {
                    phase = Phase.NONE;   // it did not reach them; canContinueToUse ends the goal
                }
            }
        } else if (phase == Phase.RESOLVE) {
            dementor.getMoveControl().setWantedPosition(dementor.getX(), dementor.getY(), dementor.getZ(), 0.1);
            if (--timer <= 0) {
                boolean landed = dementor.level() instanceof ServerLevel sl && kiss(sl, dementor, t);
                dementor.startKissCooldown(landed ? DementorEntity.KISS_COOLDOWN : BROKEN_OFF_PAUSE);
                phase = Phase.DONE;
            }
        }
    }

    /** Where it moves during the windup: a block short of the victim. */
    static Vec3 closeIn(Vec3 dementor, Vec3 victim) {
        Vec3 back = new Vec3(dementor.x - victim.x, 0, dementor.z - victim.z);
        back = back.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : back.normalize();
        return victim.add(back).add(0, DementorPursueGoal.HEM_LIFT, 0);
    }

    /**
     * Takes the soul: applies {@code SOUL_DRAINED} and the Kiss's damage, once. Re-checks everything at the moment it
     * lands, so a victim warded, moved off or already Kissed in the last tick keeps their soul.
     *
     * @return whether the Kiss landed
     */
    public static boolean kiss(ServerLevel level, DementorEntity dementor, LivingEntity victim) {
        if (!victim.isAlive() || !canBeKissed(victim) || !dementor.canFeel(victim)
                || dementor.distanceTo(victim) > KISS_REACH + 0.5) {
            return false;
        }
        victim.addEffect(new MobEffectInstance(ModEffects.SOUL_DRAINED, SOUL_DRAIN_DURATION, 0, false, true, true));
        victim.hurtServer(level, level.damageSources().source(AzkabanDamageTypes.DEMENTOR_KISS, dementor), KISS_DAMAGE);
        Vec3 from = victim.getEyePosition();
        level.sendParticles(ParticleTypes.SOUL, from.x, from.y, from.z, 12, 0.2, 0.2, 0.2, 0.03);
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), ModSounds.DEMENTOR_DRAIN.get(),
                dementor.getSoundSource(), 1.0f, 0.5f);
        return true;
    }

    /** A soul to take, not already taken, and a victim too weak to resist. */
    public static boolean canBeKissed(LivingEntity victim) {
        if (!DementorAura.canFeedOn(victim) || !DementorAura.hasSoul(victim)
                || victim.hasEffect(ModEffects.SOUL_DRAINED)) {
            return false;
        }
        boolean lowHealth = victim.getHealth() <= victim.getMaxHealth() * 0.3f;
        MobEffectInstance chill = victim.getEffect(ModEffects.DEMENTOR_CHILL);
        boolean deepChill = chill != null && chill.getAmplifier() >= DementorAura.MAX_AMPLIFIER;
        boolean trespasser = victim instanceof Player player
                && player.getData(ModAttachments.AZKABAN_TRESPASSER_TAG.get()).tagged();
        return lowHealth || deepChill || victim.isSleeping() || trespasser;
    }
}
