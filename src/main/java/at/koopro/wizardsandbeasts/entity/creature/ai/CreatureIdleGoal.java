package at.koopro.wizardsandbeasts.entity.creature.ai;

import at.koopro.wizardsandbeasts.creature.profile.IdleProfile;
import at.koopro.wizardsandbeasts.creature.schedule.AiCadence;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * The small punctuation of standing still: a creature that preens, grazes, sniffs or shakes itself
 * between the wandering and the looking around.
 *
 * <p>A goal rather than a tick hook because it has to lose to everything else. Registered at priority
 * 8 — below the wander at 5 and every combat and panic goal, above only the look goals — so a
 * creature never preens while something is trying to eat it, and the goal simply never runs while a
 * higher one is active.
 *
 * <p>It plays animation clips and nothing more. No movement, no target, no state: the idle vocabulary
 * is expressive precisely because it costs nothing and cannot conflict with the movement goals that
 * already work. Clips route through {@code triggerDeclared}, which no-ops when the rig has not
 * declared the clip, so handing an idle profile to a creature whose model has no {@code preen} is
 * inert rather than a render-pass crash.
 */
@NullMarked
public class CreatureIdleGoal extends Goal {

    private final GenericBeastEntity beast;
    private int cooldown;
    /** The playable actions, recomputed only when the profile's action list or the rig's clip list changes. */
    private List<String> playable = List.of();
    private @org.jspecify.annotations.Nullable List<String> playableFrom;
    private @org.jspecify.annotations.Nullable List<String> playableClips;

    public CreatureIdleGoal(GenericBeastEntity beast) {
        this.beast = beast;
        // No flags: this goal moves nothing and looks at nothing, so it must not hold MOVE or LOOK
        // against the goals that do. Taking a flag here would have been the quiet way to stop every
        // creature wandering.
        setFlags(java.util.EnumSet.noneOf(Goal.Flag.class));
        this.cooldown = beast.getRandom().nextInt(IdleProfile.DEFAULT_MAX_DELAY);
    }

    @Override
    public boolean canUse() {
        if (!ModuleManager.isEnabled(Module.CREATURES) || !(beast.level() instanceof ServerLevel)) {
            return false;
        }
        if (beast.getTarget() != null || beast.isInWater() || beast.isVehicle()) {
            return false;
        }
        if (--cooldown > 0) {
            return false;
        }
        if (playableActions().isEmpty()) {
            // Nothing this rig can play: ask again after a SLOW period, not on every goal evaluation (most of the
            // roster has no idle clip on disk yet, so this is the common case). A datapack reload that adds clips
            // is picked up within that period.
            cooldown = AiCadence.SLOW.period();
            return false;
        }
        return true;
    }

    @Override
    public void start() {
        List<String> actions = playableActions();
        if (actions.isEmpty()) {
            reschedule();
            return;
        }
        beast.triggerDeclared(actions.get(beast.getRandom().nextInt(actions.size())));
        reschedule();
    }

    /** Single-shot: fire the clip and stand down, so the movement goals get the tick back. */
    @Override
    public boolean canContinueToUse() {
        return false;
    }

    private void reschedule() {
        IdleProfile profile = beast.idleProfile();
        int span = Math.max(1, profile.maxDelay() - profile.minDelay());
        cooldown = profile.minDelay() + beast.getRandom().nextInt(span);
    }

    /**
     * The profile's actions filtered to the ones this rig can actually play.
     *
     * <p>Filtered here rather than trusted at trigger time so an entirely unplayable profile makes
     * {@link #canUse()} answer false and the goal never starts — otherwise the goal would wake on
     * every cooldown to do nothing.
     */
    private List<String> playableActions() {
        List<String> declared = beast.declaredClipNames();
        List<String> actions = beast.idleProfile().actions();
        // Both lists are the definition's own (a body plan's default actions are a shared constant), so identity
        // says whether anything changed; a reload replaces them and the next call recomputes.
        if (actions != playableFrom || declared != playableClips) {
            playableFrom = actions;
            playableClips = declared;
            playable = actions.stream().filter(declared::contains).toList();
        }
        return playable;
    }
}
