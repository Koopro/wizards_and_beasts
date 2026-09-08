package at.koopro.wizardsandbeasts.stats;

/**
 * The single place where a {@link PlayerStat} value becomes a gameplay number.
 *
 * <p>Every curve is a plain linear interpolation across the 0–100 stat range, deliberately: the
 * S-curve in {@link StatTrainingScaler} already makes the <em>last</em> points of a stat expensive to
 * earn, so bending the payout as well would compound two difficulty curves into one that reads as
 * "training does nothing". Earning is hard; spending is linear.
 *
 * <p>Magnitudes are moderate on purpose — roughly ±15% around the neutral value. The cast pipeline
 * ({@link at.koopro.wizardsandbeasts.spell.cast.ModifierStack}) already stacks wand condition,
 * allegiance, skill trees, vocations, Obscurial rules, niffler happiness and Dark corruption into the
 * same product, and clamps the result to [0.25, 3.0]. A stat that swung harder would spend that
 * budget on its own and silently clip the systems multiplied after it.
 *
 * <p>Pure math, no Minecraft state — see {@code StatEffectsTest}, which pins every endpoint below.
 */
public final class StatEffects {

    // ── Cast pipeline ────────────────────────────────────────────────────────────────────────
    /** Spell damage multiplier at POWER 0 / POWER 100. Neutral (1.0) lands at POWER 33. */
    static final float DAMAGE_AT_ZERO = 0.90f;
    static final float DAMAGE_AT_MAX  = 1.20f;

    /** Additive misfire-chance delta. Negative: PRECISION removes fizzles, it never adds them. */
    static final float MISFIRE_AT_ZERO = 0.00f;
    static final float MISFIRE_AT_MAX  = -0.08f;

    /** Cooldown multiplier. Below 1.0 is faster, so the max-REFLEXES end is the small one. */
    static final float COOLDOWN_AT_ZERO = 1.10f;
    static final float COOLDOWN_AT_MAX  = 0.88f;

    // ── Willpower: the trait that governs the Resolve pool ───────────────────────────────────
    /**
     * Scalar applied to mind-magic resist rolls (Imperius throw-off, Occlumency defence).
     * Never reaches 0 — a trait-0 player must still be able to break free, which
     * {@code ImperioResistReachabilityTest} asserts.
     */
    static final float RESIST_AT_ZERO = 0.40f;
    static final float RESIST_AT_MAX  = 1.00f;

    /** Ceiling of the Resolve pool ({@code ModAttachments.RESOLVE}). */
    static final float MAX_RESOLVE_AT_ZERO = 50.0f;
    static final float MAX_RESOLVE_AT_MAX  = 100.0f;

    /** Resolve regenerated per second while not under control. */
    static final float RESOLVE_REGEN_PER_SEC_AT_ZERO = 0.5f;
    static final float RESOLVE_REGEN_PER_SEC_AT_MAX  = 1.5f;

    /**
     * What a resist attempt costs, as a <em>fraction of the ceiling</em> rather than a flat amount.
     *
     * <p>The values match the flat 30 / 15 the Imperius code used against its old fixed 0–100 pool,
     * so a max-Willpower player's economy is unchanged. Charging a flat amount against a
     * trait-scaled ceiling would have punished low Willpower twice — worse odds per attempt
     * <em>and</em> fewer attempts before exhaustion — which at trait 0 emptied the pool in three
     * failures and then left it 90 seconds from useful. The trait governs the odds and the refill
     * speed; how many attempts you get is the same for everyone.
     */
    static final float RESOLVE_COST_BREAK_FREE = 0.30f;
    static final float RESOLVE_COST_FAILED_ATTEMPT = 0.15f;

    // ── Knowledge: the one thing being well-read buys you ────────────────────────────────────
    /**
     * Multiplier on what a spell teacher charges for a lesson.
     *
     * <p>KNOWLEDGE had no gameplay consumer at all — it was derived, synced and displayed, and
     * nothing in the mod read it. One consequence, not a system: a wizard who has already read the
     * books, walked the bestiary and worked the skill web arrives at the lesson knowing half of it,
     * and is charged accordingly. It attaches to {@code SpellLearningService}, which is the only
     * place KNOWLEDGE's four sources and spending money already meet.
     *
     * <p>40% off at KNOWLEDGE 100 is deliberately modest. The teacher fee is configurable and can be
     * switched off entirely ({@code Config.spellTeacherRequirePayment}), so this must not be the
     * reason anyone trains a stat — it is the reward for having trained everything else.
     */
    static final float TUITION_AT_ZERO = 1.00f;
    static final float TUITION_AT_MAX  = 0.60f;

    private StatEffects() {}

    /** Spell damage multiplier for a POWER value. Feeds {@code ModifierStack.multiplyDamage}. */
    public static float damageMultiplier(int power) {
        return lerp(DAMAGE_AT_ZERO, DAMAGE_AT_MAX, power);
    }

    /**
     * Misfire-chance delta for a PRECISION value — always ≤ 0. Feeds
     * {@code ModifierStack.addMisfireChance}, whose reader clamps the running total to [0, 1], so a
     * negative contribution can never drive the final chance below zero.
     */
    public static float misfireDelta(int precision) {
        return lerp(MISFIRE_AT_ZERO, MISFIRE_AT_MAX, precision);
    }

    /** Cooldown multiplier for a REFLEXES value. Feeds {@code ModifierStack.multiplyCooldown}. */
    public static float cooldownMultiplier(int reflexes) {
        return lerp(COOLDOWN_AT_ZERO, COOLDOWN_AT_MAX, reflexes);
    }

    /** Multiplier on a mind-magic resist chance for a WILLPOWER value. */
    public static float resistScalar(int willpower) {
        return lerp(RESIST_AT_ZERO, RESIST_AT_MAX, willpower);
    }

    /** Ceiling of the Resolve pool for a WILLPOWER value. Never 0 — division by it is safe. */
    public static float maxResolve(int willpower) {
        return lerp(MAX_RESOLVE_AT_ZERO, MAX_RESOLVE_AT_MAX, willpower);
    }

    /** Resolve regenerated per tick for a WILLPOWER value. */
    public static float resolveRegenPerTick(int willpower) {
        return lerp(RESOLVE_REGEN_PER_SEC_AT_ZERO, RESOLVE_REGEN_PER_SEC_AT_MAX, willpower) / 20.0f;
    }

    /** Resolve spent throwing off a mind-control curse, for a WILLPOWER value. */
    public static float resolveCostToBreakFree(int willpower) {
        return maxResolve(willpower) * RESOLVE_COST_BREAK_FREE;
    }

    /** Resolve spent on a resist attempt that failed, for a WILLPOWER value. */
    public static float resolveCostOfFailedAttempt(int willpower) {
        return maxResolve(willpower) * RESOLVE_COST_FAILED_ATTEMPT;
    }

    /**
     * How full a Resolve pool is, as a fraction of the ceiling a WILLPOWER value sets. Always in [0, 1].
     *
     * <p>The clamp is the point. Resolve is a stored float and the ceiling is derived from a trait that can
     * move underneath it — a lowered WILLPOWER, a heritage re-roll, or a save written while the pool was a
     * flat 0–100 — and the regeneration tick only ever clamped <em>upward</em>, so a charge left above the
     * ceiling stayed there permanently. A raw {@code resolve / ceiling} then exceeded 1 and multiplied the
     * Imperius resist chance past what any amount of training is meant to buy: the one direction this
     * number must never go, and invisible in play because it presents as unusually good luck.
     */
    public static float resolveCharge(float resolve, int willpower) {
        return Math.max(0f, Math.min(1f, resolve / maxResolve(willpower)));
    }

    /** Multiplier on a spell teacher's fee for a KNOWLEDGE value. Never below {@link #TUITION_AT_MAX}. */
    public static float tuitionMultiplier(int knowledge) {
        return lerp(TUITION_AT_ZERO, TUITION_AT_MAX, knowledge);
    }

    /**
     * A teacher's fee in knuts after the KNOWLEDGE discount, never below 1 for a lesson that costs
     * anything at all.
     *
     * <p>Both the quoted price and the amount actually withdrawn go through here, so the number on
     * the offer card is the number that leaves the vault. Quoting from one formula and charging from
     * another is how a shop ends up refusing a purchase it just advertised as affordable.
     *
     * <p>Rounded to nearest rather than up. {@code ceil} looks like the conservative choice and is
     * the wrong one here: {@link #TUITION_AT_MAX} lands on 0.6000000238 in {@code float}, so a
     * 100-knut lesson at KNOWLEDGE 100 came to 60.000002 and was billed as <em>61</em> — a player
     * told they had earned 40% off, charged 40% minus a knut, with no way to tell why.
     */
    public static int tuitionCost(int baseCostKnuts, int knowledge) {
        if (baseCostKnuts <= 0) return 0;
        return Math.max(1, Math.round(baseCostKnuts * tuitionMultiplier(knowledge)));
    }

    /** Linear interpolation from {@code atZero} to {@code atMax} over a stat clamped to 0–100. */
    private static float lerp(float atZero, float atMax, int stat) {
        float normalised = Math.max(0f, Math.min(1f, stat / 100.0f));
        return atZero + (atMax - atZero) * normalised;
    }
}
