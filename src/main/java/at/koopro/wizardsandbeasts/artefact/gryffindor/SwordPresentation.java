package at.koopro.wizardsandbeasts.artefact.gryffindor;

import org.jspecify.annotations.NullMarked;

/**
 * When the Sorting Hat gives up the Sword of Gryffindor: to its wearer, in mortal danger, standing their ground.
 *
 * <p>There is no house to sort into, so "a true Gryffindor" is read from what the wearer is doing: they struck the
 * creature that has just brought them low, a moment before it did -- they are fighting it, not fleeing it. The foes
 * are a tag ({@code wizards_and_beasts:sword_of_gryffindor_foes}) of things worth the sword: a basilisk, a
 * Dementor, an Obscurus, an acromantula, the Wither, the Warden. User decisions, 2026-10-22.
 */
@NullMarked
public final class SwordPresentation {

    /** Health at or under this fraction of the maximum is mortal danger. */
    public static final float DANGER = 0.3f;
    /** How long ago the wearer may have struck the foe and still be "standing their ground". */
    public static final long STOOD_GROUND_TICKS = 20L * 10L;

    private SwordPresentation() {}

    /**
     * @param ticksSinceStruckFoe ticks since the wearer last struck this foe; negative when never
     */
    public static boolean mayPresent(boolean moduleOn, boolean wearingHat, boolean swordInWorld, boolean foe,
                                     float health, float maxHealth, long ticksSinceStruckFoe) {
        return moduleOn && wearingHat && !swordInWorld && foe
                && health > 0 && health <= maxHealth * DANGER
                && ticksSinceStruckFoe >= 0 && ticksSinceStruckFoe <= STOOD_GROUND_TICKS;
    }
}
