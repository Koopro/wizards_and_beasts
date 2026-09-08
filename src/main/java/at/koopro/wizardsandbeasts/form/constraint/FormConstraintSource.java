package at.koopro.wizardsandbeasts.form.constraint;

import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * One system's answer to "is this player transformed, and if so what does that forbid them".
 *
 * <p>Implementations must be <b>pure and cheap</b>: this is called per interaction event and once per
 * player per tick, and it must never mutate anything. Read an attachment, compare a form id, return a
 * preset. A source with no claim on the player returns {@link FormConstraintSet#NONE}, never null.
 *
 * <p>Registered by id through {@link FormConstraints#register}.
 */
@FunctionalInterface
@NullMarked
public interface FormConstraintSource {

    FormConstraintSet constraintsFor(ServerPlayer player);
}
