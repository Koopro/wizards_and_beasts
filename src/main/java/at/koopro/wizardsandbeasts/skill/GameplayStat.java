package at.koopro.wizardsandbeasts.skill;

/**
 * Scalar gameplay bonuses granted by skill nodes. Aggregated per-player in
 * {@link SkillEffectCache} (summed as {@code perLevel * level}) and read by gameplay
 * handlers via {@link SkillSystemAPI#getGameplayBonus(net.minecraft.server.level.ServerPlayer, GameplayStat)}.
 *
 * <p>Unlike {@link SkillEffect.UnlockAbility} (a boolean flag) these are continuous, level-scaling
 * numbers — the right tool whenever a skill says "{@code +X% per level}".
 */
public enum GameplayStat {
    /** Fractional reduction of incoming damage dealt by non-player creatures ({@code 0.10} = -10%). */
    BEAST_DAMAGE_RESISTANCE,
    /** Per-harvest chance to double a broken crop's drops ({@code 0.15} = 15%). */
    HARVEST_BONUS_CHANCE,
    /**
     * Absolute reduction of the cast's misfire chance ({@code 0.05} = five percentage points off).
     * Subtracted from the {@code ModifierStack} misfire total in
     * {@link SkillSystemAPI#applySkillModifiers}, which clamps the running sum to {@code [0, 1]} —
     * so it can cancel a wand's fizzle or an unbound wand's penalty, and never goes negative.
     */
    SPELL_MISFIRE_REDUCTION,
    /**
     * Added to the target's trained Occlumency level when someone casts Legilimency on them
     * ({@code 0.25} = a quarter of a full defence). Read in {@code LegilimencyServerLogic}; the sum
     * of trained level and web bonus is clamped before it scales by Willpower, so a fully studied
     * Occlumens cannot exceed the defence a trained one already had.
     */
    OCCLUMENCY_SHIELD,
    /**
     * Extra blocks of wand-light, as whole steps ahead of the caster ({@code 2.0} = two further lights).
     * Read by {@code LumosFieldEffect}: a trained Lumos lights the corridor, not just the hand holding it.
     */
    LIGHT_REACH,
    /**
     * Fractional bonus to a Shield Charm's absorb pool ({@code 0.25} = a quarter more damage turned aside).
     * Read where {@code ProtegoRules.integrity} is called, so it composes with proficiency and cast power
     * rather than replacing either.
     */
    WARD_INTEGRITY,
    /** Fractional bonus to how long a raised ward stands ({@code 0.20} = a fifth longer). */
    WARD_LIFETIME,
    /**
     * Extra duels a wand's allegiance survives before it changes hands ({@code 1.0} = one more win needed).
     * Read in {@code WandAllegianceService.onDefeat}. A wand still chooses the wizard; this is the wizard
     * knowing their own wand well enough that losing once does not lose it.
     */
    ALLEGIANCE_GRIP,
    /**
     * A wild creature's willingness to be approached, as discovery tiers ({@code 1.0} = as if you had studied
     * it one tier further). Read in {@code WildlifeWorld}, so it reaches every wary creature at once.
     */
    CREATURE_TRUST,
    /**
     * Fractional bonus to the chance a creature yields what it is known for ({@code 0.25} = a quarter more
     * often). Read in {@code BestiaryHarvestLootModifier}: careful hands find what careless ones miss.
     */
    GENTLE_HARVEST,
    /**
     * Absolute reduction of a Dark Arts cast's misfire chance ({@code 0.05} = five points off), and nothing
     * off any other category. Control over dangerous magic, which is the only thing the Dark Arts web
     * <em>should</em> be able to buy.
     */
    CURSE_BACKLASH,
    /**
     * Fractional reduction of how far a cast is noticed ({@code 0.25} = a quarter off the witness radius).
     * Read in {@code CastSurvey}. Casting quietly, not casting invisibly: a crowd at your elbow still sees.
     */
    TRACE_DISCRETION;

    /**
     * Whether this stat is a count rather than a fraction.
     *
     * <p>Read by {@code SkillEffectSummary}: lights ahead, duels survived and tiers of trust are whole things,
     * and printing "+100%" where the game means "+1" is a tooltip that lies about its own effect.
     */
    public boolean isCount() {
        return this == LIGHT_REACH || this == ALLEGIANCE_GRIP || this == CREATURE_TRUST;
    }
}
