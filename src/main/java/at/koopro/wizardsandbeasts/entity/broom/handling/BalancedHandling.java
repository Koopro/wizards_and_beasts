package at.koopro.wizardsandbeasts.entity.broom.handling;

/**
 * The middle of the range — the Comet 260, and what any broom naming no profile gets.
 *
 * <p>Deliberately almost empty. Every hook it does not override is the behaviour {@code BroomMovement}
 * had before profiles existed, so a definition written against the old schema flies exactly as it
 * used to, and the other three profiles are readable as deviations from something.
 *
 * <p>The one thing it does carry is the inherited momentum-scaled deceleration, which is the general
 * rule rather than a Comet quirk: a broom that keeps its momentum should also take longer to shed it.
 * Normalised so this profile's own momentum is the no-op — see
 * {@link BroomHandlingProfile#modifyDeceleration}.
 */
public final class BalancedHandling implements BroomHandlingProfile {

    @Override
    public String profileId() {
        return "balanced";
    }
}
