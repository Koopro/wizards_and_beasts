package at.koopro.wizardsandbeasts.wand.allegiance;

import at.koopro.wizardsandbeasts.wand.registry.WandTemperament;

/**
 * The wand–wizard relationship as arithmetic: no Minecraft types, no {@code Config}, so every rule is
 * unit-testable on its own. {@code WandAllegianceService} reads the world and applies these.
 *
 * <p><b>What is canon and what is interpretation.</b> Rowling's wandlore (Ollivander's wood and core notes,
 * <i>Deathly Hallows</i> ch. 24) supplies the shape: a wand chooses its wizard; a won wand changes allegiance
 * and a wand merely picked up does not; another wizard's wand works, but worse; some cores and woods hold to
 * their master harder than others. Every number here, and the exact triggers, are gameplay interpretation.
 *
 * <p><b>No dice.</b> Nothing in this class rolls. A wand that serves you badly does so the same way every
 * time, for a reason the tooltip can state.
 */
public final class WandAllegianceRules {

    /** Bond below this: reluctant. */
    public static final float RELUCTANT_BELOW = 0.35f;
    /** Bond below this: accepting. */
    public static final float ACCEPTING_BELOW = 0.70f;
    /** Bond below this: loyal; at or above: mastered. */
    public static final float LOYAL_BELOW = 0.95f;

    /** Bond gained per successful cast before temperament. A first bond reaches loyal in a few dozen casts. */
    public static final float BASE_BOND_GROWTH = 0.004f;
    /** Defeats of the master that win an ordinary wand, before temperament. */
    public static final int BASE_WINS_TO_TRANSFER = 2;
    /** The bond a wand holds for the wizard who has just won it: reluctant. */
    public static final float BASE_TRANSFER_BOND = 0.15f;

    /** Power another wizard's wand lends its holder, before temperament. */
    public static final float FOREIGN_POWER = 0.70f;
    public static final float FOREIGN_COOLDOWN = 1.25f;
    public static final float RELUCTANT_POWER = 0.85f;
    public static final float RELUCTANT_COOLDOWN = 1.10f;
    public static final float LOYAL_POWER = 1.05f;
    public static final float MASTERED_POWER = 1.10f;

    /** The Elder Wand, mastered: extraordinary. Otherwise it serves worse than an ordinary wand. */
    public static final float ELDER_MASTERED_POWER = 1.35f;
    public static final float ELDER_UNMASTERED_FACTOR = 0.80f;

    /** Game ticks a master may leave a wand unused before the bond starts to cool: three days. */
    public static final long NEGLECT_GRACE_TICKS = 72_000L;
    public static final long TICKS_PER_DAY = 24_000L;
    public static final float NEGLECT_LOSS_PER_DAY = 0.02f;

    /** Integrity at or below which a wand is broken. */
    public static final float BROKEN_AT = 0.10f;
    /** Explosion damage that costs a held wand all of its integrity. */
    public static final float EXPLOSION_DAMAGE_TO_BREAK = 60.0f;

    private WandAllegianceRules() {}

    /** A wand's physical condition, read from integrity. */
    public enum Condition { SOUND, WORN, DAMAGED, BROKEN }

    /** The circumstances of one successful cast that the bond reacts to. */
    public record CastCircumstances(boolean darkArts, boolean inDanger, boolean witnessedDeath) {}

    /** What a defeat of the master did: the challenger's running tally, and whether that won the wand. */
    public record DefeatOutcome(WandBondHistory history, boolean transferred) {}

    /**
     * @param casterIsMaster whether the holder is the wand's master
     * @param bond           the bond with that master
     */
    public static WandBondState state(boolean casterIsMaster, float bond) {
        if (!casterIsMaster) return WandBondState.UNFAMILIAR;
        if (bond < RELUCTANT_BELOW) return WandBondState.RELUCTANT;
        if (bond < ACCEPTING_BELOW) return WandBondState.ACCEPTING;
        if (bond < LOYAL_BELOW) return WandBondState.LOYAL;
        return WandBondState.MASTERED;
    }

    public static Condition condition(float integrity) {
        if (integrity <= BROKEN_AT) return Condition.BROKEN;
        if (integrity < 0.5f) return Condition.DAMAGED;
        if (integrity < 0.9f) return Condition.WORN;
        return Condition.SOUND;
    }

    /**
     * The bond a wand starts with when it chooses a wizard: accepting for a bare match, loyal for a perfect one.
     * "The wand chooses the wizard" — a wand that chose you is never reluctant about it.
     */
    public static float startingBond(float resonanceScore, float matchThreshold) {
        float span = Math.max(1.0e-3f, 1.0f - matchThreshold);
        float fit = clamp01((resonanceScore - matchThreshold) / span);
        return 0.40f + 0.40f * fit;
    }

    /**
     * The bond after one successful cast by the master.
     *
     * <ul>
     *   <li>Dark Arts through a wand that resents them (unicorn hair, rowan) costs bond and gains none.</li>
     *   <li>A wand whose bond needs danger (blackthorn) deepens only through casts made in danger.</li>
     *   <li>A wand that needs a wizard who has seen death (thestral tail hair) stops short of mastered without one.</li>
     * </ul>
     */
    public static float bondAfterSuccessfulCast(float bond, WandTemperament t, CastCircumstances c) {
        if (c.darkArts() && t.darkArtsBondCost() > 0.0f) {
            return clamp01(bond - t.darkArtsBondCost());
        }
        float growth = BASE_BOND_GROWTH * t.bondGrowth();
        if (t.bondNeedsDanger() && !c.inDanger()) {
            growth = 0.0f;
        }
        float next = clamp01(bond + growth);
        if (t.masteryNeedsDeathWitness() && !c.witnessedDeath() && next >= LOYAL_BELOW) {
            next = Math.max(bond, Math.nextDown(LOYAL_BELOW));
        }
        return next;
    }

    /**
     * The bond after a master has left the wand unused for {@code idleTicks}. It cools slowly once the grace is
     * over and never below the edge of reluctance: neglect alone does not break a bond. Never raises it.
     */
    public static float bondAfterNeglect(float bond, long idleTicks) {
        if (idleTicks <= NEGLECT_GRACE_TICKS || bond <= RELUCTANT_BELOW) {
            return bond;
        }
        float days = (idleTicks - NEGLECT_GRACE_TICKS) / (float) TICKS_PER_DAY;
        return Math.max(RELUCTANT_BELOW, bond - days * NEGLECT_LOSS_PER_DAY);
    }

    /** How many defeats of its master win this wand. The Elder Wand goes with the first. */
    public static int winsToTransfer(WandTemperament t, boolean elderWand) {
        return elderWand ? 1 : Math.max(1, BASE_WINS_TO_TRANSFER + t.extraWins());
    }

    /** The bond a wand holds for the wizard who has just won it. */
    public static float bondAfterTransfer(WandTemperament t) {
        return Math.min(Math.nextDown(ACCEPTING_BELOW), clamp01(BASE_TRANSFER_BOND + t.transferBondBonus()));
    }

    /** One defeat of the master by {@code victor}. */
    public static DefeatOutcome defeat(WandBondHistory history, java.util.UUID victor, int winsNeeded) {
        WandBondHistory next = history.withDefeatBy(victor);
        return new DefeatOutcome(next, next.challengerWins() >= winsNeeded);
    }

    /**
     * The power a wand lends its holder, from the relationship alone.
     *
     * @param passedOn   the wand serves someone other than its first master
     * @param elderWand  the Elder Wand itself, not merely elder wood
     */
    public static float powerMultiplier(WandBondState state, WandTemperament t, boolean passedOn, boolean elderWand) {
        float power = switch (state) {
            case UNFAMILIAR -> FOREIGN_POWER * t.foreignHandPower();
            case RELUCTANT -> RELUCTANT_POWER;
            case ACCEPTING -> 1.0f;
            case LOYAL -> LOYAL_POWER;
            case MASTERED -> MASTERED_POWER;
        };
        if (passedOn && state.isMaster()) {
            power *= t.passedOnPower();
        }
        if (elderWand) {
            power *= state == WandBondState.MASTERED ? ELDER_MASTERED_POWER / MASTERED_POWER : ELDER_UNMASTERED_FACTOR;
        }
        return power;
    }

    public static float cooldownMultiplier(WandBondState state, WandTemperament t) {
        return switch (state) {
            case UNFAMILIAR -> FOREIGN_COOLDOWN;
            case RELUCTANT -> RELUCTANT_COOLDOWN;
            case ACCEPTING -> 1.0f;
            case LOYAL, MASTERED -> t.loyalCooldown();
        };
    }

    /**
     * Whether a cast backfires instead of casting: always with a broken wand (Ron's in <i>Chamber of
     * Secrets</i>), and in a stranger's hand for a wand that turns on a careless one (hawthorn).
     */
    public static boolean backfires(WandBondState state, WandTemperament t, float integrity) {
        return integrity <= BROKEN_AT || (state == WandBondState.UNFAMILIAR && t.backfiresInForeignHands());
    }

    /** Integrity left after the holder takes {@code damage} from an explosion. */
    public static float integrityAfterExplosion(float integrity, float damage) {
        return clamp01(integrity - Math.max(0.0f, damage) / EXPLOSION_DAMAGE_TO_BREAK);
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
