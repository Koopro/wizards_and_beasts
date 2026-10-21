package at.koopro.wizardsandbeasts.ministry.trace;

import at.koopro.wizardsandbeasts.ministry.law.MagicalOffence;
import at.koopro.wizardsandbeasts.ministry.licence.LicenseType;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The wizard's side of a hearing: proof, pleas, character, surrender, licences, appeals and cold cases. */
class HearingChoicesTest {

    private static Wizengamot.Charge charge(LegalClass legalClass, Exposure exposure, int strength, boolean licensed) {
        Optional<MagicalOffence> offence = legalClass == LegalClass.UNFORGIVABLE
                ? Optional.of(MagicalOffence.CRUCIO) : Optional.empty();
        return new Wizengamot.Charge(legalClass, exposure, false, false, offence, false, strength, licensed);
    }

    private static Wizengamot.Hearing hearing(List<Wizengamot.Charge> charges, Plea plea, boolean goodStanding,
                                              boolean appeared, boolean surrendered) {
        return new Wizengamot.Hearing(charges, 0, 0, appeared, plea, goodStanding, 0, surrendered);
    }

    // ── standard of proof ──

    @Test
    void aContestedChargeTheEvidenceDoesNotCarryIsDismissed() {
        Wizengamot.Ruling ruling = Wizengamot.rule(hearing(
                List.of(charge(LegalClass.DARK, Exposure.LOW, EvidenceRules.standardFor(LegalClass.DARK) - 1, false)),
                Plea.CONTEST, false, true, false));
        assertEquals(Verdict.DISMISSED, ruling.verdict());
        assertTrue(ruling.reasons().contains("insufficient_evidence"));
    }

    @Test
    void theWandItselfIsConclusiveWhateverTheBar() {
        Wizengamot.Charge found = new Wizengamot.Charge(LegalClass.DARK, Exposure.NONE, false, false,
                Optional.empty(), true, 0, false);
        Wizengamot.Ruling ruling = Wizengamot.rule(hearing(List.of(found), Plea.CONTEST, false, true, false));
        assertEquals(Verdict.WAND_CONFISCATION, ruling.verdict());
    }

    @Test
    void aGuiltyPleaIsEvidenceAndEarnsLeniency() {
        Wizengamot.Charge thin = charge(LegalClass.DARK, Exposure.LOW, 10, false);
        Wizengamot.Ruling contested = Wizengamot.rule(hearing(List.of(thin), Plea.CONTEST, false, true, false));
        Wizengamot.Ruling pleaded = Wizengamot.rule(hearing(List.of(thin), Plea.GUILTY, false, true, false));
        assertEquals(Verdict.DISMISSED, contested.verdict(), "thin evidence, contested, is dismissed");
        assertEquals(Verdict.WAND_CONFISCATION, pleaded.verdict(), "a plea convicts on its own");
        assertTrue(pleaded.reasons().contains("pleaded_guilty"));
        assertEquals(Wizengamot.CONFISCATION_MEDIUM_TICKS / 2, pleaded.confiscationTicks());
    }

    @Test
    void aPleaTakesAQuarterOffAFine() {
        Wizengamot.Charge severe = charge(LegalClass.UNRESTRICTED, Exposure.SEVERE, 100, false);
        Wizengamot.Ruling pleaded = Wizengamot.rule(hearing(List.of(severe), Plea.GUILTY, false, true, false));
        assertEquals(Verdict.FINE, pleaded.verdict());
        assertEquals(100 - Wizengamot.FINE_DISCOUNT_PERCENT, pleaded.finePercent());
    }

    @Test
    void neitherPleaNorCharacterBuysDownAnUnforgivable() {
        Wizengamot.Ruling ruling = Wizengamot.rule(hearing(
                List.of(charge(LegalClass.UNFORGIVABLE, Exposure.NONE, 100, false)), Plea.GUILTY, true, true, false));
        assertEquals(Verdict.AZKABAN_REFERRAL, ruling.verdict());
        assertEquals(Wizengamot.CONFISCATION_LONG_TICKS, ruling.confiscationTicks());
        assertFalse(ruling.reasons().contains("pleaded_guilty"));
        assertFalse(ruling.reasons().contains("good_standing"));
    }

    @Test
    void goodStandingIsACharacterReferenceOnlyForThoseWhoAnswered() {
        Wizengamot.Charge severe = charge(LegalClass.UNRESTRICTED, Exposure.SEVERE, 100, false);
        Wizengamot.Ruling answered = Wizengamot.rule(hearing(List.of(severe), Plea.CONTEST, true, true, false));
        Wizengamot.Ruling caught = Wizengamot.rule(hearing(List.of(severe), Plea.CONTEST, true, false, false));
        assertTrue(answered.reasons().contains("good_standing"));
        assertEquals(100 - Wizengamot.FINE_DISCOUNT_PERCENT, answered.finePercent());
        assertFalse(caught.reasons().contains("good_standing"));
    }

    @Test
    void surrenderingIsBetterThanBeingCaught() {
        Wizengamot.Charge minor = charge(LegalClass.UNRESTRICTED, Exposure.MODERATE, 100, false);
        Wizengamot.Ruling caught = Wizengamot.rule(hearing(List.of(minor), Plea.CONTEST, false, false, false));
        Wizengamot.Ruling surrendered = Wizengamot.rule(hearing(List.of(minor), Plea.CONTEST, false, false, true));
        assertEquals(Verdict.WAND_CONFISCATION, caught.verdict());
        assertTrue(caught.reasons().contains("failed_to_appear"));
        assertEquals(Verdict.WARNING, surrendered.verdict());
        assertTrue(surrendered.reasons().contains("surrendered"));
        assertTrue(surrendered.severity() < caught.severity());
    }

    // ── licences ──

    @Test
    void aRestrictedMagicLicenceCoversDarkMagicNobodySaw() {
        Wizengamot.Ruling unseen = Wizengamot.rule(hearing(List.of(charge(LegalClass.DARK, Exposure.LOW, 100, true)),
                Plea.CONTEST, false, true, false));
        assertEquals(Verdict.DISMISSED, unseen.verdict());
        assertTrue(unseen.reasons().contains("licensed_practice"));

        Wizengamot.Ruling seen = Wizengamot.rule(hearing(List.of(charge(LegalClass.DARK, Exposure.MODERATE, 100, true)),
                Plea.CONTEST, false, true, false));
        assertTrue(seen.verdict() != Verdict.DISMISSED, "a licence is no licence to break the Statute");

        Wizengamot.Ruling curse = Wizengamot.rule(hearing(List.of(charge(LegalClass.UNFORGIVABLE, Exposure.NONE, 100,
                true)), Plea.CONTEST, false, true, false));
        assertEquals(Verdict.AZKABAN_REFERRAL, curse.verdict(), "nothing licenses an Unforgivable");
    }

    @Test
    void aDarkConvictionSuspendsTheLicencesThatCoverIt() {
        Wizengamot.Ruling ruling = Wizengamot.rule(hearing(List.of(charge(LegalClass.DARK, Exposure.LOW, 100, false)),
                Plea.CONTEST, false, true, false));
        assertTrue(ruling.suspensions().contains(LicenseType.RESTRICTED_MAGIC));
        assertTrue(ruling.suspensions().contains(LicenseType.APPARITION), "no Apparition without a wand");
        assertTrue(ruling.suspensionTicks() >= Wizengamot.SUSPENSION_MIN_TICKS);

        Wizengamot.Ruling warning = Wizengamot.rule(hearing(List.of(charge(LegalClass.UNRESTRICTED, Exposure.MODERATE,
                100, false)), Plea.CONTEST, false, true, false));
        assertTrue(warning.suspensions().isEmpty());
        assertEquals(0L, warning.suspensionTicks());
    }

    // ── appeals ──

    private static ClosedCase closed(Wizengamot.Hearing hearing, long closedAt, long appealBy) {
        return new ClosedCase(1L, closedAt, hearing, Wizengamot.rule(hearing), 5.0f, 100L, appealBy,
                ClosedCase.Appeal.NONE);
    }

    @Test
    void anAppealIsHeardOnceWithinTheWindowAndNotAfterAPlea() {
        Wizengamot.Charge minor = charge(LegalClass.UNRESTRICTED, Exposure.SEVERE, 100, false);
        ClosedCase contested = closed(hearing(List.of(minor), Plea.CONTEST, false, true, false), 0L, 1000L);
        assertEquals(AppealRules.Eligibility.ALLOWED, AppealRules.eligibility(contested, 1000L));
        assertEquals(AppealRules.Eligibility.WINDOW_CLOSED, AppealRules.eligibility(contested, 1001L));
        assertEquals(AppealRules.Eligibility.ALREADY_APPEALED, AppealRules.eligibility(
                contested.withAppeal(ClosedCase.Appeal.UPHELD, contested.ruling()), 10L));

        ClosedCase pleaded = closed(hearing(List.of(minor), Plea.GUILTY, false, true, false), 0L, 1000L);
        assertEquals(AppealRules.Eligibility.PLEADED_GUILTY, AppealRules.eligibility(pleaded, 10L));

        ClosedCase cleared = closed(hearing(List.of(charge(LegalClass.DARK, Exposure.LOW, 0, false)), Plea.CONTEST,
                false, true, false), 0L, 1000L);
        assertEquals(AppealRules.Eligibility.NOTHING_TO_APPEAL, AppealRules.eligibility(cleared, 10L));
    }

    @Test
    void anAppealOverturnsAConvictionThatOnlyJustMetTheBar() {
        int bar = EvidenceRules.standardFor(LegalClass.DARK);
        ClosedCase close = closed(hearing(List.of(charge(LegalClass.DARK, Exposure.LOW, bar, false)), Plea.CONTEST,
                false, true, false), 0L, 1000L);
        assertEquals(Verdict.WAND_CONFISCATION, close.ruling().verdict());
        AppealRules.Review review = AppealRules.review(close, false);
        assertEquals(ClosedCase.Appeal.OVERTURNED, review.outcome());
        assertEquals(Verdict.DISMISSED, review.standing().verdict());
        assertEquals(AppealRules.Eligibility.ALREADY_APPEALED,
                AppealRules.eligibility(close.withAppeal(review.outcome(), review.standing()), 10L),
                "an overturned ruling stands as a dismissal, but it was appealed");
    }

    @Test
    void anAppealCanReduceForCharacterAndNeverMakesItWorse() {
        Wizengamot.Charge severe = charge(LegalClass.UNRESTRICTED, Exposure.SEVERE, 150, false);
        ClosedCase fined = closed(hearing(List.of(severe), Plea.CONTEST, false, true, false), 0L, 1000L);
        AppealRules.Review reduced = AppealRules.review(fined, true);
        assertEquals(ClosedCase.Appeal.REDUCED, reduced.outcome());
        assertTrue(reduced.standing().finePercent() < fined.ruling().finePercent());

        AppealRules.Review upheld = AppealRules.review(fined, false);
        assertEquals(ClosedCase.Appeal.UPHELD, upheld.outcome());
        assertEquals(fined.ruling(), upheld.standing());
    }

    @Test
    void theAppealWindowIsCountedInDays() {
        assertEquals(10L + 3 * 24000L, AppealRules.appealBy(10L, 3));
        assertEquals(0L, AppealRules.appealBy(10L, 0));
    }

    // ── cold cases ──

    @Test
    void aCaseGoesColdAfterEnoughFruitlessSearchesAndWarmsOnTheNextReport() {
        Identifier overworld = Identifier.withDefaultNamespace("overworld");
        OpenCase open = new OpenCase(CaseStage.AURORS_ASSIGNED, List.of(1L), 0L, 0L, overworld, BlockPos.ZERO, 100L, 0,
                true);
        long now = 100L;
        for (int i = 0; i < CaseRules.SEARCHES_BEFORE_COLD; i++) {
            assertFalse(open.cold());
            open = CaseRules.afterFruitlessSearch(open, now);
            now = open.nextSearch() == 0L ? now : open.nextSearch();
        }
        assertTrue(open.cold());
        assertEquals(CaseRules.Step.NOTHING, CaseRules.advance(open, Long.MAX_VALUE / 2).step(),
                "nobody searches a cold case");

        OpenCase warmed = CaseRules.fold(Optional.of(open), MinistryResponse.RECORD, 2L, overworld,
                new BlockPos(5, 64, 5), now).orElseThrow();
        assertFalse(warmed.cold());
        assertEquals(0, warmed.searches());
        assertEquals(List.of(1L, 2L), warmed.incidents());
        assertEquals(CaseRules.Step.SEARCH_DUE, CaseRules.advance(warmed, warmed.nextSearch()).step());
    }

    // ── saves ──

    @Test
    void closedCasesSurviveASaveAndOldDossiersStillLoad() {
        Wizengamot.Hearing put = hearing(List.of(charge(LegalClass.DARK, Exposure.LOW, 70, true)), Plea.CONTEST,
                true, false, true);
        ClosedCase closedCase = closed(put, 5L, 99L);
        Dossier dossier = Dossier.EMPTY.closedWith(closedCase);
        Dossier back = Dossier.CODEC.parse(JsonOps.INSTANCE,
                Dossier.CODEC.encodeStart(JsonOps.INSTANCE, dossier).getOrThrow()).getOrThrow();
        assertEquals(dossier, back);

        Dossier old = Dossier.CODEC.parse(JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"last_verdict\":\"warning\"}")).getOrThrow();
        assertEquals(Optional.of(Verdict.WARNING), old.lastVerdict());
        assertTrue(old.lastCase().isEmpty());
    }
}
