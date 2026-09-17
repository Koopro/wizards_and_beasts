package at.koopro.wizardsandbeasts.spell.protego;

import at.koopro.wizardsandbeasts.spell.core.Proficiency;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Shield Charm's ladder, pool and geometry — the parts that must hold without a game running. */
class ProtegoRulesTest {

    private static final int DEEP_DARK_ARTS = ProtegoRules.HORRIBILIS_DARK_ARTS_DEPTH;

    // ── tier ladder ─────────────────────────────────────────────────────────────────────────────

    @Test
    void aTapIsTheQuickParry() {
        assertSame(ProtegoTier.PROTEGO,
                ProtegoRules.resolveTier(0, Proficiency.MASTERED, DEEP_DARK_ARTS).tier());
        assertSame(ProtegoTier.PROTEGO,
                ProtegoRules.resolveTier(1, Proficiency.MASTERED, DEEP_DARK_ARTS).tier());
    }

    @Test
    void holdingLongerReachesEachShapeInTurn() {
        Proficiency master = Proficiency.MASTERED;
        for (ProtegoTier tier : ProtegoTier.values()) {
            int charge = ProtegoRules.chargeTicks(tier, master);
            assertSame(tier, ProtegoRules.resolveTier(charge, master, DEEP_DARK_ARTS).tier(),
                    tier + " must be what a mastered caster gets at exactly its charge time");
        }
    }

    /** Practice is what makes the wand answer sooner — the ladder is the same, the clock is not. */
    @Test
    void practiceShortensTheCharge() {
        for (ProtegoTier tier : ProtegoTier.values()) {
            if (tier.baseChargeTicks() == 0) {
                continue;
            }
            assertTrue(ProtegoRules.chargeTicks(tier, Proficiency.MASTERED)
                            < ProtegoRules.chargeTicks(tier, Proficiency.NOVICE),
                    tier + " should charge faster once mastered");
        }
    }

    @Test
    void aNoviceCannotHoldADomeHoweverLongTheyHold() {
        ProtegoRules.TierResolution resolution =
                ProtegoRules.resolveTier(1000, Proficiency.NOVICE, DEEP_DARK_ARTS);
        assertSame(ProtegoTier.TOTALUM, resolution.tier());
        assertSame(ProtegoTier.HORRIBILIS, resolution.reachedByHold(),
                "the hold itself still reached the top — that is what the hint is built from");
        assertSame(ProtegoRules.Limit.PROFICIENCY, resolution.limit());
    }

    @Test
    void horribilisNeedsDarkArtsStudyEvenFromAMaster() {
        ProtegoRules.TierResolution shallow = ProtegoRules.resolveTier(1000, Proficiency.MASTERED, 0);
        assertSame(ProtegoTier.MAXIMA, shallow.tier());
        assertSame(ProtegoRules.Limit.DARK_ARTS, shallow.limit());

        ProtegoRules.TierResolution deep =
                ProtegoRules.resolveTier(1000, Proficiency.MASTERED, DEEP_DARK_ARTS);
        assertSame(ProtegoTier.HORRIBILIS, deep.tier());
        assertSame(ProtegoRules.Limit.NONE, deep.limit());
        assertFalse(deep.capped());
    }

    /** A novice who holds forever is told to practise Protego, not to go and study curses. */
    @Test
    void theProficiencyWallIsReportedBeforeTheDarkArtsOne() {
        assertSame(ProtegoRules.Limit.PROFICIENCY,
                ProtegoRules.resolveTier(1000, Proficiency.NOVICE, 0).limit());
    }

    @Test
    void aNegativeHoldIsATap() {
        assertSame(ProtegoTier.PROTEGO,
                ProtegoRules.resolveTier(-50, Proficiency.MASTERED, DEEP_DARK_ARTS).tier());
    }

    // ── charge progress (drives the hum and the vignette) ───────────────────────────────────────

    /** The fill between two chimes has to actually move, or the ramp is four silent steps. */
    @Test
    void progressClimbsBetweenThresholds() {
        Proficiency novice = Proficiency.NOVICE;
        int start = ProtegoRules.chargeTicks(ProtegoTier.PROTEGO, novice);
        int next = ProtegoRules.chargeTicks(ProtegoTier.TOTALUM, novice);
        float atStart = ProtegoRules.chargeProgress(start, novice, 0);
        float halfway = ProtegoRules.chargeProgress((start + next) / 2, novice, 0);
        float atNext = ProtegoRules.chargeProgress(next - 1, novice, 0);

        assertEquals(0.0f, atStart, 1.0e-4f);
        assertTrue(halfway > atStart && halfway < 1.0f, "halfway read " + halfway);
        assertTrue(atNext > halfway, "the tick before the chime read " + atNext);
    }

    /** A bar that keeps filling into a wall is a lie: a capped caster reads full. */
    @Test
    void progressIsFullOnceThereIsNothingLeftToReach() {
        assertEquals(1.0f, ProtegoRules.chargeProgress(1000, Proficiency.NOVICE, DEEP_DARK_ARTS), 1.0e-4f);
        assertEquals(1.0f, ProtegoRules.chargeProgress(1000, Proficiency.MASTERED, 0), 1.0e-4f,
                "Horribilis out of reach for want of Dark Arts still reads full at Maxima");
        assertEquals(1.0f, ProtegoRules.chargeProgress(1000, Proficiency.MASTERED, DEEP_DARK_ARTS), 1.0e-4f);
    }

    @Test
    void progressIsBoundedForNonsenseHolds() {
        assertEquals(0.0f, ProtegoRules.chargeProgress(-100, Proficiency.NOVICE, 0), 1.0e-4f);
    }

    /** The whole climb has to fit inside one exchange of a duel, not outlast it. */
    @Test
    void theFullClimbStaysUnderTwoSeconds() {
        assertTrue(ProtegoRules.chargeTicks(ProtegoTier.HORRIBILIS, Proficiency.NOVICE) <= 40,
                "a novice's climb to the top takes "
                        + ProtegoRules.chargeTicks(ProtegoTier.HORRIBILIS, Proficiency.NOVICE) + " ticks");
        assertTrue(ProtegoRules.chargeTicks(ProtegoTier.TOTALUM, Proficiency.NOVICE) >= 6,
                "the first step must be far enough out that a flinch cannot reach it");
    }

    @Test
    void onlyDomeTiersCanBePlanted() {
        assertFalse(ProtegoRules.canPlant(ProtegoTier.PROTEGO, true));
        assertFalse(ProtegoRules.canPlant(ProtegoTier.TOTALUM, true));
        assertTrue(ProtegoRules.canPlant(ProtegoTier.MAXIMA, true));
        assertTrue(ProtegoRules.canPlant(ProtegoTier.HORRIBILIS, true));
        assertFalse(ProtegoRules.canPlant(ProtegoTier.MAXIMA, false),
                "standing up means carrying it, not setting it down");
    }

    // ── integrity ───────────────────────────────────────────────────────────────────────────────

    @Test
    void everyTierHoldsMoreThanTheOneBelow() {
        float previous = 0.0f;
        for (ProtegoTier tier : ProtegoTier.values()) {
            float integrity = ProtegoRules.integrity(tier, 1.0f, 1.0f, false);
            assertTrue(integrity > previous, tier + " must hold more than the tier below it");
            previous = integrity;
        }
    }

    @Test
    void practiceAndPlantingBothDeepenThePool() {
        float novice = ProtegoRules.integrity(ProtegoTier.MAXIMA, 0.33f, 1.0f, false);
        float master = ProtegoRules.integrity(ProtegoTier.MAXIMA, 1.0f, 1.0f, false);
        float planted = ProtegoRules.integrity(ProtegoTier.MAXIMA, 1.0f, 1.0f, true);
        assertTrue(master > novice);
        assertTrue(planted > master);
    }

    /** A wand cannot turn a shield into an invulnerability button, and corruption cannot delete it. */
    @Test
    void castPowerMovesThePoolOnlySoFar() {
        float base = ProtegoRules.integrity(ProtegoTier.TOTALUM, 1.0f, 1.0f, false);
        float absurd = ProtegoRules.integrity(ProtegoTier.TOTALUM, 1.0f, 99.0f, false);
        float crushed = ProtegoRules.integrity(ProtegoTier.TOTALUM, 1.0f, 0.0f, false);
        assertEquals(base * ProtegoRules.MAX_POWER_FACTOR, absurd, 1.0e-3f);
        assertEquals(base * ProtegoRules.MIN_POWER_FACTOR, crushed, 1.0e-3f);
    }

    @Test
    void aBrokenMultiplierCannotProduceABrokenShield() {
        float nan = ProtegoRules.integrity(ProtegoTier.MAXIMA, Float.NaN, Float.NaN, false);
        assertTrue(Float.isFinite(nan) && nan > 0.0f, "got " + nan);
    }

    @Test
    void plantingBuysTimeAsWellAsDepth() {
        assertTrue(ProtegoRules.lifetimeTicks(ProtegoTier.MAXIMA, 1.0f, true)
                > ProtegoRules.lifetimeTicks(ProtegoTier.MAXIMA, 1.0f, false));
        assertTrue(ProtegoRules.lifetimeTicks(ProtegoTier.PROTEGO, 1.0f, false)
                < ProtegoRules.lifetimeTicks(ProtegoTier.MAXIMA, 0.0f, false),
                "the quick parry is the shortest-lived shape at any practice level");
    }

    // ── impact costs ────────────────────────────────────────────────────────────────────────────

    @Test
    void harmlessSpellsStillCostSomethingToTurn() {
        assertEquals(ProtegoRules.MIN_SPELL_COST,
                ProtegoRules.spellImpactCost(ProtegoTier.TOTALUM, 0.0f, false), 1.0e-4f);
    }

    /** The whole point of Horribilis: Dark magic is cheap to swallow and expensive to merely block. */
    @Test
    void darkMagicCostsHorribilisLeastAndEveryoneElseMost() {
        float horribilis = ProtegoRules.spellImpactCost(ProtegoTier.HORRIBILIS, 8.0f, true);
        float maxima = ProtegoRules.spellImpactCost(ProtegoTier.MAXIMA, 8.0f, true);
        float ordinary = ProtegoRules.spellImpactCost(ProtegoTier.MAXIMA, 8.0f, false);
        assertTrue(horribilis < ordinary);
        assertTrue(maxima > ordinary);
    }

    /** The blow side of the same rule: Horribilis soaks Dark damage cheaply, lesser wards dearly. */
    @Test
    void darkBlowsCostHorribilisLeastAndEveryoneElseMost() {
        float horribilis = ProtegoRules.damageImpactCost(ProtegoTier.HORRIBILIS, 10.0f, true);
        float maxima = ProtegoRules.damageImpactCost(ProtegoTier.MAXIMA, 10.0f, true);
        float ordinary = ProtegoRules.damageImpactCost(ProtegoTier.MAXIMA, 10.0f, false);
        assertTrue(horribilis < ordinary, "Horribilis paid " + horribilis + " for what costs " + ordinary);
        assertTrue(maxima > ordinary, "a lesser ward paid " + maxima + " for what costs " + ordinary);
        assertEquals(10.0f, ProtegoRules.damageImpactCost(ProtegoTier.HORRIBILIS, 10.0f, false), 1.0e-4f,
                "an ordinary blow costs its own size at every tier");
    }

    /** An ordinary graze must cost the ward the graze, not the three-point floor spells pay. */
    @Test
    void aGrazeCostsWhatItIsWorth() {
        assertEquals(1.0f, ProtegoRules.damageImpactCost(ProtegoTier.TOTALUM, 1.0f, false), 1.0e-4f);
    }

    /** What a pool can stand in front of is not what it holds, once a discount is in play. */
    @Test
    void aDiscountedPoolStandsInFrontOfMoreThanItHolds() {
        float horribilis = ProtegoRules.absorbableDamage(ProtegoTier.HORRIBILIS, 10.0f, 100.0f, true);
        float maxima = ProtegoRules.absorbableDamage(ProtegoTier.MAXIMA, 10.0f, 100.0f, true);
        assertTrue(horribilis > 10.0f, "10 integrity soaked only " + horribilis + " of Dark damage");
        assertTrue(maxima < 10.0f, "a lesser ward soaked " + maxima + ", which is not a premium");
        assertEquals(10.0f, ProtegoRules.absorbableDamage(ProtegoTier.MAXIMA, 10.0f, 100.0f, false), 1.0e-4f);
    }

    /** A pool never stands in front of more of a blow than the blow contains. */
    @Test
    void absorbingNeverExceedsTheBlow() {
        assertEquals(3.0f, ProtegoRules.absorbableDamage(ProtegoTier.HORRIBILIS, 999.0f, 3.0f, true), 1.0e-4f);
    }

    // ── ordinary projectiles at the wall ────────────────────────────────────────────────────────

    /** A snowball is not a curse: turning one must not cost what the cheapest spell costs. */
    @Test
    void aThrownNuisanceCostsLessThanASpell() {
        float nuisance = ProtegoRules.projectileImpactCost(ProtegoTier.TOTALUM, 0.0f, false);
        assertEquals(ProtegoRules.MIN_PROJECTILE_COST, nuisance, 1.0e-4f);
        assertTrue(nuisance < ProtegoRules.spellImpactCost(ProtegoTier.TOTALUM, 0.0f, false),
                "a snowball cost " + nuisance + ", the same as a spell");
    }

    /** A hard-hitting arrow costs the ward what it would have done to the body behind it. */
    @Test
    void aProjectileCostsWhatItWouldHaveDone() {
        assertEquals(9.0f, ProtegoRules.projectileImpactCost(ProtegoTier.MAXIMA, 9.0f, false), 1.0e-4f);
    }

    /** A wither skull is Dark magic however it is delivered, so the tier split still applies. */
    @Test
    void darkProjectilesFollowTheSameSplitAsDarkSpells() {
        float horribilis = ProtegoRules.projectileImpactCost(ProtegoTier.HORRIBILIS, 8.0f, true);
        float maxima = ProtegoRules.projectileImpactCost(ProtegoTier.MAXIMA, 8.0f, true);
        float ordinary = ProtegoRules.projectileImpactCost(ProtegoTier.MAXIMA, 8.0f, false);
        assertTrue(horribilis < ordinary, "Horribilis paid " + horribilis + " against " + ordinary);
        assertTrue(maxima > ordinary, "a lesser ward paid " + maxima + " against " + ordinary);
    }

    @Test
    void theLowWarningFiresOnceOnTheWayDown() {
        float max = 40.0f;
        assertTrue(ProtegoRules.crossedLowIntegrity(20.0f, 10.0f, max));
        assertFalse(ProtegoRules.crossedLowIntegrity(10.0f, 6.0f, max), "already below the line");
        assertFalse(ProtegoRules.crossedLowIntegrity(20.0f, 0.0f, max), "that is a breach, not a warning");
    }

    // ── geometry ────────────────────────────────────────────────────────────────────────────────

    @Test
    void theDiscCoversWhatTheCasterFacesAndNotTheirBack() {
        assertTrue(ProtegoRules.isFrontal(0, 1, 0, 5), "straight ahead");
        assertTrue(ProtegoRules.isFrontal(0, 1, 4, 0), "from the side still clips the edge");
        assertFalse(ProtegoRules.isFrontal(0, 1, 0, -5), "from directly behind");
    }

    @Test
    void anAttackWithNoDirectionIsNotPunished() {
        assertTrue(ProtegoRules.isFrontal(0, 0, 0, 0));
    }

    @Test
    void aBoltCrossingTheWardIsCaughtAtItsSurface() {
        // Straight through the centre, from 10 blocks out, in one 20-block step.
        double t = ProtegoRules.sphereEntry(-10, 0, 0, 10, 0, 0, 0, 0, 0, 5.0);
        assertTrue(t > 0.0 && t < 1.0, "expected an entry fraction, got " + t);
        double entryX = -10 + t * 20;
        assertEquals(-5.0, entryX, 1.0e-6, "entry must be on the sphere, not somewhere inside it");
    }

    /** The tunnelling case: a bolt that steps clean across a ward must still be caught. */
    @Test
    void aBoltFasterThanTheWardIsWideDoesNotTunnelThrough() {
        assertTrue(ProtegoRules.sphereEntry(-30, 0, 0, 30, 0, 0, 0, 0, 0, 1.6) > 0.0);
    }

    @Test
    void aBoltCastFromInsideIsNotStoppedByTheWall() {
        assertEquals(-1.0, ProtegoRules.sphereEntry(0, 0, 0, 20, 0, 0, 0, 0, 0, 5.0));
    }

    @Test
    void aBoltGoingPastOrAwayIsNotTouched() {
        assertEquals(-1.0, ProtegoRules.sphereEntry(-10, 20, 0, 10, 20, 0, 0, 0, 0, 5.0), "passes over");
        assertEquals(-1.0, ProtegoRules.sphereEntry(-10, 0, 0, -30, 0, 0, 0, 0, 0, 5.0), "flying away");
        assertEquals(-1.0, ProtegoRules.sphereEntry(-10, 0, 0, -9, 0, 0, 0, 0, 0, 5.0), "not there yet");
    }

    @Test
    void aStationaryBoltIsNotAnEntry() {
        assertEquals(-1.0, ProtegoRules.sphereEntry(-10, 0, 0, -10, 0, 0, 0, 0, 0, 5.0));
    }

    // ── the ladder as a whole ───────────────────────────────────────────────────────────────────

    /** Each step up must cost more to recover from, or there is no reason to ever cast a lesser one. */
    @Test
    void theStrongerShapesCostMore() {
        ProtegoTier[] tiers = ProtegoTier.values();
        for (int i = 1; i < tiers.length; i++) {
            assertTrue(tiers[i].cooldownFactor() > tiers[i - 1].cooldownFactor(),
                    tiers[i] + " should take longer to recover than " + tiers[i - 1]);
            assertTrue(tiers[i].breachLockoutTicks() > tiers[i - 1].breachLockoutTicks(),
                    tiers[i] + " should lock the caster out longer when broken");
        }
        assertTrue(ProtegoTier.PROTEGO.cooldownFactor() < 1.0f,
                "the quick parry must come back faster than the spell's own cooldown");
    }

    @Test
    void onlyTheTopTierSwallowsDarkMagicAndOnlyItBitesBack() {
        for (ProtegoTier tier : ProtegoTier.values()) {
            assertEquals(tier == ProtegoTier.HORRIBILIS, tier.absorbsDark(), tier.name());
            assertEquals(tier == ProtegoTier.HORRIBILIS, tier.backlashOnBreach(), tier.name());
        }
    }

    @Test
    void tierIndexLookupIsClamped() {
        assertSame(ProtegoTier.PROTEGO, ProtegoTier.byIndex(-3));
        assertSame(ProtegoTier.HORRIBILIS, ProtegoTier.byIndex(99));
    }
}
