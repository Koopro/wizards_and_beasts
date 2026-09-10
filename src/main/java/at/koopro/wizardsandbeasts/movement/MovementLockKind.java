package at.koopro.wizardsandbeasts.movement;

/**
 * How completely a movement lock takes a player's body away from them.
 *
 * <p>The distinction is not cosmetic. {@link #ROOTED} hands the body to physics with the
 * controls unplugged; {@link #PINNED} takes the body out of physics altogether. Getting it
 * the wrong way round is visible immediately — a stunned wizard hanging in mid-air, or a
 * petrified one sliding across the floor on the knockback from the curse that got them.
 */
public enum MovementLockKind {

    /**
     * The controls stop answering. Gravity, knockback, friction and fluids all still apply.
     *
     * <p>This is the honest shape of a stun: the wizard is still a body in the world, they
     * just cannot do anything with it. Someone stupefied off a tower falls off the tower.
     */
    ROOTED,

    /**
     * The body stops being subject to motion at all — no input, no gravity, no knockback.
     *
     * <p>For petrification, which is a wizard turned into stone furniture rather than a
     * wizard who has lost control. {@code ROOTED} is not enough here: a statue that creeps
     * a block sideways every time something shoves it is not petrified, and that creep is
     * exactly what the old per-tick {@code teleportTo} existed to undo.
     */
    PINNED
}
