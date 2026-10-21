package at.koopro.wizardsandbeasts.creature.ecology;

import at.koopro.wizardsandbeasts.creature.schedule.AiBackoff;
import at.koopro.wizardsandbeasts.creature.schedule.AiCadence;
import at.koopro.wizardsandbeasts.creature.schedule.AiMetrics;
import at.koopro.wizardsandbeasts.creature.schedule.AiPursuit;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

/**
 * Carries out what {@link EcologyBrain} decided. One goal per creature, registered once at construction and reading
 * the profile live, so a datapack reload reaches creatures already in the world — the same rule
 * {@code FollowBondedOwnerGoal} follows.
 *
 * <p>Scheduling (documentation/CREATURE_AI_SCHEDULING.md): looking around is the MEDIUM tier — every
 * {@link EcologySensing#INTERVAL} ticks, staggered — and the EVENT tier: a stimulus, damage or a player's interaction
 * wakes the creature, and it looks around at the next goal evaluation (no sooner than {@link #EVENT_MIN_GAP} ticks
 * after the last look), so it answers a blow within a few ticks rather than up to a second later. Steering is the
 * FAST tier and never queries the world. Hopeless searches (shelter, a place to flee to) back off instead of retrying
 * every tick ({@link AiBackoff}); a pursuit re-paths at vanilla's pace ({@link AiPursuit}). Movement goes through the
 * creature's own navigation, so a flier flies, a swimmer swims and a walker walks.
 *
 * <p>{@code skipped} lets a bespoke creature keep intents it already implements better (the Niffler's own treasure
 * goals, the Mooncalf's burrow): those intents are never acted on here, though they are still decided — and still
 * written up in a watching player's field notes.
 */
@NullMarked
public final class EcologyGoal extends Goal {

    /** Speeds, relative to the creature's own movement speed. */
    static final double FLEE_SPEED = 1.45;
    static final double WALK_SPEED = 1.0;
    static final double CHARGE_SPEED = 1.3;
    /** Ticks between a warning's sound and particles. */
    static final int WARN_INTERVAL = 40;
    /** Ticks between bites while hunting or defending. */
    static final int ATTACK_INTERVAL = 20;
    /** Fewest ticks between two looks, however many events arrive: a burning creature is hurt every tick. */
    public static final int EVENT_MIN_GAP = 5;

    private final PathfinderMob mob;
    private final Set<EcologyIntent> skipped;
    /** The creature's working memory, looked up once (the attachment object is the creature's for life). */
    private @Nullable EcologyState state;
    /** No covered spot in reach: wait 1 s, then 2, 4… up to the SLOW period before sampling again. */
    private final AiBackoff shelterSearch = new AiBackoff(20, AiCadence.SLOW.period());
    /** No way to run that is away from the threat: try again within a second, never every tick. */
    private final AiBackoff fleeSearch = new AiBackoff(5, 20);
    private final AiPursuit pursuit = new AiPursuit();
    /** A flier steering straight at a target it can see, instead of following a path. */
    private boolean directChase;
    private double lastChaseDistance = Double.MAX_VALUE;

    public EcologyGoal(PathfinderMob mob, Set<EcologyIntent> skipped) {
        this.mob = mob;
        this.skipped = skipped.isEmpty() ? EnumSet.noneOf(EcologyIntent.class) : EnumSet.copyOf(skipped);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!ModuleManager.isEnabled(Module.CREATURES) || !(mob.level() instanceof ServerLevel)) {
            return false;
        }
        maybeThink();
        EcologyIntent intent = state().intent();
        return intent != EcologyIntent.NONE && !skipped.contains(intent);
    }

    @Override
    public boolean canContinueToUse() {
        maybeThink();
        EcologyIntent intent = state().intent();
        return intent != EcologyIntent.NONE && !skipped.contains(intent);
    }

    private EcologyState state() {
        EcologyState current = state;
        if (current == null) {
            current = EcologySensing.state(mob);
            state = current;
        }
        return current;
    }

    /** MEDIUM: look around on the creature's staggered beat. EVENT: or now, if something woke it. */
    private void maybeThink() {
        if (EcologySensing.due(mob)) {
            think();
            return;
        }
        EcologyState s = state();
        if (s.wake) {
            if (mob.level().getGameTime() - s.lastThinkTick >= EVENT_MIN_GAP) {
                AiMetrics.inc(AiMetrics.Metric.EVENT_WAKE);
                think();
            } else {
                AiMetrics.inc(AiMetrics.Metric.EVENT_WAKE_DEFERRED);
            }
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        pursuit.reset();
        directChase = false;
        lastChaseDistance = Double.MAX_VALUE;
    }

    /** Look around and decide. Public for the AI test harness, which drives it on demand. */
    public EcologyBrain.Decision think() {
        EcologyProfile profile = EcologyProfileRegistry.of(mob.getType());
        EcologyState state = state();
        state.wake = false;
        state.lastThinkTick = mob.level().getGameTime();
        EcologyBrain.Decision before = state.decision;
        if (profile.isEmpty()) {
            state.decision = EcologyBrain.Decision.NONE;
            return state.decision;
        }
        Perception perception = EcologySensing.perceive(mob, profile);
        state.decision = EcologyBrain.decide(profile, perception);
        if (state.decision.intent() != before.intent()) {
            mob.getNavigation().stop();
            EcologyFieldNotes.witness(mob, state.decision);
        }
        return state.decision;
    }

    @Override
    public void tick() {
        EcologyState state = state();
        Perception p = state.perception;
        if (p == null) {
            return;
        }
        ServerLevel level = (ServerLevel) mob.level();
        long now = level.getGameTime();
        switch (state.intent()) {
            case FLEE_PREDATOR -> fleeFrom(now, p.threat().at());
            case FLEE_PLAYER, FLEE_HURT -> fleeFrom(now, p.player().at() != null ? p.player().at() : p.threat().at());
            case FLEE_STIMULUS -> fleeFrom(now, p.stimulus() == null ? null : p.stimulus().source());
            case WARN_INTRUDER -> warn(level, now, state, p);
            case DEFEND_TERRITORY -> defend(level, now, state, p);
            case HUNT -> hunt(level, now, state);
            case REST -> rest();
            case SEEK_SHELTER -> seekShelter(level, now);
            case REGROUP -> moveTo(p.kin().centre(), WALK_SPEED, 3.0);
            case RETURN_HOME -> moveTo(state.home, WALK_SPEED, 2.0);
            case SEEK_ITEM -> seekItem(level, p);
            case INVESTIGATE -> approach(p.stimulus() != null ? p.stimulus().source() : p.player().at(), 3.0);
            case APPROACH_PLAYER -> approach(p.player().at(), 4.0);
            case NONE -> { }
        }
    }

    // ── movement ────────────────────────────────────────────────────────────────────────────────

    private void fleeFrom(long now, @Nullable Vec3 source) {
        if (source == null || !mob.getNavigation().isDone()) {
            return;
        }
        if (!fleeSearch.ready(now)) {
            AiMetrics.inc(AiMetrics.Metric.BACKOFF_SKIP);
            return;
        }
        Vec3 away = DefaultRandomPos.getPosAway(mob, 16, 7, source);
        if (away != null) {
            fleeSearch.succeed();
            mob.getNavigation().moveTo(away.x, away.y, away.z, FLEE_SPEED);
        } else {
            fleeSearch.fail(now); // cornered: hold, and look again shortly rather than every tick
        }
    }

    private void moveTo(@Nullable Vec3 target, double speed, double stopWithin) {
        if (target == null) {
            return;
        }
        if (mob.position().distanceTo(target) <= stopWithin) {
            mob.getNavigation().stop();
            return;
        }
        if (mob.getNavigation().isDone() || AiCadence.every(mob, 20)) {
            mob.getNavigation().moveTo(target.x, target.y, target.z, speed);
        }
    }

    private void approach(@Nullable Vec3 target, double stopWithin) {
        if (target == null) {
            return;
        }
        mob.getLookControl().setLookAt(target.x, target.y + 1.0, target.z);
        moveTo(target, WALK_SPEED * 0.8, stopWithin);
    }

    private void rest() {
        mob.getNavigation().stop();
        // Head down, still: the look control would otherwise keep glancing at whatever walks past.
        mob.getLookControl().setLookAt(mob.getX() + mob.getLookAngle().x, mob.getY() - 1.0,
                mob.getZ() + mob.getLookAngle().z);
    }

    private void seekShelter(ServerLevel level, long now) {
        if (!mob.getNavigation().isDone()) {
            return;
        }
        if (!shelterSearch.ready(now)) {
            // Searched lately and found nothing: rest where it is, as it would have after the failed search.
            AiMetrics.inc(AiMetrics.Metric.BACKOFF_SKIP);
            rest();
            return;
        }
        // A few samples, once per arrival: the first spot near enough with something overhead.
        for (int i = 0; i < 8; i++) {
            Vec3 pos = LandRandomPos.getPos(mob, 12, 6);
            if (pos != null && !level.canSeeSky(BlockPos.containing(pos).above())) {
                shelterSearch.succeed();
                mob.getNavigation().moveTo(pos.x, pos.y, pos.z, WALK_SPEED);
                return;
            }
        }
        shelterSearch.fail(now);
        rest(); // nowhere covered in reach: rest where it is
    }

    // ── ground ──────────────────────────────────────────────────────────────────────────────────

    private void warn(ServerLevel level, long now, EcologyState state, Perception p) {
        mob.getNavigation().stop();
        Vec3 at = p.player().at() != null ? p.player().at() : p.stimulus() != null ? p.stimulus().source() : null;
        if (at != null) {
            mob.getLookControl().setLookAt(at.x, at.y + 1.0, at.z);
        }
        if (now >= state.nextActionTick) {
            state.nextActionTick = now + WARN_INTERVAL;
            mob.playAmbientSound();
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, mob.getX(), mob.getEyeY() + 0.3, mob.getZ(),
                    2, 0.3, 0.2, 0.3, 0.0);
        }
    }

    /**
     * Drive the intruder off: a creature that can bite closes and bites; one that cannot makes a bluff charge and
     * pulls up short. Never a pursuit: the goal ends as soon as the intruder is off its ground.
     */
    private void defend(ServerLevel level, long now, EcologyState state, Perception p) {
        LivingEntity target = state.playerId == null ? null : level.getPlayerByUUID(state.playerId);
        if (target == null || !target.isAlive() || ((Player) target).isCreative() || target.isSpectator()) {
            warn(level, now, state, p);
            return;
        }
        mob.getLookControl().setLookAt(target, 30.0f, 30.0f);
        double reach = mob.getBbWidth() * 2.0 + target.getBbWidth();
        if (mob.distanceTo(target) > reach) {
            chase(target, now);
            return;
        }
        mob.getNavigation().stop();
        if (now >= state.nextActionTick) {
            state.nextActionTick = now + ATTACK_INTERVAL;
            if (canBite()) {
                mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                mob.doHurtTarget(level, target);
            } else {
                warn(level, now, state, p);
            }
        }
    }

    /**
     * Closes on {@code target}. A walker follows a path re-planned at {@link AiPursuit}'s pace. A flier that can see
     * its target steers straight at it with its move control, as vanilla's flying hunters do (the Phantom's swoop,
     * the Vex's charge): a 3D path search costs milliseconds for a dragon-sized body, and in open air it only ever
     * finds the straight line anyway. Sight is re-checked at the pursuit's pace (one raycast per 4–20 ticks); when it
     * is blocked, or the flier has stopped gaining ground, it falls back to a path.
     */
    private void chase(LivingEntity target, long now) {
        if (pursuit.shouldRepath(mob, target, now)) {
            double distance = mob.distanceToSqr(target);
            boolean gaining = distance < lastChaseDistance - 0.25;
            boolean wasDirect = directChase;
            lastChaseDistance = distance;
            directChase = mob.getNavigation() instanceof FlyingPathNavigation
                    && (!wasDirect || gaining) && mob.hasLineOfSight(target);
            if (directChase) {
                mob.getNavigation().stop();
            } else if (!mob.getNavigation().moveTo(target, CHARGE_SPEED)) {
                pursuit.failed(now);
            }
        }
        if (directChase) {
            mob.getMoveControl().setWantedPosition(target.getX(), target.getY() + target.getBbHeight() * 0.5,
                    target.getZ(), CHARGE_SPEED);
        }
    }

    // ── food ────────────────────────────────────────────────────────────────────────────────────

    private void hunt(ServerLevel level, long now, EcologyState state) {
        Entity prey = state.preyId == null ? null : level.getEntity(state.preyId);
        if (!(prey instanceof LivingEntity living) || !living.isAlive()) {
            return;
        }
        mob.getLookControl().setLookAt(living, 30.0f, 30.0f);
        double reach = mob.getBbWidth() * 2.0 + living.getBbWidth();
        if (mob.distanceTo(living) > reach) {
            chase(living, now);
            return;
        }
        if (now >= state.nextActionTick && canBite()) {
            state.nextActionTick = now + ATTACK_INTERVAL;
            mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            mob.doHurtTarget(level, living); // a kill feeds it: CreatureStimuli.onDeath
        }
    }

    /** A kill feeds the predator for its profile's {@code fed_ticks}; it leaves prey alone until then. */
    static void fed(EcologyState state, long now, int fedTicks) {
        state.fedUntil = now + Math.max(fedTicks, 0);
        state.decision = EcologyBrain.Decision.NONE;
        state.preyId = null;
    }

    private boolean canBite() {
        return mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) != null
                && mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) > 0;
    }

    private void seekItem(ServerLevel level, Perception p) {
        Vec3 at = p.item().at();
        if (at == null) {
            return;
        }
        if (mob.position().distanceTo(at) > 1.5) {
            moveTo(at, WALK_SPEED, 1.0);
            return;
        }
        EcologyProfile profile = EcologyProfileRegistry.of(mob.getType());
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, mob.getBoundingBox().inflate(1.5))) {
            ItemStack stack = item.getItem();
            if (!EcologySensing.wants(profile, stack)) {
                continue;
            }
            // A scavenger eats what it found; a treasure-seeker noses at it — the Niffler's own goals pocket it.
            if (profile.has(EcologyCapability.SCAVENGER) && !profile.has(EcologyCapability.TREASURE_SEEKER)) {
                stack.shrink(1);
                mob.heal(2.0f);
                mob.playSound(net.minecraft.sounds.SoundEvents.GENERIC_EAT.value(), 0.6f, 1.0f);
            } else {
                mob.getLookControl().setLookAt(item, 30.0f, 30.0f);
            }
            EcologySensing.state(mob).decision = EcologyBrain.Decision.NONE;
            return;
        }
    }
}
