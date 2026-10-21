package at.koopro.wizardsandbeasts.owl.career;

import at.koopro.wizardsandbeasts.heritage.data.PlayerProfessionData;
import at.koopro.wizardsandbeasts.ministry.licence.LicenseType;
import at.koopro.wizardsandbeasts.owl.OWLExaminationHandler;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import at.koopro.wizardsandbeasts.owl.Profession;
import at.koopro.wizardsandbeasts.owl.data.PlayerOWLData;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Careers as jobs: paid for their work once a day, carrying their papers, saved compatibly. */
class CareerProfileTest {

    @Test
    void everyOfferedCareerPaysAndEveryKindOfWorkHasACareer() {
        Set<CareerWork.Kind> paid = EnumSet.noneOf(CareerWork.Kind.class);
        for (Profession profession : Profession.values()) {
            CareerProfile profile = CareerProfile.of(profession);
            if (profession.offered()) {
                assertTrue(profile.dailyWage() > 0, profession + " pays nothing");
                // A wage above a wand a day would make the wand price meaningless.
                assertTrue(profile.dailyWage() < 3451, profession + " pays more than a wand a day");
                paid.add(profile.work());
            }
        }
        assertEquals(EnumSet.allOf(CareerWork.Kind.class), paid, "a kind of work no career is paid for");
    }

    @Test
    void aDayIsPaidOnceAndOnlyForTheCareersOwnWork() {
        assertTrue(CareerWork.pays(Profession.AUROR, CareerWork.Kind.DUELLING, 10, 9));
        assertFalse(CareerWork.pays(Profession.AUROR, CareerWork.Kind.DUELLING, 10, 10), "paid twice in a day");
        assertFalse(CareerWork.pays(Profession.AUROR, CareerWork.Kind.BREWING, 10, 9), "paid for someone else's job");
        assertFalse(CareerWork.pays(null, CareerWork.Kind.DUELLING, 10, -1), "paid with no career");
        assertFalse(CareerWork.pays(Profession.ARITHMANCER, CareerWork.Kind.CHARMWORK, 10, -1), "paid an unoffered career");
        assertTrue(CareerWork.pays(Profession.HEALER, CareerWork.Kind.BREWING, 0, -1), "the first day of a save is unpaid");
    }

    @Test
    void passesEarnTheirLicencesAndCareersAddTheirOwn() {
        Map<OWLSubject, OWLGrade> grades = new EnumMap<>(OWLSubject.class);
        grades.put(OWLSubject.HISTORY_OF_MAGIC, OWLGrade.A);
        grades.put(OWLSubject.POTIONS, OWLGrade.P); // a fail earns nothing
        assertEquals(EnumSet.of(LicenseType.MINISTRY_ACCESS), CareerProfile.papersFor(grades, null));

        Set<LicenseType> auror = CareerProfile.papersFor(grades, Profession.AUROR);
        assertTrue(auror.contains(LicenseType.AUROR_TRAINEE));

        grades.put(OWLSubject.CHARMS, OWLGrade.O);
        grades.put(OWLSubject.TRANSFIGURATION, OWLGrade.O);
        Set<LicenseType> all = CareerProfile.papersFor(grades, null);
        assertFalse(all.contains(LicenseType.APPARITION), "Apparition has its own test");
        assertFalse(all.contains(LicenseType.ANIMAGUS), "Animagus has its own register");
        assertFalse(all.contains(LicenseType.AUROR_TRAINEE), "the Auror warrant comes only with the career");
    }

    @Test
    void anOldSaveLoadsAsOneSittingThatMayBeSatAgainNow() {
        PlayerOWLData owl = PlayerOWLData.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"grades\":{\"CHARMS\":\"O\"},\"examTaken\":true}")).getOrThrow();
        assertEquals(1, owl.sittings());
        assertEquals(0L, owl.lastSatTick());
        assertTrue(OWLExaminationHandler.maySit(owl, 1L), "an old sheet marked by the old rules may be resat");

        PlayerProfessionData career = PlayerProfessionData.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"profession\":\"AUROR\"}")).getOrThrow();
        assertEquals(Profession.AUROR, career.profession());
        assertEquals(-1L, career.lastPaidDay());
        assertTrue(OWLExaminationHandler.careerOpen(owl, career), "an old career may be chosen again once");

        PlayerOWLData fresh = PlayerOWLData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow();
        assertEquals(0, fresh.sittings());
        assertFalse(OWLExaminationHandler.careerOpen(fresh, PlayerProfessionData.DEFAULT), "no career before an exam");
    }

    @Test
    void aResitWaitsAWeekAndReopensTheCareer() {
        PlayerOWLData sat = PlayerOWLData.DEFAULT.afterSitting(Map.of(), 1_000L);
        assertFalse(OWLExaminationHandler.maySit(sat, 1_000L + OWLExaminationHandler.RESIT_INTERVAL_TICKS - 1));
        assertTrue(OWLExaminationHandler.maySit(sat, 1_000L + OWLExaminationHandler.RESIT_INTERVAL_TICKS));

        PlayerProfessionData chosen = PlayerProfessionData.chosen(Profession.HEALER, sat.sittings());
        assertFalse(OWLExaminationHandler.careerOpen(sat, chosen), "career open again without a sitting");
        PlayerOWLData resat = sat.afterSitting(Map.of(), 1_000L + OWLExaminationHandler.RESIT_INTERVAL_TICKS);
        assertTrue(OWLExaminationHandler.careerOpen(resat, chosen), "a resit did not reopen the career");
    }
}
