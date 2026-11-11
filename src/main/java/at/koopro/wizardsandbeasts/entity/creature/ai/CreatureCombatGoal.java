package at.koopro.wizardsandbeasts.entity.creature.ai;

import at.koopro.wizardsandbeasts.creature.profile.CombatProfile;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Melee for a creature that declares a {@link CombatProfile}: the fight {@link CombatRhythm} describes, carried out
 * with navigation, a telegraph and a blow.
 *
 * <p>It takes the place of vanilla's {@code MeleeAttackGoal} for those creatures only. What a player gains is a
 * blow that can be read and answered: the creature stops, shows its windup, and strikes where the target was; an
 * ambusher lies still until it is stepped on; a harrier leaves after it bites; a skirmisher will not be closed
 * with; a creature with a retreat threshold runs when it is losing.
 */
public final class CreatureCombatGoal extends Goal {

    /** Vanilla's horizontal melee reach beyond the attacker's own box ({@code Mob.DEFAULT_ATTACK_REACH}). */
    private static final double MELEE_REACH = 0.83;
    private static final double PACK_RADIUS = 12.0;

    private final GenericBeastEntity mob;
    private final CombatProfile profile;
    private final CombatRhythm rhythm;
    /** CHARGE creatures close faster than their profile says, as they did under the vanilla goal. */
    private final double minimumApproach;

    private int repath;
    private int allies;
    private int alliesAge;
    /** Which way round the target it circles; fixed per creature so a pack spreads. */
    private final int side;

    public CreatureCombatGoal(GenericBeastEntity mob, CombatProfile profile, double minimumApproach) {
        this.mob = mob;
        this.profile = profile;
        this.rhythm = new CombatRhythm(profile);
        this.minimumApproach = minimumApproach;
        this.side = (mob.getId() & 1) == 0 ? 1 : -1;
        // The body only. A ranged goal above this one holds the gaze whenever its target is in its band; claiming
        // the gaze too would lock this goal out for as long as the creature can shoot (seen in a client 2026-11-11:
        // a giant that never walked in, a centaur that never took its distance). Both look at the same target.
        setFlags(EnumSet.of(Flag.MOVE));
    }

    /** Where the fight stands, for tests and the debug report. */
    public CombatRhythm.Phase phase() {
        return rhythm.phase();
    }

    @Override
    public boolean canUse() {
        if (!ModuleManager.isEnabled(Module.CREATURES)) {
            return false;
        }
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive() || !ModuleManager.isEnabled(Module.CREATURES)) {
            return false;
        }
        return !(target instanceof Player player && (player.isSpectator() || player.isCreative()));
    }

    @Override
    public void start() {
        rhythm.engage();
        mob.setAggressive(true);
        repath = 0;
        alliesAge = 0;
        allies = 0;
    }

    @Override
    public void stop() {
        LivingEntity target = mob.getTarget();
        if (!EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target)) {
            mob.setTarget(null);
        }
        mob.setAggressive(false);
        mob.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;   // windup and recovery are counted in real ticks
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return;
        }
        mob.getLookControl().setLookAt(target, 30.0f, 30.0f);
        boolean sees = mob.getSensing().hasLineOfSight(target);
        double lunge = CombatRhythm.lunge(profile.style());
        CombatRhythm.Step step = rhythm.step(new CombatRhythm.Input(
                mob.distanceTo(target),
                sees && mob.isWithinMeleeAttackRange(target),
                sees && reaches(target, lunge),
                mob.getHealth() / Math.max(1.0f, mob.getMaxHealth()),
                provokedBy(target),
                packBeside(target)));

        switch (step.event()) {
            case TELEGRAPH -> mob.telegraphBlow();
            case STRIKE -> strike(target);
            case WHIFF -> {
                lungeAt(target);
                mob.swing(InteractionHand.MAIN_HAND);
                mob.swingAtNothing();
            }
            case DISENGAGE -> {
                mob.setTarget(null);
                return;
            }
            default -> { }
        }
        move(step, target);
    }

    private void strike(LivingEntity target) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        if (profile.windupTicks() > 0) {
            lungeAt(target);
        }
        mob.swing(InteractionHand.MAIN_HAND);
        mob.doHurtTarget(level, target);
    }

    /** A committed blow carries the body forward, so the lunge is seen and not only counted. */
    private void lungeAt(LivingEntity target) {
        if (!mob.onGround() || CombatRhythm.lunge(profile.style()) < 1.0) {
            return;
        }
        Vec3 to = new Vec3(target.getX() - mob.getX(), 0.0, target.getZ() - mob.getZ());
        if (to.lengthSqr() > 1.0e-4) {
            Vec3 push = to.normalize().scale(0.45);
            mob.setDeltaMovement(mob.getDeltaMovement().add(push.x, 0.18, push.z));
        }
    }

    private void move(CombatRhythm.Step step, LivingEntity target) {
        switch (step.move()) {
            case STAND -> {
                mob.getNavigation().stop();
                repath = 0;
            }
            case APPROACH -> {
                if (--repath <= 0) {
                    repath = 4 + mob.getRandom().nextInt(7);
                    if (!mob.getNavigation().moveTo(target, Math.max(minimumApproach, step.speed()))) {
                        repath += 15;
                    }
                }
            }
            case STATION -> {
                // Each leg is walked to its end before the next is planned. Re-planning twice a second left
                // every leg a quarter done, and a wide body's path start is offset by half a block each time:
                // the centaur spiralled in to six blocks instead of holding ten (traced 2026-11-11).
                if (--repath <= 0 || mob.getNavigation().isDone()) {
                    repath = 30;
                    Vec3 spot = station(target, step.range());
                    mob.getNavigation().moveTo(spot.x, spot.y, spot.z, step.speed());
                }
            }
            case AWAY -> {
                if (--repath <= 0 || mob.getNavigation().isDone()) {
                    repath = 20;
                    Vec3 away = DefaultRandomPos.getPosAway(mob, 14, 6, target.position());
                    if (away != null) {
                        mob.getNavigation().moveTo(away.x, away.y, away.z, step.speed());
                    }
                }
            }
        }
    }

    /**
     * A point {@code range} blocks from the target, a little round from where the creature stands now. Moving to it
     * again and again backs the creature off when it is too close, brings it in when it is too far, and walks it
     * round the target when it is at the right distance.
     */
    private Vec3 station(LivingEntity target, double range) {
        Vec3 out = new Vec3(mob.getX() - target.getX(), 0.0, mob.getZ() - target.getZ());
        if (out.lengthSqr() < 1.0e-4) {
            out = new Vec3(1.0, 0.0, 0.0);
        }
        // Well off its distance it goes straight out or straight in; near it, it walks round.
        boolean offRange = Math.abs(Math.sqrt(out.lengthSqr()) - range) > 2.0;
        Vec3 turned = out.normalize().yRot(offRange ? 0.0f : side * 0.45f).scale(range);
        return new Vec3(target.getX() + turned.x, target.getY(), target.getZ() + turned.z);
    }

    private boolean reaches(LivingEntity target, double extra) {
        double reach = MELEE_REACH + extra;
        return mob.getBoundingBox().inflate(reach, 0.5, reach).intersects(target.getBoundingBox());
    }

    private boolean provokedBy(LivingEntity target) {
        return mob.getLastHurtByMob() == target && mob.tickCount - mob.getLastHurtByMobTimestamp() < 100;
    }

    /** Others of its kind near it with the same quarry. Counted once a second: it only changes what follows a blow. */
    private int packBeside(LivingEntity target) {
        if (profile.style() != CombatProfile.Style.PACK_HUNTER) {
            return 0;
        }
        if (--alliesAge <= 0) {
            alliesAge = 20;
            allies = mob.level().getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(PACK_RADIUS),
                    other -> other != mob && other.getType() == mob.getType() && other.isAlive()
                            && other.getTarget() == target).size();
        }
        return allies;
    }
}
