package at.koopro.wizardsandbeasts.spell.cast;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Locks the cast-power formula.
 *
 * <p>{@link SpellPower} is pure by design so this needs no Minecraft bootstrap and no player: the
 * formula that decides how hard a spell hits is checked as arithmetic, which is the only way it can
 * be checked at all. Bounds are passed explicitly rather than read from config, so a retune cannot
 * quietly turn a failing test green.
 */
class SpellPowerTest {

    private static final SpellPower.Bounds BOUNDS = SpellPower.DEFAULT_BOUNDS;
    private static final float EPS = 1.0e-4f;

    // -- composition order -----------------------------------------------------------------------

    @Test
    void neutralChannelsLeaveTheMultiplierAtOne() {
        assertEquals(1.0f, SpellPower.damage(1.0f, 1.0f, 1.0f, BOUNDS).total(), EPS);
        assertEquals(1.0f, SpellPower.cooldown(1.0f, 1.0f, 1.0f, BOUNDS).total(), EPS);
    }

    @Test
    void theThreeChannelsMultiply() {
        // 1.1 * 1.2 * 1.05 = 1.386, below the knee so no compression.
        SpellPower.Breakdown power = SpellPower.damage(1.1f, 1.2f, 1.05f, BOUNDS);
        assertEquals(1.386f, power.raw(), EPS);
        assertEquals(1.386f, power.total(), EPS);
        assertFalse(power.clamped());
    }

    @Test
    void theBreakdownKeepsItsChannelsSeparable() {
        SpellPower.Breakdown power = SpellPower.damage(1.1f, 1.2f, 1.05f, BOUNDS);
        assertEquals(1.1f, power.situational(), EPS);
        assertEquals(1.2f, power.proficiency(), EPS);
        assertEquals(1.05f, power.skill(), EPS);
    }

    /**
     * The regression this whole class exists for: proficiency belongs to exactly one channel.
     *
     * <p>{@code SkillSystemAPI} used to push the {@code Proficiency} enum tier (1.20 at mastery)
     * into the situational bag while the caller multiplied {@code ProficiencyScaler}'s float curve
     * on top, so a mastered spell was scaled by both. Composing through the named channels makes the
     * double application impossible to express.
     */
    @Test
    void proficiencyAppliedOnceIsWeakerThanProficiencyAppliedTwice() {
        float once = SpellPower.damage(1.0f, 1.2f, 1.0f, BOUNDS).total();
        float twice = SpellPower.damage(1.2f, 1.2f, 1.0f, BOUNDS).total();
        assertEquals(1.2f, once, EPS);
        assertTrue(twice > once, "the old double-count really was bigger; this test would have caught it");
    }

    // -- soft cap --------------------------------------------------------------------------------

    @Test
    void nothingBelowTheKneeIsCompressed() {
        for (float raw : new float[] {0.5f, 1.0f, 1.25f, 1.4999f}) {
            assertEquals(raw, SpellPower.boundDamage(raw, BOUNDS), EPS,
                    raw + " is under the knee and must pass through untouched");
        }
    }

    @Test
    void theCurveIsContinuousAtTheKnee() {
        float justUnder = SpellPower.boundDamage(BOUNDS.softCapKnee() - 0.001f, BOUNDS);
        float justOver = SpellPower.boundDamage(BOUNDS.softCapKnee() + 0.001f, BOUNDS);
        assertTrue(Math.abs(justOver - justUnder) < 0.01f,
                "no visible kink where compression starts: " + justUnder + " -> " + justOver);
    }

    @Test
    void theCeilingIsNeverExceeded() {
        for (float raw : new float[] {3.0f, 10.0f, 100.0f, 10_000.0f}) {
            float total = SpellPower.boundDamage(raw, BOUNDS);
            assertTrue(total <= BOUNDS.max(), raw + " must not pass the ceiling, got " + total);
        }
    }

    @Test
    void theCeilingIsApproachedAsymptotically() {
        // Double the knee is still visibly short of the ceiling: past the knee a player is losing
        // value, not hitting a wall.
        assertTrue(SpellPower.boundDamage(3.0f, BOUNDS) < BOUNDS.max() - 0.4f);
        // Far out, it is within a percent of the ceiling. In real arithmetic it never arrives; in
        // float the exponential underflows to zero somewhere past raw ~ 100 and it lands exactly on
        // the ceiling, which is a saturation this deliberately allows rather than pretends away.
        assertTrue(SpellPower.boundDamage(10.0f, BOUNDS) > BOUNDS.max() * 0.99f);
    }

    @Test
    void moreInvestmentIsNeverWorthLess() {
        float previous = -1.0f;
        for (float raw = 0.1f; raw <= 8.0f; raw += 0.05f) {
            float total = SpellPower.boundDamage(raw, BOUNDS);
            assertTrue(total >= previous, "not monotonic at raw=" + raw);
            previous = total;
        }
    }

    @Test
    void compressionStillRewardsAcrossTheReachableRange() {
        // Monotonic alone is satisfied by a hard clamp. This is the property a clamp fails: past the
        // knee, and all the way out to a multiplier no build will realistically reach, one more
        // point of raw multiplier still buys strictly more damage.
        // Starts above the damage floor: below it every input maps to `min`, which is flat on
        // purpose and is the one place strict increase does not hold.
        float previous = -1.0f;
        for (float raw = BOUNDS.min() + 0.05f; raw <= 5.0f; raw += 0.05f) {
            float total = SpellPower.boundDamage(raw, BOUNDS);
            assertTrue(total > previous, "compression flattened at raw=" + raw);
            previous = total;
        }
    }

    @Test
    void stackingSourcesDoesNotExplode() {
        // Six independent +40% sources: 1.4^6 = 7.53 raw.
        float raw = 1.0f;
        for (int i = 0; i < 6; i++) {
            raw *= 1.4f;
        }
        assertTrue(raw > 7.0f, "the raw product really is runaway: " + raw);
        assertTrue(SpellPower.boundDamage(raw, BOUNDS) < 3.0f);
    }

    // -- floors ----------------------------------------------------------------------------------

    @Test
    void damageHasAHardFloor() {
        assertEquals(BOUNDS.min(), SpellPower.damage(0.1f, 0.1f, 0.1f, BOUNDS).total(), EPS);
        assertEquals(BOUNDS.min(), SpellPower.damage(0.0f, 1.0f, 1.0f, BOUNDS).total(), EPS);
    }

    @Test
    void cooldownIsClampedBothWays() {
        // The floor is the anti-spam guard; the ceiling stops a penalty stack locking a spell away.
        assertEquals(BOUNDS.cooldownMin(), SpellPower.cooldown(0.1f, 0.1f, 0.1f, BOUNDS).total(), EPS);
        assertEquals(BOUNDS.cooldownMax(), SpellPower.cooldown(3.0f, 2.0f, 2.0f, BOUNDS).total(), EPS);
    }

    @Test
    void cooldownIsNotSoftCapped() {
        // Deliberately different from damage: a floor is a floor. Halving twice really does quarter.
        assertEquals(0.25f, SpellPower.cooldown(0.5f, 0.5f, 1.0f, BOUNDS).total(), EPS);
    }

    // -- robustness ------------------------------------------------------------------------------

    @Test
    void nonFiniteChannelsCollapseToTheFloorRatherThanPoisoningTheCast() {
        assertEquals(BOUNDS.min(), SpellPower.damage(Float.NaN, 1.0f, 1.0f, BOUNDS).total(), EPS);
        assertEquals(BOUNDS.min(), SpellPower.damage(Float.POSITIVE_INFINITY, 1.0f, 1.0f, BOUNDS).total(), EPS);
        assertEquals(BOUNDS.min(), SpellPower.damage(-2.0f, 1.0f, 1.0f, BOUNDS).total(), EPS);
    }

    @Test
    void theSameInputsAlwaysGiveTheSameNumber() {
        // The client renders this number in a tooltip and the server casts with it; if it were not
        // deterministic the tooltip would be a guess.
        float first = SpellPower.damage(1.13f, 1.27f, 1.41f, BOUNDS).total();
        for (int i = 0; i < 1000; i++) {
            assertEquals(first, SpellPower.damage(1.13f, 1.27f, 1.41f, BOUNDS).total(), 0.0f);
        }
    }

    @Test
    void percentIsWhatATooltipWouldPrint() {
        assertEquals(0, SpellPower.damage(1.0f, 1.0f, 1.0f, BOUNDS).percent());
        assertEquals(25, SpellPower.damage(1.25f, 1.0f, 1.0f, BOUNDS).percent());
        assertEquals(-35, SpellPower.damage(0.65f, 1.0f, 1.0f, BOUNDS).percent());
    }

    // -- bounds validation -----------------------------------------------------------------------

    @Test
    void nonsensicalBoundsAreRejectedAtConstruction() {
        // Config is hand-edited; a max below the knee has to fail loudly here rather than inside a cast.
        assertThrows(IllegalArgumentException.class, () -> new SpellPower.Bounds(2.0f, 1.5f, 0.25f, 0.25f, 2.0f));
        assertThrows(IllegalArgumentException.class, () -> new SpellPower.Bounds(1.5f, 3.0f, 2.0f, 0.25f, 2.0f));
        assertThrows(IllegalArgumentException.class, () -> new SpellPower.Bounds(1.5f, 3.0f, 0.25f, 3.0f, 2.0f));
    }
}
