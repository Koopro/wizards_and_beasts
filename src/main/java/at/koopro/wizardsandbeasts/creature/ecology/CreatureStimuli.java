package at.koopro.wizardsandbeasts.creature.ecology;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.creature.profile.CreatureReaction;
import at.koopro.wizardsandbeasts.creature.profile.CreatureReaction.Response;
import at.koopro.wizardsandbeasts.creature.profile.CreatureReaction.Stimulus;
import at.koopro.wizardsandbeasts.creature.schedule.AiMetrics;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import static at.koopro.wizardsandbeasts.creature.ecology.EcologyCapability.*;

/**
 * Raises the {@link CreatureReaction} stimuli from the things that actually happen: a spell cast, a creature dying, a
 * creature being hurt. Until now the reaction vocabulary was declared on creatures and raised by nothing.
 *
 * <p>Each event does one bounded query around where it happened — per event, never per tick. A creature's answer is
 * what its definition declares for that stimulus; a creature that declares nothing answers from its ecology
 * (a herd scatters when one of it falls, a curious creature with a sense for magic comes to see a spell, a prey
 * animal bolts from one). The answer is held for {@link EcologySensing#STIMULUS_TICKS} and acted on by
 * {@link EcologyGoal} through {@link EcologyBrain}, so it competes with everything else in one ordered policy.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class CreatureStimuli {

    /** Spells are noticed this far by an ordinary creature, twice as far with a magical sense. */
    public static final double SPELL_RADIUS = 12.0;
    /** Kin falling is noticed this far. */
    public static final double DEATH_RADIUS = 16.0;

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
            // A predator that killed its prey — by its hunt or by any other bite — has eaten.
            if (event.getSource().getEntity() instanceof PathfinderMob killer) {
                EcologyProfile hunter = EcologyProfileRegistry.of(killer.getType());
                if (EcologySensing.isPrey(hunter, killer, dead)) {
                    EcologyGoal.fed(EcologySensing.state(killer), level.getGameTime(), hunter.fedTicks());
                }
            }
        }
    }

    /** A hurt creature answers, and — if social — so do its kin within its herd radius. */
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
        Response own = responseOf(hurt, profile, Stimulus.DAMAGE);
        if (own != Response.IGNORE) {
            hold(hurt, own, from, level.getGameTime());
        }
        if (profile.has(SOCIAL)) {
            AiMetrics.inc(AiMetrics.Metric.STIMULUS_QUERY);
            for (PathfinderMob kin : level.getEntitiesOfClass(PathfinderMob.class,
                    hurt.getBoundingBox().inflate(profile.herdRadius() * 1.5), e -> e.getType() == hurt.getType() && e != hurt)) {
                Response answer = profile.has(AGGRESSIVE) || profile.has(TERRITORIAL) ? Response.ATTACK
                        : profile.has(PREY) || profile.has(COWARDLY) ? Response.FLEE : Response.BECOME_ALERT;
                hold(kin, answer, from, level.getGameTime());
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
                new net.minecraft.world.phys.AABB(at, at).inflate(reach), e -> e != cause && e.isAlive())) {
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
                hold(mob, answer, at, now);
            }
        }
    }

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
                    ? (profile.has(CURIOUS) ? Response.INVESTIGATE
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
    private static void hold(PathfinderMob mob, Response response, Vec3 source, long now) {
        EcologyState state = EcologySensing.state(mob);
        state.react(new Perception.Stimulus(response, source), now, now + EcologySensing.STIMULUS_TICKS);
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
