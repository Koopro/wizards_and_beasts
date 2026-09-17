package at.koopro.wizardsandbeasts.ministry.trace;

import at.koopro.wizardsandbeasts.ministry.law.MagicalOffence;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A cast is not a detection. These pin who comes to hear of magic, and how bad it is, from the scene alone.
 */
class TraceRulesTest {

    private static final SpellLaw LUMOS = new SpellLaw(LegalClass.UNRESTRICTED, 1, Optional.empty());
    private static final SpellLaw CONFUNDO = new SpellLaw(LegalClass.RESTRICTED, 0, Optional.empty());
    private static final SpellLaw STUPEFY = new SpellLaw(LegalClass.RESTRICTED, 2, Optional.empty());
    private static final SpellLaw BOMBARDA = new SpellLaw(LegalClass.RESTRICTED, 3, Optional.empty());
    private static final SpellLaw SECTUMSEMPRA = new SpellLaw(LegalClass.DARK, 2, Optional.empty());
    private static final SpellLaw CRUCIO = new SpellLaw(LegalClass.UNFORGIVABLE, 2, Optional.of(MagicalOffence.CRUCIO));

    private static CastScene scene(boolean underage, boolean hogwarts, int muggles, int adults, int officials,
                                   boolean creature) {
        return new CastScene(underage, false, hogwarts, muggles, adults, officials, creature, List.of());
    }

    private static List<TraceRules.PlannedReport> reports(CastScene scene, SpellLaw law) {
        return TraceRules.reports(scene, law, TraceRules.exposure(scene, law));
    }

    // ── nobody saw ──

    @Test
    void anAdultCastingAloneInTheWildernessIsKnownToNobody() {
        assertTrue(reports(CastScene.alone(), STUPEFY).isEmpty());
        assertTrue(reports(CastScene.alone(), CRUCIO).isEmpty(),
                "even an Unforgivable is only known if someone reports it — or a wand examination finds it");
    }

    @Test
    void lumosInARemoteAreaIsLowExposure() {
        assertEquals(Exposure.LOW, TraceRules.exposure(CastScene.alone(), LUMOS));
        assertFalse(Exposure.LOW.breachesSecrecy());
    }

    // ── Muggles ──

    @Test
    void aVisibleEffectNearAMuggleIsModerateAndReportedWithoutAName() {
        CastScene scene = scene(false, false, 1, 0, 0, false);
        assertEquals(Exposure.MODERATE, TraceRules.exposure(scene, LUMOS));
        List<TraceRules.PlannedReport> reports = reports(scene, LUMOS);
        assertEquals(1, reports.size());
        assertEquals(ReportChannel.MUGGLE_REPORT, reports.getFirst().channel());
        assertFalse(reports.getFirst().identifiesCaster(), "a Muggle saw a light, not a name");
        assertTrue(reports.getFirst().delayTicks() > TraceRules.TRACE_DELAY_TICKS, "word travels second-hand");
    }

    @Test
    void magicAMuggleCannotSeeBreachesNothing() {
        assertEquals(Exposure.NONE, TraceRules.exposure(scene(false, false, 3, 0, 0, false), CONFUNDO));
    }

    @Test
    void aLargeEventBeforeSeveralMugglesIsSevere() {
        assertEquals(Exposure.SEVERE, TraceRules.exposure(scene(false, false, 2, 0, 0, false), BOMBARDA));
        assertEquals(Exposure.SEVERE, TraceRules.exposure(scene(false, false, TraceRules.CROWD, 0, 0, false), LUMOS));
        assertEquals(Exposure.MODERATE, TraceRules.exposure(scene(false, false, 1, 0, 0, false), BOMBARDA),
                "one Muggle is a sighting, not a crowd");
    }

    @Test
    void darkMagicOrADangerousCreatureBeforeAnyMuggleIsExtreme() {
        assertEquals(Exposure.EXTREME, TraceRules.exposure(scene(false, false, 1, 0, 0, false), SECTUMSEMPRA));
        assertEquals(Exposure.EXTREME, TraceRules.exposure(scene(false, false, 1, 0, 0, true), LUMOS));
    }

    // ── underage ──

    @Test
    void theTraceNamesAChildCastingWithNoAdultWizardAbout() {
        List<TraceRules.PlannedReport> reports = reports(scene(true, false, 0, 0, 0, false), LUMOS);
        assertEquals(1, reports.size());
        assertEquals(ReportChannel.TRACE, reports.getFirst().channel());
        assertTrue(reports.getFirst().identifiesCaster());
    }

    @Test
    void theTraceCannotTellWhoCastWhenAdultWizardsAreAbout() {
        List<TraceRules.PlannedReport> reports = reports(scene(true, false, 0, 2, 0, false), LUMOS);
        assertEquals(ReportChannel.TRACE, reports.getFirst().channel());
        assertFalse(reports.getFirst().identifiesCaster(),
                "Deathly Hallows: inside wizarding homes the Ministry relies on the parents");
    }

    @Test
    void atHogwartsAChildMayUseMagicAndNoMuggleCanSeeIt() {
        CastScene scene = scene(true, true, 3, 0, 0, false);
        assertFalse(TraceRules.underageBreach(scene));
        assertEquals(Exposure.NONE, TraceRules.exposure(scene, BOMBARDA));
        assertTrue(reports(scene, BOMBARDA).isEmpty());
    }

    @Test
    void anAdultIsNotUnderTheTrace() {
        assertFalse(TraceRules.underageBreach(scene(false, false, 0, 0, 0, false)));
    }

    // ── officials ──

    @Test
    void anOfficialWatchingALegalDuelHasNothingToReport() {
        assertTrue(reports(scene(false, false, 0, 0, 1, false), STUPEFY).isEmpty());
    }

    @Test
    void anOfficialWatchingAnUnforgivableReportsTheCasterByName() {
        List<TraceRules.PlannedReport> reports = reports(scene(false, false, 0, 0, 1, false), CRUCIO);
        assertEquals(1, reports.size());
        assertEquals(ReportChannel.OFFICIAL, reports.getFirst().channel());
        assertTrue(reports.getFirst().identifiesCaster());
    }

    // ── inquiries ──

    @Test
    void onlyGraveUnattributedIncidentsAreInquiredInto() {
        assertFalse(TraceRules.inquiryWarranted(LegalClass.UNRESTRICTED, Exposure.MODERATE));
        assertTrue(TraceRules.inquiryWarranted(LegalClass.RESTRICTED, Exposure.SEVERE));
        assertTrue(TraceRules.inquiryWarranted(LegalClass.UNFORGIVABLE, Exposure.NONE));
    }

    @Test
    void anInquiryNamesSomeoneOnlyWithEnoughWitnesses() {
        assertFalse(TraceRules.inquiryIdentifies(1));
        assertTrue(TraceRules.inquiryIdentifies(TraceRules.WITNESSES_TO_IDENTIFY));
    }

    @Test
    void witnessesSeeFurtherTheMoreVisibleTheMagic() {
        for (int v = 1; v <= SpellLaw.MAX_VISIBILITY; v++) {
            assertTrue(TraceRules.witnessRadius(v) > TraceRules.witnessRadius(v - 1));
        }
    }

    @Test
    void wizardWitnessesAreRecordedNotInformants() {
        CastScene scene = new CastScene(false, false, false, 0, 1, 0, false, List.of(UUID.randomUUID()));
        assertTrue(reports(scene, CRUCIO).isEmpty(), "another player is not the Ministry's informant");
    }
}
