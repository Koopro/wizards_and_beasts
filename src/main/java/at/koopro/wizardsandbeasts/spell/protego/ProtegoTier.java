package at.koopro.wizardsandbeasts.spell.protego;

/**
 * The four shapes one Protego cast can take. There is one spell; the tier is chosen at release from
 * how long the wand was held, capped by proficiency and (for Horribilis) Dark Arts depth — see
 * {@link ProtegoRules#resolveTier}.
 *
 * <p>Each tier is meant to play differently, not just be a bigger number:
 * <ul>
 *   <li>{@link #PROTEGO} — a quick parry. A disc in front of the caster that only stops what comes
 *       at their front, raises instantly and recovers fast.</li>
 *   <li>{@link #TOTALUM} — a bubble that walks with the caster and shelters whoever stands close.</li>
 *   <li>{@link #MAXIMA} — a wide, heavy dome. Can be planted to hold ground for a group.</li>
 *   <li>{@link #HORRIBILIS} — Maxima's footprint with the deepest pool, swallows Dark spells whole,
 *       and costs the caster to raise and to lose.</li>
 * </ul>
 *
 * <p>Pure data with no Minecraft types, so the balance table is unit-testable without a game.
 * Durations are in ticks; integrity is in half-hearts (the same unit as damage).
 */
public enum ProtegoTier {
    //          radius integ. life  life/prof charge cooldown lockout exhaust colour
    PROTEGO(    1.6,   10f,   60,   80,       0,     0.6f,    40,     0f,    0xFFB8D8FF,
            false, false, false, true),
    TOTALUM(    3.0,   18f,   100,  140,      8,     1.0f,    60,     0f,    0xFF8FE3FF,
            true,  false, false, false),
    MAXIMA(     5.5,   40f,   160,  240,      18,    1.5f,    100,    2f,    0xFFD6ECFF,
            true,  true,  false, false),
    HORRIBILIS( 5.5,   60f,   160,  240,      32,    2.0f,    160,    8f,    0xFFC39BFF,
            true,  true,  true,  false);

    private final double radius;
    private final float baseIntegrity;
    private final int baseLifetimeTicks;
    private final int lifetimePerProficiencyTicks;
    private final int baseChargeTicks;
    private final float cooldownFactor;
    private final int breachLockoutTicks;
    private final float raiseExhaustion;
    private final int colour;
    private final boolean coversAllies;
    private final boolean plantable;
    private final boolean absorbsDark;
    private final boolean frontalOnly;

    ProtegoTier(double radius, float baseIntegrity, int baseLifetimeTicks, int lifetimePerProficiencyTicks,
                int baseChargeTicks, float cooldownFactor, int breachLockoutTicks, float raiseExhaustion,
                int colour, boolean coversAllies, boolean plantable, boolean absorbsDark, boolean frontalOnly) {
        this.radius = radius;
        this.baseIntegrity = baseIntegrity;
        this.baseLifetimeTicks = baseLifetimeTicks;
        this.lifetimePerProficiencyTicks = lifetimePerProficiencyTicks;
        this.baseChargeTicks = baseChargeTicks;
        this.cooldownFactor = cooldownFactor;
        this.breachLockoutTicks = breachLockoutTicks;
        this.raiseExhaustion = raiseExhaustion;
        this.colour = colour;
        this.coversAllies = coversAllies;
        this.plantable = plantable;
        this.absorbsDark = absorbsDark;
        this.frontalOnly = frontalOnly;
    }

    /** Clamped lookup — the synced tier index is an int and must never throw on the client. */
    public static ProtegoTier byIndex(int index) {
        ProtegoTier[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, index))];
    }

    public int index() {
        return ordinal();
    }

    public ProtegoTier previous() {
        return this == PROTEGO ? PROTEGO : values()[ordinal() - 1];
    }

    public ProtegoTier next() {
        return this == HORRIBILIS ? HORRIBILIS : values()[ordinal() + 1];
    }

    /** Protected sphere radius in blocks, measured from the shield's centre. Also drives the render scale. */
    public double radius() {
        return radius;
    }

    /** Absorb pool before proficiency, spell power and planting are applied. */
    public float baseIntegrity() {
        return baseIntegrity;
    }

    public int baseLifetimeTicks() {
        return baseLifetimeTicks;
    }

    public int lifetimePerProficiencyTicks() {
        return lifetimePerProficiencyTicks;
    }

    /**
     * Hold ticks a NOVICE needs to reach this tier; practice shortens it
     * ({@link ProtegoRules#chargeSpeed}).
     *
     * <p>Tuned against how a hold actually arrives. A tap the player means as a tap measures 3-5
     * ticks once client release latency is in it, so the first step sits at 8 — far enough that a
     * flinch never buys a bubble by accident. The whole climb then wants to fit inside one exchange
     * of a duel: 18 (0.9s) for a dome and 32 (1.6s) for Horribilis, which a mastered caster reaches
     * in 22 ticks.
     */
    public int baseChargeTicks() {
        return baseChargeTicks;
    }

    /** Multiplied into the cast's cooldown channel. Below 1 recovers faster than the spell's base. */
    public float cooldownFactor() {
        return cooldownFactor;
    }

    /** Recast lockout imposed when this tier is broken by damage (not when it simply runs out). */
    public int breachLockoutTicks() {
        return breachLockoutTicks;
    }

    /** Food exhaustion paid on raise. Vanilla sprinting costs 0.1 per metre; 4.0 is one hunger point. */
    public float raiseExhaustion() {
        return raiseExhaustion;
    }

    /** ARGB identity colour used by particles and the renderer. */
    public int colour() {
        return colour;
    }

    /** Whether players and pets standing inside are sheltered, not just the caster. */
    public boolean coversAllies() {
        return coversAllies;
    }

    /** Whether releasing while sneaking anchors the dome in place instead of following the caster. */
    public boolean plantable() {
        return plantable;
    }

    /** Whether Dark spells are swallowed at a discount instead of deflected at a premium. */
    public boolean absorbsDark() {
        return absorbsDark;
    }

    /** A disc, not a dome: only stops what arrives from the caster's front. */
    public boolean frontalOnly() {
        return frontalOnly;
    }

    /** Horribilis is the only tier whose breach hurts the caster. */
    public boolean backlashOnBreach() {
        return this == HORRIBILIS;
    }

    /**
     * The canon incantation this shape is known by. These are catalogue names, not registered
     * spells — Totalum, Maxima and Horribilis are ranks of the one Protego, and naming them here is
     * what lets the charge feedback call a dome by its proper name.
     */
    public String canonId() {
        return switch (this) {
            case PROTEGO -> "protego";
            case TOTALUM -> "protego_totalum";
            case MAXIMA -> "protego_maxima";
            case HORRIBILIS -> "protego_horribilis";
        };
    }

    /** Translation key for {@link #canonId()}; the four names already ship in {@code en_us.json}. */
    public String nameKey() {
        return "spell.wizards_and_beasts." + canonId() + ".name";
    }
}
