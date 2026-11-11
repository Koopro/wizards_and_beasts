package at.koopro.wizardsandbeasts.creature.ecology;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.creature.profile.CreatureReaction;
import at.koopro.wizardsandbeasts.creature.profile.CreatureReaction.Response;
import at.koopro.wizardsandbeasts.creature.profile.CreatureReaction.Stimulus;
import at.koopro.wizardsandbeasts.creature.schedule.AiMetrics;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

import static at.koopro.wizardsandbeasts.creature.ecology.EcologyCapability.*;

/**
 * Raises the {@link CreatureReaction} stimuli from the things that actually happen: a spell cast, a creature dying, a
 * creature being hurt, a noise, a person running. Until 2026-10 the reaction vocabulary was declared on creatures
 * and raised by nothing.
 *
 * <p>Each event does one bounded query around where it happened — per event, never per tick. A creature's answer is
 * what its definition declares for that stimulus; a creature that declares nothing answers from its ecology and
 * temperament (a herd scatters when one of it falls, a curious creature comes to see a spell or a noise, a timid prey
 * animal bolts from a crash a bold one only looks up at). The answer is held for {@link EcologySensing#STIMULUS_TICKS}
 * and acted on by {@link EcologyGoal} through {@link EcologyBrain}, so it competes with everything else in one
 * ordered policy.
 *
 * <p><b>Noise</b> comes from vanilla's game events ({@link VanillaGameEvent}), which fire for every step and block
 * change in the world, so the listener is built to say no fast: an identity check against a handful of events,
 * then a per-chunk rate limit (one noise of each loudness per chunk per {@link #NOISE_GAP} ticks), and only then one
 * bounded query. A sleeping creature sleeps through a soft noise.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class CreatureStimuli {

    /** Spells are noticed this far by an ordinary creature, twice as far with a magical sense. */
    public static final double SPELL_RADIUS = 12.0;
    /** Kin falling is noticed this far. */
    public static final double DEATH_RADIUS = 16.0;
    /** A scavenger smells a death this far off... */
    public static final double CARRION_RADIUS = 24.0;
    /** ...and remembers it this long. */
    public static final int CARRION_TICKS = 2400;
    /** One noise of each loudness per chunk per this many ticks; the rest are dropped. */
    public static final int NOISE_GAP = 20;

    /** How far a noise carries, and how alarming it is. */
    public enum Loudness {
        /** A block placed. */
        SOFT(6),
        /** A block broken, an arrow landing, a note block, a fuse lit, a person running. */
        MEDIUM(10),
        /** An explosion, lightning, a horn. */
        LOUD(32);

        public final double radius;

        Loudness(double radius) {
            this.radius = radius;
        }
    }

    private CreatureStimuli() {}

    /** A spell was cast at {@code at}. Called from the cast pipeline on a successful cast. */
    public static void spellCast(ServerLevel level, Vec3 at) {
        raise(level, at, Stimulus.SPELL, SPELL_RADIUS * 2, null);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level() instanceof ServerLevel level) {
            // Only the dead's own kind and fellow prey take note of a death (raise). A vanilla mob with no ecology is
            // news to nobody — its kin have no ecology either — so a mob farm's deaths cost no query at all.
            if (dead instanceof GenericBeastEntity || !EcologyProfileRegistry.of(dead.getType()).isEmpty()) {
                raise(level, dead.position(), Stimulus.CREATURE_DEATH, DEATH_RADIUS, dead);
            } else {
                AiMetrics.inc(AiMetrics.Metric.STIMULUS_SKIPPED);
            }
            carrion(level, dead);
            // A predator that killed its prey — by its hunt or by any other bite — has eaten.
            if (event.getSource().getEntity() instanceof PathfinderMob killer) {
                EcologyProfile hunter = EcologyProfileRegistry.of(killer.getType());
                if (EcologySensing.isPrey(hunter, killer, dead)) {
                    EcologyGoal.fed(EcologySensing.state(killer), level.getGameTime(), hunter.fedTicks());
                }
            }
        }
    }

    /**
     * Logs a death for scavengers to find ({@link CarrionLog}): a few array writes, no query — only scavengers pay,
     * when they look around. Nothing at all while no scavenger is loaded in the data.
     */
    private static void carrion(ServerLevel level, LivingEntity dead) {
        if (!ModuleManager.isEnabled(Module.CREATURES) || !EcologyProfileRegistry.anyHas(SCAVENGER)
                || dead instanceof Player) {
            return;
        }
        CarrionLog.of(level).record(dead.position(), level.getGameTime());
    }

    /** A hurt creature is frightened and answers, and — if social — so do its kin within its herd radius. */
    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof PathfinderMob hurt) || !(hurt.level() instanceof ServerLevel level)
                || !ModuleManager.isEnabled(Module.CREATURES)) {
            return;
        }
        Entity attacker = event.getSource().getEntity();
        Vec3 from = attacker != null ? attacker.position()
                : event.getSource().getSourcePosition() != null ? event.getSource().getSourcePosition() : hurt.position();
        EcologyProfile profile = EcologyProfileRegistry.of(hurt.getType());
        if (!profile.isEmpty()) {
            EcologySensing.state(hurt).needs.alarm(profile);
        }
        Response own = responseOf(hurt, profile, Stimulus.DAMAGE);
        if (own != Response.IGNORE) {
            hold(hurt, own, from, level.getGameTime(), Stimulus.DAMAGE);
        }
        // Burned: to a creature that declares a reaction to fire, that outranks whatever it made of the blow.
        if (event.getSource().is(DamageTypeTags.IS_FIRE)) {
            Response burned = responseOf(hurt, profile, Stimulus.FIRE);
            if (burned != Response.IGNORE) {
                hold(hurt, burned, from, level.getGameTime(), Stimulus.FIRE);
            }
        }
        if (profile.has(SOCIAL)) {
            AiMetrics.inc(AiMetrics.Metric.STIMULUS_QUERY);
            for (PathfinderMob kin : level.getEntitiesOfClass(PathfinderMob.class,
                    hurt.getBoundingBox().inflate(profile.herdRadius() * 1.5), e -> e.getType() == hurt.getType() && e != hurt)) {
                Response answer = profile.has(AGGRESSIVE) || profile.has(TERRITORIAL) ? Response.ATTACK
                        : profile.has(PREY) || profile.has(COWARDLY) ? Response.FLEE : Response.BECOME_ALERT;
                hold(kin, answer, from, level.getGameTime(), Stimulus.DAMAGE);
            }
        }
    }

    private static void raise(ServerLevel level, Vec3 at, Stimulus stimulus, double reach, @Nullable Entity cause) {
        if (!ModuleManager.isEnabled(Module.CREATURES)) {
            return;
        }
        long now = level.getGameTime();
        AiMetrics.inc(AiMetrics.Metric.STIMULUS_QUERY);
        boolean causeIsPrey = cause != null && EcologyProfileRegistry.of(cause.getType()).has(PREY);
        for (PathfinderMob mob : level.getEntitiesOfClass(PathfinderMob.class,
                new AABB(at, at).inflate(reach), e -> e != cause && e.isAlive())) {
            EcologyProfile profile = EcologyProfileRegistry.of(mob.getType());
            if (stimulus == Stimulus.CREATURE_DEATH && cause != null && cause.getType() != mob.getType()
                    && !(profile.has(PREY) && causeIsPrey)) {
                continue; // a death is news to its own kind and to fellow prey, not to everything nearby
            }
            double radius = radiusFor(mob, profile, stimulus);
            if (mob.position().distanceTo(at) > radius) {
                continue;
            }
            Response answer = responseOf(mob, profile, stimulus);
            if (answer != Response.IGNORE) {
                if (stimulus == Stimulus.SPELL) {
                    EcologySensing.state(mob).needs.set(EcologyNeed.MAGIC, 0); // magic done nearby feeds a magivore
                }
                hold(mob, answer, at, now, stimulus);
            }
        }
    }

    // ── noise ───────────────────────────────────────────────────────────────────────────────────

    /** Last tick a noise of each loudness was let through, per chunk, per level. */
    private static final Map<ServerLevel, Long2LongOpenHashMap> HEARD = new WeakHashMap<>();

    /** A flame is noticed this far. */
    public static final double FIRE_RADIUS = 16.0;

    /**
     * Fire appeared at {@code at}: lit with flint and steel, a torch or campfire set down, lava poured. Only a
     * creature whose definition declares a reaction to fire answers (the Yeti, which canon says fears it); nothing
     * is implied for the rest. Public for the test harness and for spells that make fire without a block event.
     */
    public static void fire(ServerLevel level, Vec3 at) {
        raise(level, at, Stimulus.FIRE, FIRE_RADIUS, null);
    }

    /** A block a creature would call fire: open flame, a lit campfire, a torch, lava. */
    static boolean isFlame(BlockState state) {
        if (state.is(BlockTags.FIRE) || state.is(Blocks.LAVA)) {
            return true;
        }
        if (state.is(BlockTags.CAMPFIRES)) {
            return state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT);
        }
        return state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH) || state.is(Blocks.SOUL_TORCH)
                || state.is(Blocks.SOUL_WALL_TORCH);
    }

    @SubscribeEvent
    public static void onGameEvent(VanillaGameEvent event) {
        // Fire first: it is not a noise and is not rate-limited away. Flint and steel and block items both announce
        // a placed block; one block-state read per placement is the whole cost.
        if (event.getVanillaEvent().is(GameEvent.BLOCK_PLACE) && event.getLevel() instanceof ServerLevel placedIn
                && ModuleManager.isEnabled(Module.CREATURES)
                && isFlame(placedIn.getBlockState(BlockPos.containing(event.getEventPosition())))) {
            fire(placedIn, event.getEventPosition());
        }
        Loudness loudness = loudness(event.getVanillaEvent(), event.getCause());
        if (loudness == null || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!ModuleManager.isEnabled(Module.CREATURES) || !EcologyProfileRegistry.anyLoaded()) {
            return;
        }
        Vec3 at = event.getEventPosition();
        long now = level.getGameTime();
        if (!admit(level, at, loudness, now)) {
            AiMetrics.inc(AiMetrics.Metric.NOISE_DROPPED);
            return;
        }
        Stimulus kind = event.getVanillaEvent().is(GameEvent.STEP) ? Stimulus.PLAYER : Stimulus.NOISE;
        noise(level, at, loudness, kind, event.getCause(), now);
    }

    /** Raises a noise at {@code at}. Public for the test harness, which makes noises on demand. */
    public static void noise(ServerLevel level, Vec3 at, Loudness loudness, Stimulus kind, @Nullable Entity cause,
                             long now) {
        AiMetrics.inc(AiMetrics.Metric.NOISE_QUERY);
        for (PathfinderMob mob : level.getEntitiesOfClass(PathfinderMob.class, new AABB(at, at).inflate(loudness.radius),
                e -> e != cause && e.isAlive())) {
            EcologyProfile profile = EcologyProfileRegistry.of(mob.getType());
            if (profile.isEmpty()) {
                continue;
            }
            EcologyState state = EcologySensing.state(mob);
            double distance = mob.position().distanceTo(at);
            if (distance > loudness.radius) {
                continue;
            }
            // Asleep, a soft noise goes unheard, and a medium one only close by.
            if (state.intent().resting() && (loudness == Loudness.SOFT
                    || (loudness == Loudness.MEDIUM && distance > loudness.radius * 0.5))) {
                continue;
            }
            Response answer = mob instanceof GenericBeastEntity beast && beast.reactionTo(kind) != Response.IGNORE
                    ? beast.reactionTo(kind)
                    : heard(profile, kind, loudness,
                            state.personality != null ? state.personality : EcologyPersonality.NEUTRAL);
            if (answer != Response.IGNORE) {
                hold(mob, answer, at, now, kind);
            }
        }
    }

    /** Which game events are noises, and how loud. Identity checks only: this runs for every event in the world. */
    static @Nullable Loudness loudness(Holder<GameEvent> event, @Nullable Entity cause) {
        if (event.is(GameEvent.STEP)) {
            // Only a person running is worth hearing; sneaking makes no step event at all.
            return cause instanceof Player player && player.isSprinting() && !player.isSpectator() ? Loudness.MEDIUM : null;
        }
        if (event.is(GameEvent.EXPLODE) || event.is(GameEvent.LIGHTNING_STRIKE) || event.is(GameEvent.INSTRUMENT_PLAY)) {
            return Loudness.LOUD;
        }
        if (event.is(GameEvent.PROJECTILE_LAND) || event.is(GameEvent.NOTE_BLOCK_PLAY) || event.is(GameEvent.PRIME_FUSE)) {
            return Loudness.MEDIUM;
        }
        // Blocks changing are noise only when a person does it: a grazing herd-mate cropping grass, fire spreading or
        // a crop growing is the background a creature lives in.
        if (!(cause instanceof Player)) {
            return null;
        }
        return event.is(GameEvent.BLOCK_DESTROY) ? Loudness.MEDIUM : event.is(GameEvent.BLOCK_PLACE) ? Loudness.SOFT : null;
    }

    /** The per-chunk rate limit. Pruned when it grows, so a long session does not accumulate chunks. */
    static synchronized boolean admit(ServerLevel level, Vec3 at, Loudness loudness, long now) {
        Long2LongOpenHashMap heard = HEARD.computeIfAbsent(level, l -> new Long2LongOpenHashMap());
        long key = (((long) ((int) Math.floor(at.x) >> 4)) << 34) ^ (((long) ((int) Math.floor(at.z) >> 4)) << 2)
                ^ loudness.ordinal();
        long last = heard.getOrDefault(key, Long.MIN_VALUE / 2);
        if (now - last < NOISE_GAP) {
            return false;
        }
        if (heard.size() > 4096) {
            heard.long2LongEntrySet().removeIf(e -> now - e.getLongValue() > NOISE_GAP * 10L);
        }
        heard.put(key, now);
        return true;
    }

    /**
     * What a creature that declared nothing does about a noise or a running person. Pure. Loud: prey and the
     * avoidant run, the territorial bristle, the curious come to look. Medium: only the timid run. Soft: only the
     * curious care. A running person frightens the timid and the avoidant and makes the territorial bristle; the
     * curious only ever come to quiet people.
     */
    public static Response heard(EcologyProfile profile, Stimulus kind, Loudness loudness, EcologyPersonality me) {
        boolean skittish = profile.has(PREY) || profile.has(COWARDLY) || profile.has(HUMAN_AVOIDANT);
        boolean timid = skittish && me.boldness() < 0.35f;
        if (kind == Stimulus.PLAYER) {
            if (timid || profile.has(HUMAN_AVOIDANT)) {
                return Response.FLEE;
            }
            return profile.has(TERRITORIAL) ? Response.BECOME_ALERT : Response.IGNORE;
        }
        return switch (loudness) {
            case LOUD -> skittish ? Response.FLEE
                    : profile.has(TERRITORIAL) ? Response.BECOME_ALERT
                    : profile.has(CURIOUS) ? Response.INVESTIGATE : Response.IGNORE;
            case MEDIUM -> timid ? Response.FLEE
                    : profile.has(CURIOUS) ? Response.INVESTIGATE : Response.IGNORE;
            case SOFT -> profile.has(CURIOUS) && !timid ? Response.INVESTIGATE : Response.IGNORE;
        };
    }

    // ── answers ─────────────────────────────────────────────────────────────────────────────────

    static double radiusFor(PathfinderMob mob, EcologyProfile profile, Stimulus stimulus) {
        if (mob instanceof GenericBeastEntity beast && beast.reactionTo(stimulus) != Response.IGNORE) {
            return beast.reactionRadius(stimulus);
        }
        return switch (stimulus) {
            case SPELL -> profile.has(MAGICAL_SENSE) ? SPELL_RADIUS * 2 : SPELL_RADIUS;
            case CREATURE_DEATH -> DEATH_RADIUS;
            default -> CreatureReaction.DEFAULT_RADIUS;
        };
    }

    /** The declared reaction, else the one the creature's ecology implies. */
    static Response responseOf(PathfinderMob mob, EcologyProfile profile, Stimulus stimulus) {
        if (mob instanceof GenericBeastEntity beast) {
            Response declared = beast.reactionTo(stimulus);
            if (declared != Response.IGNORE) {
                return declared;
            }
        }
        return implied(profile, stimulus);
    }

    /** What a creature's ecology says it does about a stimulus nobody wrote a reaction for. Pure. */
    public static Response implied(EcologyProfile profile, Stimulus stimulus) {
        return switch (stimulus) {
            case SPELL -> profile.has(MAGICAL_SENSE)
                    ? (profile.has(CURIOUS) || profile.needs(EcologyNeed.MAGIC) ? Response.INVESTIGATE
                        : profile.has(PREY) || profile.has(COWARDLY) || profile.has(HUMAN_AVOIDANT) ? Response.FLEE
                        : Response.BECOME_ALERT)
                    : Response.IGNORE;
            case CREATURE_DEATH -> profile.has(HERD) || profile.has(PREY) ? Response.FLEE
                    : profile.has(SOCIAL) && profile.has(PREDATOR) ? Response.BECOME_ALERT : Response.IGNORE;
            case DAMAGE -> profile.has(COWARDLY) || (profile.has(PREY) && !profile.has(AGGRESSIVE)) ? Response.FLEE
                    : Response.IGNORE;
            default -> Response.IGNORE;
        };
    }

    /** Records the answer and wakes the creature, so it acts on it within a few ticks (the EVENT tier). */
    private static void hold(PathfinderMob mob, Response response, Vec3 source, long now, Stimulus kind) {
        EcologyState state = EcologySensing.state(mob);
        state.react(new Perception.Stimulus(response, source, kind), now, now + EcologySensing.STIMULUS_TICKS);
        if (response == Response.FLEE) {
            state.needs.alarm(EcologyProfileRegistry.of(mob.getType()));
            // What runs is no longer fighting. Without this a creature driven off kept its target and came
            // straight back the moment the fright ended.
            if (mob.getTarget() != null) {
                mob.setTarget(null);
            }
        }
        state.wake();
    }

    /**
     * A player's hand on a creature — feeding, handling, harvesting, bonding — is an EVENT: the creature looks at the
     * new situation now (a bond just formed, a scale just taken) instead of at its next scheduled look.
     */
    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof PathfinderMob mob && mob.level() instanceof ServerLevel
                && !EcologyProfileRegistry.of(mob.getType()).isEmpty()) {
            EcologySensing.state(mob).wake();
        }
    }
}
