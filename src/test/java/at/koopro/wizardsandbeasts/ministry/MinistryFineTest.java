package at.koopro.wizardsandbeasts.ministry;

import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.ministry.data.MinistryRank;
import at.koopro.wizardsandbeasts.ministry.data.PlayerMinistryRecord;
import at.koopro.wizardsandbeasts.ministry.law.FineSchedule;
import at.koopro.wizardsandbeasts.ministry.law.MagicalOffence;
import at.koopro.wizardsandbeasts.ministry.law.WantedLevel;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The money half of Ministry enforcement.
 *
 * <p>Everything asserted here is reachable without a server: {@link FineSchedule} is deliberately free of
 * {@code Config}, of the vault and of Minecraft, so the numbers a player experiences can be pinned rather
 * than described. What the tests cannot see — the vault debit itself — is one call to
 * {@code PlayerVaultData.withdrawSmartKnuts}, which has its own coverage.
 */
class MinistryFineTest {

    private static final Gson GSON = new Gson();

    // ── the tariff ──

    /**
     * The load-bearing invariant of the whole design. An offence is answered with time <em>or</em> with
     * money, never both and never neither: an arrestable offence carries no fine because Azkaban is the
     * penalty, and a paperwork offence must carry one or committing it costs nothing at all.
     */
    @Test
    void everyOffenceIsAnsweredWithExactlyOneOfTimeOrMoney() {
        for (MagicalOffence offence : MagicalOffence.values()) {
            assertEquals(!offence.arrestable(), offence.fineable(),
                    offence.getSerializedName() + " must be fineable exactly when it is not arrestable");
            if (offence.arrestable()) {
                assertEquals(0, offence.fineKnuts(),
                        offence.getSerializedName() + " is settled in Azkaban and must carry no fine");
            } else {
                assertTrue(offence.fineKnuts() > 0,
                        offence.getSerializedName() + " has no penalty at all");
            }
        }
    }

    @Test
    void concealingACapabilityCostsMoreThanSkippingPaperwork() {
        assertTrue(MagicalOffence.UNREGISTERED_ANIMAGUS.fineKnuts()
                        > MagicalOffence.UNLICENSED_APPARITION.fineKnuts(),
                "an unregistered Animagus is hiding what they are; an unlicensed Apparator only skipped a form");
    }

    @Test
    void finesAreWholeGalleonAmountsAtTheShippedScale() {
        assertEquals(2 * CurrencyHelper.KNUTS_PER_GALLEON, MagicalOffence.UNLICENSED_APPARITION.fineKnuts());
        assertEquals(10 * CurrencyHelper.KNUTS_PER_GALLEON, MagicalOffence.UNREGISTERED_ANIMAGUS.fineKnuts());
    }

    // ── assessment ──

    @Test
    void aFirstOffenceIsChargedAtTheTariff() {
        int tariff = MagicalOffence.UNLICENSED_APPARITION.fineKnuts();
        assertEquals(tariff, FineSchedule.assess(tariff, 1.0f, 100));
    }

    @Test
    void priorsMakeTheSameOffenceCostMore() {
        int tariff = MagicalOffence.UNLICENSED_APPARITION.fineKnuts();
        long first = FineSchedule.assess(tariff, 1.0f, 100);
        long fourth = FineSchedule.assess(tariff, 1.45f, 100);
        assertTrue(fourth > first, "a fourth offence must cost more than the first");
        assertEquals(Math.round(tariff * 1.45), fourth);
    }

    /** The record caps priors at double, so the fine cannot run away either. */
    @Test
    void theRepeatMultiplierIsBoundedByTheRecord() {
        PlayerMinistryRecord career = PlayerMinistryRecord.DEFAULT;
        for (int i = 0; i < 50; i++) {
            career = career.withOffence(MagicalOffence.UNLICENSED_APPARITION, 0f);
        }
        int tariff = MagicalOffence.UNLICENSED_APPARITION.fineKnuts();
        assertEquals(2L * tariff,
                FineSchedule.assess(tariff, career.repeatMultiplier(MagicalOffence.UNLICENSED_APPARITION), 100));
    }

    @Test
    void aMultiplierBelowOneNeverDiscountsAFine() {
        int tariff = MagicalOffence.UNLICENSED_APPARITION.fineKnuts();
        assertEquals(tariff, FineSchedule.assess(tariff, 0.1f, 100),
                "priors may only ever raise a fine");
    }

    @Test
    void serversCanScaleOrDisableFinesWithoutTouchingTheRecord() {
        int tariff = MagicalOffence.UNREGISTERED_ANIMAGUS.fineKnuts();
        assertEquals(2L * tariff, FineSchedule.assess(tariff, 1.0f, 200));
        assertEquals(tariff / 2, FineSchedule.assess(tariff, 1.0f, 50));
        assertEquals(0L, FineSchedule.assess(tariff, 1.0f, 0), "scale 0 turns fines off");
    }

    @Test
    void anOffenceWithATariffNeverRoundsDownToFree() {
        assertEquals(1L, FineSchedule.assess(1, 1.0f, 1),
                "a 1-Knut fine at 1% must still cost something, or the offence is filed for free");
    }

    @Test
    void anOffenceWithoutATariffIsNeverBilled() {
        assertEquals(0L, FineSchedule.assess(0, 2.0f, 100));
        assertEquals(0L, FineSchedule.assess(MagicalOffence.CRUCIO.fineKnuts(), 2.0f, 100));
    }

    // ── collection ──

    @Test
    void gringottsTakesWhatIsThereAndKeepsTheRest() {
        assertEquals(300L, FineSchedule.collectable(986L, 300L), "partial payment is allowed");
        assertEquals(986L, FineSchedule.collectable(986L, 5000L), "never takes more than is owed");
        assertEquals(0L, FineSchedule.collectable(986L, 0L), "an empty vault pays nothing");
        assertEquals(0L, FineSchedule.collectable(0L, 5000L), "no debt, no collection");
    }

    // ── debt against heat ──

    @Test
    void heatCannotCoolWhileAFineStands() {
        assertTrue(FineSchedule.mayCool(30f, false, false, false), "a clean debtor-free wizard cools");
        assertFalse(FineSchedule.mayCool(30f, true, false, false), "an unpaid fine freezes the decay");
        assertFalse(FineSchedule.mayCool(30f, false, true, false), "a fugitive does not cool");
        assertFalse(FineSchedule.mayCool(30f, false, false, true), "a prisoner does not cool");
        assertFalse(FineSchedule.mayCool(0f, false, false, false), "nothing to cool at zero");
    }

    @Test
    void ignoringABillSlowlyRaisesHeat() {
        assertTrue(FineSchedule.debtHeat(0f, 20) > 0f);
        assertEquals(FineSchedule.DEBT_HEAT_PER_SECOND, FineSchedule.debtHeat(0f, 20), 1e-5,
                "one second of debt is one second of heat");
        assertEquals(0f, FineSchedule.debtHeat(0f, 0), 1e-5);
    }

    /**
     * The rule that keeps the cheapest offence in the mod from out-ranking the Killing Curse. Debt heat
     * stops at the point Aurors would be dispatched and never climbs past it, however long a bill is left.
     */
    @Test
    void debtHeatStopsAtTheWantedThresholdAndNeverPassesIt() {
        assertEquals(WantedLevel.WANTED.threshold(), FineSchedule.DEBT_HEAT_CEILING, 1e-5);

        float notoriety = 0f;
        for (int i = 0; i < 200_000; i++) {
            notoriety += FineSchedule.debtHeat(notoriety, 20);
        }
        assertEquals(FineSchedule.DEBT_HEAT_CEILING, notoriety, 1e-3,
                "debt heat converges on the ceiling");
        assertEquals(WantedLevel.WANTED, WantedLevel.forNotoriety(notoriety));
        assertEquals(0f, FineSchedule.debtHeat(FineSchedule.DEBT_HEAT_CEILING, 20), 1e-5);
        assertEquals(0f, FineSchedule.debtHeat(95f, 20), 1e-5,
                "a wizard already past the ceiling gains nothing further from the debt");
    }

    // ── the debt on the record ──

    @Test
    void aDebtNeverGoesNegativeWhenOverpaid() {
        PlayerMinistryRecord owing = PlayerMinistryRecord.DEFAULT.withOutstandingFine(500L);
        assertEquals(0L, owing.withFineAdjusted(-9999L).outstandingFineKnuts());
        assertFalse(owing.withFineAdjusted(-9999L).owesFine());
    }

    @Test
    void fineAndFileMoveIndependently() {
        PlayerMinistryRecord record = PlayerMinistryRecord.DEFAULT
                .withOffence(MagicalOffence.UNLICENSED_APPARITION, 2f)
                .withFineAdjusted(986L);

        assertEquals(1, record.offenceCount(MagicalOffence.UNLICENSED_APPARITION));
        assertEquals(986L, record.outstandingFineKnuts());

        PlayerMinistryRecord paid = record.withFineAdjusted(-986L);
        assertEquals(1, paid.offenceCount(MagicalOffence.UNLICENSED_APPARITION),
                "paying a fine does not clear the offence from the file");
        assertFalse(paid.owesFine());
    }

    @Test
    void aPardonSettlesTheDebtSoTheWizardCanCoolAgain() {
        PlayerMinistryRecord pardoned = PlayerMinistryRecord.DEFAULT
                .withOffence(MagicalOffence.UNREGISTERED_ANIMAGUS, 6f)
                .withFineAdjusted(4930L)
                .pardoned();

        assertFalse(pardoned.owesFine(),
                "a pardon that left the bill standing would freeze notoriety forever");
        assertEquals(1, pardoned.offenceCount(MagicalOffence.UNREGISTERED_ANIMAGUS),
                "the file survives the pardon");
    }

    // ── persistence ──

    @Test
    void aSaveWrittenBeforeFinesExistedLoadsWithNoDebt() {
        // Exactly the shape the record's codec wrote before outstandingFineKnuts existed.
        String legacy = """
                {"notoriety": 33.5, "offences": {"crucio": 2}, "sentenceTicks": 400,
                 "fugitive": true, "rank": "auror"}
                """;
        PlayerMinistryRecord restored = PlayerMinistryRecord.CODEC
                .parse(JsonOps.INSTANCE, GSON.fromJson(legacy, JsonElement.class))
                .getOrThrow(msg -> new AssertionError("legacy record failed to parse: " + msg));

        assertEquals(0L, restored.outstandingFineKnuts());
        assertFalse(restored.owesFine());
        // The rest of the file must survive intact — a parse failure here would reset the whole record.
        assertEquals(33.5f, restored.notoriety(), 1e-4);
        assertEquals(2, restored.offenceCount(MagicalOffence.CRUCIO));
        assertEquals(400, restored.sentenceTicks());
        assertTrue(restored.fugitive());
        assertEquals(MinistryRank.AUROR, restored.rank());
    }

    @Test
    void theDebtSurvivesARoundTrip() {
        PlayerMinistryRecord original = PlayerMinistryRecord.DEFAULT
                .withOffence(MagicalOffence.UNLICENSED_APPARITION, 2f)
                .withFineAdjusted(1479L);

        JsonElement encoded = PlayerMinistryRecord.CODEC.encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow(msg -> new AssertionError("encode failed: " + msg));
        PlayerMinistryRecord restored = PlayerMinistryRecord.CODEC
                .parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(msg -> new AssertionError("parse failed: " + msg));

        assertEquals(1479L, restored.outstandingFineKnuts());
        assertEquals(original, restored);
    }
}
