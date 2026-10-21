package at.koopro.wizardsandbeasts.creature.ecology;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * A creature's ecological working memory: what it last perceived, what it decided, where home is, when it last ate,
 * and any reaction still in force. A transient attachment — not saved: on a reload the creature looks around again,
 * and the place it is standing becomes home, which is what a wild creature found there would do.
 */
@NullMarked
public final class EcologyState {

    @Nullable Perception perception;
    EcologyBrain.Decision decision = EcologyBrain.Decision.NONE;
    @Nullable Vec3 home;
    long fedUntil;
    Perception.@Nullable Stimulus stimulus;
    long stimulusUntil;
    @Nullable UUID preyId;
    @Nullable UUID playerId;
    long nextActionTick;
    /** EVENT tier: something happened (a stimulus, damage, an interaction) that deserves a look before the next one. */
    boolean wake;
    /** When it last looked around, so a burst of events cannot make it look around every tick. */
    long lastThinkTick = Long.MIN_VALUE / 2;

    public EcologyIntent intent() {
        return decision.intent();
    }

    public EcologyBrain.Decision decision() {
        return decision;
    }

    public @Nullable Perception perception() {
        return perception;
    }

    public @Nullable Vec3 home() {
        return home;
    }

    public boolean fedAt(long now) {
        return now < fedUntil;
    }

    /** Records a reaction answer, in force until {@code until}. A stronger answer (flight) is not overwritten. */
    public void react(Perception.Stimulus value, long now, long until) {
        boolean current = stimulus != null && now < stimulusUntil;
        if (current && stimulus.response() == at.koopro.wizardsandbeasts.creature.profile.CreatureReaction.Response.FLEE
                && value.response() != at.koopro.wizardsandbeasts.creature.profile.CreatureReaction.Response.FLEE) {
            return;
        }
        stimulus = value;
        stimulusUntil = until;
    }

    /**
     * Asks for a look around at the next goal evaluation instead of the next scheduled one (the EVENT tier,
     * documentation/CREATURE_AI_SCHEDULING.md). Costs a flag; the look itself is the usual one bounded query, and no
     * sooner than {@code EcologyGoal.EVENT_MIN_GAP} ticks after the last.
     */
    public void wake() {
        wake = true;
    }

    public boolean awake() {
        return wake;
    }

    /** Test and admin seam: forget home so the next look around sets it here. */
    public void resetHome(@Nullable Vec3 value) {
        home = value;
    }
}
