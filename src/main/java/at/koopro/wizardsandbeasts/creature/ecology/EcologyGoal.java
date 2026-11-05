package at.koopro.wizardsandbeasts.creature.ecology;

import at.koopro.wizardsandbeasts.creature.bond.BondableBeast;
import at.koopro.wizardsandbeasts.creature.profile.CreatureActivity;
import at.koopro.wizardsandbeasts.creature.profile.HasActivity;
import at.koopro.wizardsandbeasts.creature.schedule.AiBackoff;
import at.koopro.wizardsandbeasts.creature.schedule.AiCadence;
import at.koopro.wizardsandbeasts.creature.schedule.AiMetrics;
import at.koopro.wizardsandbeasts.creature.schedule.AiPursuit;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

/**
 * Carries out what {@link EcologyBrain} decided and {@link EcologyCommitment} let stand. One goal per creature,
 * registered once at construction and reading the profile live, so a datapack reload reaches creatures already in
 * the world — the same rule {@code FollowBondedOwnerGoal} follows.
 *
 * <p>Scheduling (documentation/CREATURE_AI_SCHEDULING.md): looking around is the MEDIUM tier — every
 * {@link EcologySensing#INTERVAL} ticks, staggered — and the EVENT tier: a stimulus, damage or a player's interaction
 * wakes the creature, and it looks around at the next goal evaluation (no sooner than {@link #EVENT_MIN_GAP} ticks
 * after the last look). Looking for water and food ({@link EcologyResources}) is the SLOW tier, and only while a need
 * is close to acting. Steering is the FAST tier and never queries the world. Hopeless searches back off
 * ({@link AiBackoff}); a pursuit re-paths at vanilla's pace ({@link AiPursuit}). Movement goes through the creature's
 * own navigation, so a flier flies, a swimmer swims and a walker walks.
 *
 * <p><b>Failure recovery.</b> Every move toward something watches its own progress: no ground gained toward the
 * destination for {@link #NAV_TIMEOUT} ticks (a chase: {@link #CHASE_TIMEOUT}) and the intent fails — it is dropped,
 * cooled down ({@link EcologyState#failed}, longer for repeated failures) and whatever place it was going to is
 * forgotten. A target that dies, leaves, or is taken in by someone ends the intent at once.
 *
 * <p>{@code skipped} lets a bespoke creature keep intents it already implements better (the Niffler's own treasure
 * goals, the Mooncalf's burrow): those intents are never acted on here, though they are still decided — and still
 * written up in a watching player's field notes. Ridden or carried, a creature has no ecology of its own.
 */
@NullMarked
public final class EcologyGoal extends Goal {

    /** Speeds, relative to the creature's own movement speed. */
    static final double FLEE_SPEED = 1.45;
    static final double WALK_SPEED = 1.0;
    static final double CHARGE_SPEED = 1.3;
    static final double STALK_SPEED = 0.6;
    /** Ticks between a warning's sound and particles. */
    static final int WARN_INTERVAL = 40;
    /** Ticks between bites while hunting or defending. */
    static final int ATTACK_INTERVAL = 20;
    /** Fewest ticks between two looks, however many events arrive: a burning creature is hurt every tick. */
    public static final int EVENT_MIN_GAP = 5;
    /** A move that gains no ground for this long has failed. */
    public static final int NAV_TIMEOUT = 100;
    /** A chase that gains no ground for this long has failed: the prey got away. */
    public static final int CHASE_TIMEOUT = 200;
    /** A destination vanilla finds no path to this many times running is given up. */
    static final int NO_PATH_TRIES = 3;
    /** Ticks a drink takes. */
    static final int DRINK_TICKS = 60;
    /** Ticks between bites when grazing, browsing or picking at remains. */
    static final int BITE_INTERVAL = 20;
    /** What one bite of forage takes off hunger. */
    static final float BITE = 0.25f;

    private final PathfinderMob mob;
    private final Set<EcologyIntent> skipped;
    /** The creature's working memory, looked up once (the attachment object is the creature's for life). */
    private @Nullable EcologyState state;
    /** No covered spot in reach: wait 1 s, then 2, 4… up to the SLOW period before sampling again. */
    private final AiBackoff shelterSearch = new AiBackoff(20, AiCadence.SLOW.period());
    /** No way to run that is away from the threat: try again within a second, never every tick. */
    private final AiBackoff fleeSearch = new AiBackoff(5, 20);
    /** No water or food found nearby: look again in 5 s, then 10, 20… up to a minute. */
    private final AiBackoff waterSearch = new AiBackoff(100, 1200);
    private final AiBackoff forageSearch = new AiBackoff(100, 1200);
    private final AiPursuit pursuit = new AiPursuit();
    /** A flier steering straight at a target it can see, instead of following a path. */
    private boolean directChase;
    private double lastChaseDistance = Double.MAX_VALUE;
    /** Progress toward the current destination, for the timeout. */
    private @Nullable Vec3 navTarget;
    private double navBest;
    private long navProgressAt;
    /** Consecutive path requests vanilla could not answer. */
    private int noPath;
    /** When the current act at the destination (a drink, a meal) began; 0 when not yet there. */
    private long actingSince;

    public EcologyGoal(PathfinderMob mob, Set<EcologyIntent> skipped) {
        this.mob = mob;
        this.skipped = skipped.isEmpty() ? EnumSet.noneOf(EcologyIntent.class) : EnumSet.copyOf(skipped);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!ModuleManager.isEnabled(Module.CREATURES) || !(mob.level() instanceof ServerLevel)
                || mob.isVehicle() || mob.isPassenger()) {
            return false;
        }
        maybeThink();
        EcologyIntent intent = state().intent();
        return intent != EcologyIntent.NONE && !skipped.contains(intent);
    }

    @Override
    public boolean canContinueToUse() {
        if (mob.isVehicle() || mob.isPassenger()) {
            return false;
        }
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

    /** SLOW: look for water and food when needed. MEDIUM: look around. EVENT: or now, if something woke it. */
    private void maybeThink() {
        if (AiCadence.SLOW.due(mob)) {
            searchForNeeds();
        }
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
        show(CreatureActivity.IDLE);
        mob.getNavigation().stop();
        pursuit.reset();
        directChase = false;
        lastChaseDistance = Double.MAX_VALUE;
        resetProgress();
    }

    /**
     * Look around, decide, and let the decision stand or not ({@link EcologyCommitment}). Public for the AI test
     * harness, which drives it on demand.
     */
    public EcologyBrain.Decision think() {
        EcologyProfile profile = EcologyProfileRegistry.of(mob.getType());
        EcologyState state = state();
        long now = mob.level().getGameTime();
        state.wake = false;
        state.lastThinkTick = now;
        EcologyBrain.Decision before = state.decision;
        if (profile.isEmpty()) {
            state.decision = EcologyBrain.Decision.NONE;
            return state.decision;
        }
        Perception perception = EcologySensing.perceive(mob, profile);
        EcologyBrain.Decision candidate = EcologyBrain.decide(profile, perception);
        boolean sustained = before.intent() != EcologyIntent.NONE
                && EcologyBrain.sustains(profile, perception, before.intent());
        EcologyBrain.Decision chosen = EcologyCommitment.arbitrate(before, state.heldSince, sustained, candidate, now, state);
        if (chosen.intent() != before.intent()) {
            if (before.intent() != EcologyIntent.NONE) {
                state.ended(before.intent(), now, EcologyCommitment.timedOut(before.intent(), now - state.heldSince));
            }
            state.heldSince = now;
            state.remember(chosen.intent(), now, false);
            mob.getNavigation().stop();
            resetProgress();
            EcologyFieldNotes.witness(mob, chosen);
        }
        state.decision = chosen;
        return chosen;
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
        Perception.Surroundings world = p.surroundings();
        switch (state.intent()) {
            case FLEE_PREDATOR -> fleeFrom(now, p.threat().at(), FLEE_SPEED);
            case FLEE_PLAYER, FLEE_HURT -> fleeFrom(now, p.player().at() != null ? p.player().at() : p.threat().at(), FLEE_SPEED);
            case FLEE_STIMULUS -> fleeFrom(now, p.stimulus() == null ? null : p.stimulus().source(), FLEE_SPEED);
            case FLEE_HAZARD -> fleeHazard(now, state);
            case WARN_INTRUDER -> warn(level, now, state, p);
            case DEFEND_TERRITORY -> defend(level, now, state, p);
            case HUNT -> hunt(level, now, state);
            case STALK -> stalk(level, now, state);
            case REST, SLEEP -> rest();
            case SEEK_SHELTER -> seekShelter(level, now);
            case SHELTER_FROM_WEATHER -> {
                if (world.skyAbove()) {
                    seekShelter(level, now);
                } else {
                    mob.getNavigation().stop(); // under cover: stay put until it passes
                }
            }
            case FORAGE -> forage(level, now, state, p);
            case DRINK -> drink(level, now, state);
            case SOCIALISE -> socialise(now, state, world.nearestKin().at());
            case KEEP_DISTANCE -> fleeFrom(now, world.nearestKin().at(), WALK_SPEED);
            case GROOM -> mob.getNavigation().stop();
            case REGROUP -> go(now, state, p.kin().centre(), WALK_SPEED, 3.0);
            case RETURN_HOME -> go(now, state, state.home, WALK_SPEED, 2.0);
            case SEEK_ITEM -> seekItem(level, now, state, p);
            case SCAVENGE -> scavenge(level, now, state, world.carrion().at());
            case INVESTIGATE -> investigate(now, state, p);
            case APPROACH_PLAYER -> approach(now, state, p.player().at(), 4.0);
            case NONE -> { }
        }
        show(activityFor(state.intent(), p));
    }

    /**
     * What the creature should be seen doing for {@code intent}: the client loops a clip for it
     * ({@link CreatureActivity#loop}). An act done in place (eating, drinking) shows only once it is there.
     */
    CreatureActivity activityFor(EcologyIntent intent, Perception p) {
        boolean there = actingSince != 0;
        return switch (intent) {
            case FLEE_PREDATOR, FLEE_PLAYER, FLEE_HURT, FLEE_STIMULUS, FLEE_HAZARD -> CreatureActivity.FLEE;
            case HUNT, DEFEND_TERRITORY -> CreatureActivity.RUN;
            case STALK -> p.prey().within(EcologyBrain.STRIKE_DISTANCE) ? CreatureActivity.RUN : CreatureActivity.SNEAK;
            case WARN_INTRUDER -> CreatureActivity.THREATEN;
            case REST -> CreatureActivity.REST;
            case SLEEP -> CreatureActivity.SLEEP;
            case SEEK_SHELTER, SHELTER_FROM_WEATHER -> mob.getNavigation().isInProgress()
                    ? CreatureActivity.WALK : CreatureActivity.REST;
            case FORAGE, SCAVENGE -> there ? CreatureActivity.EAT : CreatureActivity.WALK;
            case DRINK -> there ? CreatureActivity.DRINK : CreatureActivity.WALK;
            case GROOM -> CreatureActivity.GROOM;
            case INVESTIGATE -> mob.getNavigation().isInProgress() ? CreatureActivity.WALK : CreatureActivity.ALERT;
            default -> CreatureActivity.IDLE;
        };
    }

    /** Writes the activity; the synced value only goes out when it changes. */
    private void show(CreatureActivity activity) {
        if (mob instanceof HasActivity shown && shown.activity() != activity) {
            shown.setActivity(activity);
        }
    }

    // ── movement ────────────────────────────────────────────────────────────────────────────────

    private void fleeFrom(long now, @Nullable Vec3 source, double speed) {
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
            mob.getNavigation().moveTo(away.x, away.y, away.z, speed);
        } else {
            fleeSearch.fail(now); // cornered: hold, and look again shortly rather than every tick
        }
    }

    /** Out of the fire: to water it knows of if it is burning, else anywhere a few blocks off. */
    private void fleeHazard(long now, EcologyState state) {
        if (mob.isOnFire() && state.waterLook != null && state.waterLook.distanceTo(mob.position()) < 10) {
            mob.getNavigation().moveTo(state.waterLook.x, state.waterLook.y, state.waterLook.z, FLEE_SPEED);
            return;
        }
        if (!mob.getNavigation().isDone() || !fleeSearch.ready(now)) {
            return;
        }
        Vec3 out = DefaultRandomPos.getPos(mob, 8, 4);
        if (out != null) {
            fleeSearch.succeed();
            mob.getNavigation().moveTo(out.x, out.y, out.z, FLEE_SPEED);
        } else {
            fleeSearch.fail(now);
        }
    }

    /** The outcome of one tick's move toward a destination. */
    enum Nav { ARRIVED, MOVING, FAILED }

    /**
     * One tick of moving toward {@code target}, watching for progress. A new destination (more than two blocks from
     * the last) starts the clock again; ground gained resets it; {@link #NAV_TIMEOUT} ticks without either fails.
     */
    private Nav navigate(long now, @Nullable Vec3 target, double speed, double stopWithin) {
        if (target == null) {
            return Nav.FAILED;
        }
        double d = mob.position().distanceTo(target);
        // A finished path ends about a block short of a block's centre for a small body: that is arrival too.
        if (d <= stopWithin || (mob.getNavigation().isDone() && navTarget != null && d <= stopWithin + 1.0)) {
            mob.getNavigation().stop();
            navTarget = null;
            return Nav.ARRIVED;
        }
        if (navTarget == null || navTarget.distanceToSqr(target) > 4.0) {
            navTarget = target;
            navBest = d;
            navProgressAt = now;
            noPath = 0;
        } else if (d < navBest - 0.5) {
            navBest = d;
            navProgressAt = now;
        } else if (now - navProgressAt > NAV_TIMEOUT) {
            AiMetrics.inc(AiMetrics.Metric.NAV_TIMEOUT);
            return Nav.FAILED;
        }
        if (mob.getNavigation().isDone() || AiCadence.every(mob, 20)) {
            // No path at all, three asks running: give up now rather than ask vanilla for one every tick until the
            // timeout (a path search is the most expensive thing a creature does).
            if (mob.getNavigation().moveTo(target.x, target.y, target.z, speed)) {
                noPath = 0;
            } else if (++noPath >= NO_PATH_TRIES) {
                AiMetrics.inc(AiMetrics.Metric.NAV_TIMEOUT);
                return Nav.FAILED;
            }
        }
        return Nav.MOVING;
    }

    /** Moves toward {@code target}; gives the intent up as failed if the way is blocked. */
    private boolean go(long now, EcologyState state, @Nullable Vec3 target, double speed, double stopWithin) {
        Nav nav = navigate(now, target, speed, stopWithin);
        if (nav == Nav.FAILED) {
            fail(state, now);
        }
        return nav == Nav.ARRIVED;
    }

    private boolean approach(long now, EcologyState state, @Nullable Vec3 target, double stopWithin) {
        if (target == null) {
            return false;
        }
        mob.getLookControl().setLookAt(target.x, target.y + 1.0, target.z);
        return go(now, state, target, WALK_SPEED * 0.8, stopWithin);
    }

    /** The current intent could not be done: drop it, cool it down, forget where it was going. */
    private void fail(EcologyState state, long now) {
        EcologyIntent intent = state.intent();
        state.failed(intent, now);
        switch (intent) {
            case DRINK -> { state.waterAt = null; state.waterLook = null; }
            case FORAGE -> { state.forageAt = null; state.forageLook = null; }
            case SCAVENGE -> {
                state.carrionEaten = state.carrionAt;
                state.carrionAt = null;
            }
            default -> { }
        }
        finish(state, now);
    }

    /** The current intent is over (done, or failed after {@link #fail}): back to nothing, and look again soon. */
    private void finish(EcologyState state, long now) {
        if (state.intent() != EcologyIntent.NONE) {
            state.ended(state.intent(), now, false);
        }
        state.decision = EcologyBrain.Decision.NONE;
        state.heldSince = now;
        state.remember(EcologyIntent.NONE, now, false);
        state.wake();
        mob.getNavigation().stop();
        resetProgress();
    }

    private void resetProgress() {
        navTarget = null;
        noPath = 0;
        actingSince = 0;
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

    // ── needs ───────────────────────────────────────────────────────────────────────────────────

    /** SLOW tier: look for water or food once a need is getting close to acting and nothing is known. */
    private void searchForNeeds() {
        EcologyProfile profile = EcologyProfileRegistry.of(mob.getType());
        if (profile.isEmpty() || !(mob.level() instanceof ServerLevel level) || mob.isInWater()) {
            return;
        }
        EcologyState state = state();
        long now = level.getGameTime();
        float soon = EcologyBrain.NEED_START * 0.8f;
        if (profile.needs(EcologyNeed.THIRST) && state.needs.get(EcologyNeed.THIRST) >= soon
                && (state.waterAt == null || now >= state.waterUntil) && waterSearch.ready(now)) {
            EcologyResources.Find found = EcologyResources.findWater(mob.blockPosition(), mob.getRandom(),
                    EcologyResources.probe(level, profile));
            if (found != null) {
                waterSearch.succeed();
                state.waterAt = Vec3.atBottomCenterOf(found.stand());
                state.waterLook = Vec3.atCenterOf(found.target());
                state.waterUntil = now + EcologyResources.MEMORY;
            } else {
                waterSearch.fail(now);
            }
        }
        if (profile.foragesBlocks() && state.needs.get(EcologyNeed.HUNGER) >= soon
                && (state.forageAt == null || now >= state.forageUntil) && forageSearch.ready(now)) {
            EcologyResources.Find found = EcologyResources.findFood(mob.blockPosition(), mob.getRandom(),
                    EcologyResources.probe(level, profile));
            if (found != null) {
                forageSearch.succeed();
                state.forageAt = Vec3.atBottomCenterOf(found.stand());
                state.forageLook = Vec3.atCenterOf(found.target());
                state.forageUntil = now + EcologyResources.MEMORY;
            } else {
                forageSearch.fail(now);
            }
        }
    }

    private void drink(ServerLevel level, long now, EcologyState state) {
        Vec3 look = state.waterLook;
        if (look == null) {
            return;
        }
        // Walk to the bank, but drink as soon as the water is in reach of the head: a path to the bank block can end a
        // block or two short for a small body, and a path into the water itself may not exist at all.
        if (mob.position().distanceTo(look) <= 1.6 + mob.getBbWidth() * 0.5) {
            mob.getNavigation().stop();
        } else if (!go(now, state, state.waterAt, WALK_SPEED, 1.2)) {
            return;
        }
        BlockPos water = BlockPos.containing(look);
        if (actingSince == 0) {
            if (!level.getFluidState(water).is(FluidTags.WATER)) { // gone since it was found
                fail(state, now);
                return;
            }
            actingSince = now;
        }
        mob.getLookControl().setLookAt(look.x, look.y, look.z);
        state.needs.satisfy(EcologyNeed.THIRST, 1.0f / DRINK_TICKS);
        if ((now - actingSince) % BITE_INTERVAL == 0) {
            mob.playSound(SoundEvents.GENERIC_DRINK.value(), 0.5f, 0.9f + mob.getRandom().nextFloat() * 0.2f);
            level.sendParticles(ParticleTypes.SPLASH, look.x, look.y + 0.5, look.z, 4, 0.2, 0.0, 0.2, 0.0);
        }
        if (state.needs.get(EcologyNeed.THIRST) <= EcologyBrain.NEED_DONE) {
            state.succeeded(EcologyIntent.DRINK);
            finish(state, now);
        }
    }

    private void forage(ServerLevel level, long now, EcologyState state, Perception p) {
        EcologyProfile profile = EcologyProfileRegistry.of(mob.getType());
        if (state.forageAt == null || now >= state.forageUntil) {
            if (p.item().seen()) {
                seekItem(level, now, state, p);
            }
            return;
        }
        Vec3 look = state.forageLook;
        // Reach is measured to the food itself, as for water: a mouth reaches the block from beside it.
        if (look == null) {
            return;
        }
        if (mob.position().distanceTo(look) <= 1.6 + mob.getBbWidth() * 0.5) {
            mob.getNavigation().stop();
        } else if (!go(now, state, state.forageAt, WALK_SPEED, 1.2)) {
            return;
        }
        BlockPos target = BlockPos.containing(look);
        BlockState food = level.getBlockState(target);
        if (EcologyResources.foodCell(profile, food) == null) { // eaten by something else, or cut
            state.forageAt = null;
            state.forageLook = null;
            finish(state, now);
            return;
        }
        if (actingSince == 0) {
            actingSince = now;
        }
        mob.getLookControl().setLookAt(look.x, look.y, look.z);
        if ((now - actingSince) % BITE_INTERVAL == BITE_INTERVAL - 1) {
            bite(level, target, food);
            state.needs.satisfy(EcologyNeed.HUNGER, BITE);
            if (state.needs.get(EcologyNeed.HUNGER) <= EcologyBrain.NEED_DONE) {
                state.succeeded(EcologyIntent.FORAGE);
                finish(state, now);
            }
        }
    }

    /** One mouthful: crumbs of the block, a chewing sound, and — where mobs may grief — the grass is gone. */
    private void bite(ServerLevel level, BlockPos target, BlockState food) {
        level.levelEvent(2001, target, Block.getId(food));
        mob.playSound(SoundEvents.GENERIC_EAT.value(), 0.5f, 1.0f);
        mob.heal(1.0f);
        boolean softPlant = food.is(Blocks.SHORT_GRASS) || food.is(Blocks.FERN);
        if (softPlant && EventHooks.canEntityGrief(level, mob)) {
            level.destroyBlock(target, false, mob);
            EcologyState s = state();
            s.forageAt = null;
            s.forageLook = null;
        }
    }

    private void socialise(long now, EcologyState state, @Nullable Vec3 kin) {
        if (kin == null) {
            return;
        }
        mob.getLookControl().setLookAt(kin.x, kin.y + 0.5, kin.z);
        go(now, state, kin, WALK_SPEED * 0.8, EcologyBrain.COMPANY - 1.0);
    }

    private void scavenge(ServerLevel level, long now, EcologyState state, @Nullable Vec3 carrion) {
        if (!go(now, state, carrion, WALK_SPEED, 1.5) || carrion == null) {
            return;
        }
        if (actingSince == 0) {
            actingSince = now;
        }
        mob.getLookControl().setLookAt(carrion.x, carrion.y, carrion.z);
        if ((now - actingSince) % BITE_INTERVAL == BITE_INTERVAL - 1) {
            mob.playSound(SoundEvents.GENERIC_EAT.value(), 0.4f, 0.8f);
            level.sendParticles(ParticleTypes.SMOKE, carrion.x, carrion.y + 0.2, carrion.z, 2, 0.2, 0.05, 0.2, 0.0);
        }
        if (now - actingSince >= 3L * BITE_INTERVAL) { // nosed over what is left: done with this spot
            state.needs.satisfy(EcologyNeed.HUNGER, 0.3f);
            state.carrionEaten = state.carrionAt;
            state.carrionAt = null;
            state.succeeded(EcologyIntent.SCAVENGE);
            finish(state, now);
        }
    }

    private void investigate(long now, EcologyState state, Perception p) {
        Vec3 at = p.stimulus() != null ? p.stimulus().source() : p.player().at();
        if (approach(now, state, at, 3.0)) {
            // Arrived and had a good look: curiosity fed.
            state.needs.set(EcologyNeed.CURIOSITY, 0);
        }
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
                mob.swing(InteractionHand.MAIN_HAND);
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
        chase(target, now, CHARGE_SPEED);
    }

    private void chase(LivingEntity target, long now, double speed) {
        if (pursuit.shouldRepath(mob, target, now)) {
            double distance = mob.distanceToSqr(target);
            boolean gaining = distance < lastChaseDistance - 0.25;
            boolean wasDirect = directChase;
            lastChaseDistance = distance;
            directChase = mob.getNavigation() instanceof FlyingPathNavigation
                    && (!wasDirect || gaining) && mob.hasLineOfSight(target);
            if (directChase) {
                mob.getNavigation().stop();
            } else if (!mob.getNavigation().moveTo(target, speed)) {
                pursuit.failed(now);
            }
        }
        if (directChase) {
            mob.getMoveControl().setWantedPosition(target.getX(), target.getY() + target.getBbHeight() * 0.5,
                    target.getZ(), speed);
        }
    }

    // ── food ────────────────────────────────────────────────────────────────────────────────────

    /** The prey it decided on, if it is still fair game: alive, here, in reach and nobody's pet. */
    private @Nullable LivingEntity validPrey(ServerLevel level, EcologyState state) {
        Entity prey = state.preyId == null ? null : level.getEntity(state.preyId);
        if (!(prey instanceof LivingEntity living) || !living.isAlive() || living.level() != mob.level()
                || (living instanceof BondableBeast b && b.bondOwner() != null)
                || (living instanceof TamableAnimal tame && tame.isTame())) {
            return null;
        }
        double reach = EcologyProfileRegistry.of(mob.getType()).senseRadius() * 1.5;
        return living.distanceToSqr(mob) <= reach * reach ? living : null;
    }

    private void hunt(ServerLevel level, long now, EcologyState state) {
        LivingEntity prey = validPrey(level, state);
        if (prey == null) {
            finish(state, now); // gone, or taken in by someone: nothing to hunt
            return;
        }
        mob.getLookControl().setLookAt(prey, 30.0f, 30.0f);
        double reach = mob.getBbWidth() * 2.0 + prey.getBbWidth();
        double d = mob.distanceTo(prey);
        if (d > reach) {
            if (!chaseProgress(now, prey.position(), d, CHASE_TIMEOUT)) {
                fail(state, now); // it got away
                return;
            }
            chase(prey, now);
            return;
        }
        navTarget = null;
        if (now >= state.nextActionTick && canBite()) {
            state.nextActionTick = now + ATTACK_INTERVAL;
            mob.swing(InteractionHand.MAIN_HAND);
            mob.doHurtTarget(level, prey); // a kill feeds it: CreatureStimuli.onDeath
        }
    }

    /** Creep up, low and slow; from close, the ordinary hunt takes over. */
    private void stalk(ServerLevel level, long now, EcologyState state) {
        LivingEntity prey = validPrey(level, state);
        if (prey == null) {
            finish(state, now);
            return;
        }
        double d = mob.distanceTo(prey);
        if (d <= EcologyBrain.STRIKE_DISTANCE) {
            hunt(level, now, state);
            return;
        }
        mob.getLookControl().setLookAt(prey, 10.0f, 10.0f);
        if (!chaseProgress(now, prey.position(), d, CHASE_TIMEOUT)) {
            fail(state, now);
            return;
        }
        chase(prey, now, STALK_SPEED);
    }

    /** Progress of a chase (the target moves, so only the distance counts). False once it has stalled too long. */
    private boolean chaseProgress(long now, Vec3 target, double distance, int timeout) {
        if (navTarget == null) {
            navTarget = target;
            navBest = distance;
            navProgressAt = now;
            return true;
        }
        navTarget = target;
        if (distance < navBest - 0.5) {
            navBest = distance;
            navProgressAt = now;
            return true;
        }
        if (now - navProgressAt > timeout) {
            AiMetrics.inc(AiMetrics.Metric.NAV_TIMEOUT);
            return false;
        }
        return true;
    }

    /** A kill feeds the predator: hunger gone, and — without a hunger need — fed for its profile's {@code fed_ticks}. */
    static void fed(EcologyState state, long now, int fedTicks) {
        state.fedUntil = now + Math.max(fedTicks, 0);
        state.needs.set(EcologyNeed.HUNGER, 0);
        if (state.intent() == EcologyIntent.HUNT || state.intent() == EcologyIntent.STALK) {
            state.succeeded(state.intent());
        }
        state.decision = EcologyBrain.Decision.NONE;
        state.heldSince = now;
        state.preyId = null;
    }

    private boolean canBite() {
        return mob.getAttribute(Attributes.ATTACK_DAMAGE) != null && mob.getAttributeValue(Attributes.ATTACK_DAMAGE) > 0;
    }

    private void seekItem(ServerLevel level, long now, EcologyState state, Perception p) {
        Vec3 at = p.item().at();
        if (at == null) {
            return;
        }
        if (mob.position().distanceTo(at) > 1.5) {
            go(now, state, at, WALK_SPEED, 1.0);
            return;
        }
        EcologyProfile profile = EcologyProfileRegistry.of(mob.getType());
        boolean hungry = state.needs.get(EcologyNeed.HUNGER) >= EcologyBrain.NEED_START
                || state.intent() == EcologyIntent.FORAGE;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, mob.getBoundingBox().inflate(1.5))) {
            ItemStack stack = item.getItem();
            if (!EcologySensing.wants(profile, stack, hungry)) {
                continue;
            }
            // A creature eats food it found; a treasure-seeker noses at treasure — the Niffler's own goals pocket it.
            if (EcologySensing.isFood(profile, stack, hungry)) {
                stack.shrink(1);
                mob.heal(2.0f);
                state.needs.satisfy(EcologyNeed.HUNGER, 0.5f);
                mob.playSound(SoundEvents.GENERIC_EAT.value(), 0.6f, 1.0f);
            } else {
                mob.getLookControl().setLookAt(item, 30.0f, 30.0f);
            }
            finish(state, now);
            return;
        }
    }
}
