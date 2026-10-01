package at.koopro.wizardsandbeasts.spell.proficiency;

import at.koopro.wizardsandbeasts.spell.core.Proficiency;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Practice rules: spread over days, and one measure of skill shared by the tier and the power curve. */
class SpellPracticeTest {

    @Test
    void aDayHoldsALimitedAmountOfPractice() {
        assertTrue(SpellPractice.counts(0));
        assertTrue(SpellPractice.counts(SpellPractice.DAILY_PRACTICE - 1));
        assertFalse(SpellPractice.counts(SpellPractice.DAILY_PRACTICE));
        assertEquals(12, SpellPractice.practicedToday(5, 5, 12));
        assertEquals(0, SpellPractice.practicedToday(6, 5, 12), "a new day starts fresh");
        assertEquals(SpellPractice.day(SpellPractice.DAY_TICKS - 1) + 1, SpellPractice.day(SpellPractice.DAY_TICKS));
    }

    @Test
    void masteringASpellTakesDaysOfPractice() {
        int days = (int) Math.ceil(Proficiency.MASTERED.getCastsRequired() / (double) SpellPractice.DAILY_PRACTICE);
        assertTrue(days >= 5, "mastery in under five in-game days would be spam again: " + days);
    }

    @Test
    void theTierAndThePowerCurveAgree() {
        assertEquals(0.0f, SpellPractice.curve(0), 1e-6);
        assertEquals(0.5f, SpellPractice.curve(Proficiency.PROFICIENT.getCastsRequired()), 1e-6);
        assertEquals(1.0f, SpellPractice.curve(Proficiency.MASTERED.getCastsRequired()), 1e-6);
        // Proficient casts at least at baseline; Mastered at the full ceiling.
        float proficientDamage = ProficiencyScaler.computeProfile(
                SpellPractice.curve(Proficiency.PROFICIENT.getCastsRequired())).damageMult();
        float masteredDamage = ProficiencyScaler.computeProfile(
                SpellPractice.curve(Proficiency.MASTERED.getCastsRequired())).damageMult();
        assertTrue(proficientDamage >= 1.0f, "a proficient spell cast weaker than an untrained one: " + proficientDamage);
        assertEquals(1.5f, masteredDamage, 1e-4);
    }

    @Test
    void anOldSaveIsLiftedToWhatItsPracticeEarned() {
        // Before the fix a mastered spell (200 hits) had stored ~0.4.
        assertEquals(1.0f, SpellPractice.effective(0.4f, 200), 1e-6);
        assertEquals(0.9f, SpellPractice.effective(0.9f, 10), 1e-6, "never lowered");
    }

    @Test
    void studyMakesPracticePayOffSooner() {
        float plain = SpellPractice.afterPractice(0.0f, 100, 1.0f);
        float studied = SpellPractice.afterPractice(0.0f, 100, 1.5f);
        assertTrue(studied > plain);
        assertEquals(1.0f, SpellPractice.afterPractice(0.0f, 140, 1.5f), 1e-6);
        assertEquals(plain, SpellPractice.afterPractice(0.0f, 100, 0.5f), 1e-6, "a low rate never penalises");
        assertEquals(0.8f, SpellPractice.afterPractice(0.8f, 10, 1.0f), 1e-6, "never lowered");
    }
}
