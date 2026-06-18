package at.koopro.wizardsandbeasts.creature;

/**
 * Data-driven behaviour vocabulary applied to a generic creature via its {@link CreatureDefinition}.
 * Interpreted at runtime by {@code GenericBeastEntity} — no per-creature Java class. Extend freely;
 * a trait the entity does not yet handle is simply ignored.
 */
public enum Trait {
    /** Flees and actively avoids players; never initiates combat. */
    FEARFUL,
    /** Alerts nearby allies when hurt (loose pack behaviour). */
    PACK,
    /** Immune to fire and lava damage. */
    FIRE_IMMUNE,
    /** Melee hits set the target alight. */
    FIRE_ATTACK,
    /** Melee hits apply Poison. */
    POISON_ATTACK,
    /** Melee hits apply a heavy slow + blindness ("petrify-lite"). */
    PETRIFY,
    /** Faster pursuit and stronger knockback when attacking (charger). */
    CHARGE,
    /** Extra knockback on melee. */
    KNOCKBACK,
    /** On hitting a player, knocks a random held/inventory item to the ground and flees (mischief). */
    THIEF,
    /** Can breathe underwater (amphibious land beasts). */
    AMPHIBIOUS,
    /** Slowly regenerates health over time. */
    REGEN,
    /** Detonates on death. */
    EXPLODE_ON_DEATH
}
