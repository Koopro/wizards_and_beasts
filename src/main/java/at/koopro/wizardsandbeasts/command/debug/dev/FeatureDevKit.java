package at.koopro.wizardsandbeasts.command.debug.dev;

import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * How to get a feature in front of a developer, in one command.
 *
 * <h2>Why this exists beside the fine-grained commands</h2>
 *
 * <p>Nearly every subsystem already has setters — {@code /wandb magic spell learn},
 * {@code /wandb player skill unlock}, {@code /wandb world floo register}. What it did not have is an
 * answer to "I want to test brewing"; that took eight commands, and you had to already know which
 * eight. Half of testing a feature was remembering how to reach it, which is exactly the work a
 * developer should not be redoing every session.
 *
 * <p>So a kit answers three questions, and only these three:
 *
 * <ul>
 *   <li>{@link #open} — what is <em>gating</em> this, and open all of it. The module, the licence,
 *       the heritage requirement, the unlock flag.</li>
 *   <li>{@link #kit} — what do I need to be <em>holding</em>. The wand, the cauldron, the
 *       ingredients, the broom.</li>
 *   <li>{@link #reset} — put it back, so the next run starts from the same place.</li>
 * </ul>
 *
 * <p>Anything more specific than that stays in the feature's own command tree. A kit is a starting
 * position, not a second copy of the controls.
 *
 * <p>Every method reports through {@link DevLog}, because an action that rewrites progression
 * silently is one you cannot trust — see the note there. Defaults are no-ops that say so, so a
 * feature implements only the parts it has.
 */
@NullMarked
public interface FeatureDevKit {

    /**
     * Command literal and lookup key.
     *
     * <p>Matches the {@code FeatureDebugSection} id wherever the same feature has both, so
     * {@code /wandb debug feature brewing} and {@code /wandb debug dev kit brewing} name one thing.
     */
    String id();

    String title();

    /** One line for {@code /wandb debug dev} with no argument. */
    default String summary() {
        return "";
    }

    /** Open every gate standing between this player and the feature. */
    default void open(ServerPlayer target, DevLog log) {
        log.skip("nothing gates " + id());
    }

    /** Put the items needed to exercise it into the player's inventory. */
    default void kit(ServerPlayer target, DevLog log) {
        log.skip("no items needed for " + id());
    }

    /** Return the feature to a fresh state for this player. */
    default void reset(ServerPlayer target, DevLog log) {
        log.skip("nothing to reset for " + id());
    }
}
