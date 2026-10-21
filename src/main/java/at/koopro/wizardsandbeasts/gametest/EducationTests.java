package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.brew.Brews;
import at.koopro.wizardsandbeasts.education.EducationService;
import at.koopro.wizardsandbeasts.education.EducationView;
import at.koopro.wizardsandbeasts.education.ExamRules;
import at.koopro.wizardsandbeasts.education.ExamSittings;
import at.koopro.wizardsandbeasts.education.QualificationGates;
import at.koopro.wizardsandbeasts.education.data.ExamSitting;
import at.koopro.wizardsandbeasts.education.data.PlayerEducationData;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.item.brew.BrewItem;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.module.ModuleState;
import at.koopro.wizardsandbeasts.owl.OWLExaminationHandler;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLGradeCalculator;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import at.koopro.wizardsandbeasts.owl.Profession;
import at.koopro.wizardsandbeasts.owl.Qualification;
import at.koopro.wizardsandbeasts.owl.career.CareerWork;
import at.koopro.wizardsandbeasts.owl.data.PlayerOWLData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.skill.SkillTrees;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.NullMarked;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.retire;

/**
 * Education end to end, through the real seams: a sitting opened at a desk, practicals credited by
 * {@link CareerWork#record} and a hand-in at the desk, the clock closing an abandoned paper, resits keeping the best
 * paper, N.E.W.T. seats and the elite careers they open, the qualification gates on brews and capstones, two
 * candidates marked apart, and a sitting surviving a relog.
 */
@NullMarked
final class EducationTests {

    private EducationTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("education_sitting_practical_lifts_a_grade",
                "education: a brew handed in and a harvest during the sitting each raise their subject one grade",
                EducationTests::practicalLifts);
        tests.add("education_sitting_closes_when_the_clock_runs_out",
                "education: a sitting left open is marked when its ten minutes are up",
                EducationTests::clockCloses);
        tests.add("education_resit_waits_and_keeps_the_best_paper",
                "education: a resit is refused inside the week, then never lowers a grade",
                EducationTests::resitKeepsBest);
        tests.add("education_newt_seat_and_elite_career",
                "education: a N.E.W.T. needs an O.W.L. E, and an Auror needs N.E.W.T.s on top of O.W.L.s",
                EducationTests::newtGatesEliteCareer);
        tests.add("education_gates_brews_and_capstones",
                "education: Felix Felicis and a capstone wait for the N.E.W.T.; with O.W.L.s off nothing is gated",
                EducationTests::gates);
        tests.add("education_two_candidates_are_marked_apart",
                "education: one candidate's practical never credits another's sitting",
                EducationTests::twoCandidates);
        tests.add("education_sitting_survives_a_relog",
                "education: a sitting and its practicals are saved",
                EducationTests::survivesRelog);
        tests.add("education_lessons_count_toward_the_owl",
                "education: three lessons lift a blank subject to Acceptable and no further",
                EducationTests::lessonsCount);
    }

    // -- helpers ---------------------------------------------------------------------------------------

    private static ServerPlayer candidate(GameTestHelper helper, String name) {
        return candidate(helper, name, UUID.randomUUID());
    }

    private static ServerPlayer candidate(GameTestHelper helper, String name, UUID id) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name, id, GameType.SURVIVAL);
        WizardTestSupport.parkAtOrigin(helper, player);
        HeritageAPI.commit(player, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
        player.setData(ModAttachments.OWL_DATA.get(), PlayerOWLData.DEFAULT);
        player.setData(ModAttachments.EDUCATION_DATA.get(), PlayerEducationData.EMPTY);
        return player;
    }

    private static void desk(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 1), ModBlocks.EXAMINATION_DESK.get());
    }

    private static PlayerOWLData record(ServerPlayer player) {
        return player.getData(ModAttachments.OWL_DATA.get());
    }

    private static Optional<ExamSitting> sitting(ServerPlayer player) {
        return player.getData(ModAttachments.EDUCATION_DATA.get()).sitting();
    }

    private static ItemStack aBrew() {
        return BrewItem.of(Brews.byId("wizards_and_beasts:pepperup_potion"));
    }

    private static void requireOwls(GameTestHelper helper) {
        check(helper, ModuleManager.isEnabled(Module.OWLS), () -> "the O.W.L. module is off in the test world");
    }

    // -- scenarios -------------------------------------------------------------------------------------

    private static void practicalLifts(GameTestHelper helper) {
        requireOwls(helper);
        ServerPlayer player = candidate(helper, "PracticalCandidate");
        try {
            desk(helper);
            // Coursework: Potions A (10 brew points), Herbology A (2 nodes' worth of harvests = 6 Mandrakes).
            var skills = SkillSystemAPI.getSkillData(player);
            skills.addPotionBrewPoints(10);
            for (int i = 0; i < 6; i++) skills.incrementPlantsHarvested();

            check(helper, ExamSittings.begin(player, Qualification.OWL, true) == null, () -> "the sitting did not open");
            check(helper, sitting(player).isPresent(), () -> "no sitting was saved");

            ItemStack brew = aBrew();
            check(helper, ExamSittings.handIn(player, brew), () -> "the desk refused a brew");
            check(helper, brew.isEmpty(), () -> "the brew handed in was not taken");
            check(helper, !ExamSittings.handIn(player, aBrew()), () -> "the Potions practical was credited twice");
            CareerWork.record(player, CareerWork.Kind.HARVEST);
            check(helper, sitting(player).orElseThrow().practicals().contains(OWLSubject.HERBOLOGY),
                    () -> "a harvest during the sitting was not the Herbology practical");

            ExamSittings.finish(player);
            PlayerOWLData owl = record(player);
            check(helper, owl.grades().get(OWLSubject.POTIONS) == OWLGrade.E,
                    () -> "Potions A with its practical marked " + owl.grades().get(OWLSubject.POTIONS));
            check(helper, owl.grades().get(OWLSubject.HERBOLOGY) == OWLGrade.E,
                    () -> "Herbology A with its practical marked " + owl.grades().get(OWLSubject.HERBOLOGY));
            check(helper, owl.grades().get(OWLSubject.CHARMS) == OWLGrade.T,
                    () -> "an untouched subject was marked " + owl.grades().get(OWLSubject.CHARMS));
            check(helper, owl.sittings() == 1 && sitting(player).isEmpty(), () -> "the sitting did not close");
            EducationView view = EducationService.view(player);
            check(helper, view.lastResults().stream().anyMatch(r -> r.subject() == OWLSubject.POTIONS && r.lifted()),
                    () -> "the sheet does not show the lift");
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void clockCloses(GameTestHelper helper) {
        requireOwls(helper);
        ServerPlayer player = candidate(helper, "LateCandidate");
        try {
            desk(helper);
            check(helper, ExamSittings.begin(player, Qualification.OWL, true) == null, () -> "the sitting did not open");
            ExamSittings.tick(player);
            check(helper, sitting(player).isPresent(), () -> "the clock closed a fresh sitting");
            // Wind the deadline back to now: the next second's tick marks it.
            PlayerEducationData data = player.getData(ModAttachments.EDUCATION_DATA.get());
            ExamSitting open = data.sitting().orElseThrow();
            long now = player.level().getGameTime();
            player.setData(ModAttachments.EDUCATION_DATA.get(), data.withSitting(Optional.of(new ExamSitting(
                    open.level(), open.startTick(), now, open.papers(), open.practicals(), open.questions(),
                    open.answers()))));
            check(helper, !ExamSittings.practical(player, OWLSubject.CHARMS), () -> "a practical after the bell counted");
            ExamSittings.tick(player);
            check(helper, sitting(player).isEmpty(), () -> "the clock did not close the sitting");
            check(helper, record(player).sittings() == 1, () -> "the closed sitting was not counted");
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void resitKeepsBest(GameTestHelper helper) {
        requireOwls(helper);
        ServerPlayer player = candidate(helper, "ResitCandidate");
        try {
            desk(helper);
            Map<OWLSubject, OWLGrade> first = new EnumMap<>(OWLSubject.class);
            first.put(OWLSubject.CHARMS, OWLGrade.O);
            long now = player.level().getGameTime();
            player.setData(ModAttachments.OWL_DATA.get(), new PlayerOWLData(first, true, 1, Math.max(1L, now)));
            check(helper, ExamSittings.begin(player, Qualification.OWL, true) == ExamSittings.Refusal.TOO_SOON,
                    () -> "a resit opened inside the week");

            // The week is over (a test world's clock is younger than a week, so the last sitting is written as
            // "long ago", tick 0): the resit opens, and a blank candidate's paper does not cost the Charms O.
            check(helper, !ExamRules.maySit(Qualification.OWL, record(player), now + ExamRules.RESIT_INTERVAL_TICKS - 1)
                    && ExamRules.maySit(Qualification.OWL, record(player), Math.max(1L, now) + ExamRules.RESIT_INTERVAL_TICKS),
                    () -> "the resit wait is not one week");
            player.setData(ModAttachments.OWL_DATA.get(), new PlayerOWLData(first, true, 1, 0L));
            check(helper, ExamSittings.begin(player, Qualification.OWL, true) == null, () -> "the resit did not open");
            check(helper, ExamSittings.begin(player, Qualification.OWL, true) == ExamSittings.Refusal.IN_PROGRESS,
                    () -> "a second sitting opened over the first");
            ExamSittings.finish(player);
            check(helper, record(player).grades().get(OWLSubject.CHARMS) == OWLGrade.O,
                    () -> "the resit lost the Charms O: " + record(player).grades().get(OWLSubject.CHARMS));
            check(helper, record(player).sittings() == 2, () -> "sittings " + record(player).sittings());
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void newtGatesEliteCareer(GameTestHelper helper) {
        requireOwls(helper);
        ServerPlayer player = candidate(helper, "AurorCandidate");
        try {
            desk(helper);
            Map<OWLSubject, OWLGrade> owls = new EnumMap<>(OWLSubject.class);
            owls.put(OWLSubject.DEFENCE_AGAINST_DARK_ARTS, OWLGrade.A);
            player.setData(ModAttachments.OWL_DATA.get(), new PlayerOWLData(owls, true, 1, 1L));
            check(helper, ExamSittings.begin(player, Qualification.NEWT, true) == ExamSittings.Refusal.NO_SEAT,
                    () -> "a N.E.W.T. opened on an Acceptable");

            owls.put(OWLSubject.DEFENCE_AGAINST_DARK_ARTS, OWLGrade.E);
            owls.put(OWLSubject.CHARMS, OWLGrade.E);
            owls.put(OWLSubject.POTIONS, OWLGrade.A);
            player.setData(ModAttachments.OWL_DATA.get(), new PlayerOWLData(owls, true, 1, 1L));
            OWLExaminationHandler.chooseProfession(player, Profession.AUROR);
            check(helper, player.getData(ModAttachments.PROFESSION_DATA.get()).profession() == null,
                    () -> "an Auror was appointed on O.W.L.s alone");

            check(helper, ExamSittings.begin(player, Qualification.NEWT, true) == null, () -> "the N.E.W.T. did not open");
            ExamSitting newt = sitting(player).orElseThrow();
            check(helper, newt.papers().equals(List.of(OWLSubject.CHARMS, OWLSubject.DEFENCE_AGAINST_DARK_ARTS)),
                    () -> "the N.E.W.T. set " + newt.papers());
            ExamSittings.finish(player);
            check(helper, record(player).newtSittings() == 1 && record(player).sittings() == 1,
                    () -> "the N.E.W.T. sitting was counted as " + record(player));

            Map<OWLSubject, OWLGrade> newts = new EnumMap<>(OWLSubject.class);
            newts.put(OWLSubject.DEFENCE_AGAINST_DARK_ARTS, OWLGrade.E);
            newts.put(OWLSubject.CHARMS, OWLGrade.A);
            player.setData(ModAttachments.OWL_DATA.get(), record(player).withNewtGrades(newts));
            OWLExaminationHandler.chooseProfession(player, Profession.AUROR);
            check(helper, player.getData(ModAttachments.PROFESSION_DATA.get()).profession() == Profession.AUROR,
                    () -> "a qualified Auror was refused");
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void gates(GameTestHelper helper) {
        requireOwls(helper);
        ServerPlayer player = candidate(helper, "GatedCandidate");
        try {
            String felix = "wizards_and_beasts:felix_felicis";
            check(helper, QualificationGates.firstUnmet(player, QualificationGates.Kind.BREW, felix) != null,
                    () -> "Felix Felicis is open without a N.E.W.T. (did the gate data load?)");
            check(helper, QualificationGates.firstUnmet(player, QualificationGates.Kind.BREW,
                    "wizards_and_beasts:pepperup_potion") == null, () -> "an everyday brew is gated");
            check(helper, QualificationGates.firstUnmet(player, QualificationGates.Kind.SKILL_NODE, "healers_hands") != null,
                    () -> "the Healer's capstone is open to an unqualified wizard");
            check(helper, !SkillSystemAPI.evaluateUnlock(player, SkillTrees.byId("healers_hands")).allowed(),
                    () -> "evaluateUnlock allowed a gated capstone");

            WizardTestSupport.grantNewts(player);
            check(helper, QualificationGates.firstUnmet(player, QualificationGates.Kind.BREW, felix) == null,
                    () -> "a N.E.W.T. Potions O did not open Felix Felicis");
            check(helper, QualificationGates.firstUnmet(player, QualificationGates.Kind.SKILL_NODE, "healers_hands") == null,
                    () -> "a N.E.W.T. Herbology O did not open the Healer's capstone gate");

            player.setData(ModAttachments.OWL_DATA.get(), PlayerOWLData.DEFAULT);
            ModuleManager.overrideCached(Module.OWLS, ModuleState.DISABLED);
            try {
                check(helper, QualificationGates.firstUnmet(player, QualificationGates.Kind.BREW, felix) == null,
                        () -> "with O.W.L.s off a brew still asked for one");
            } finally {
                ModuleManager.overrideCached(Module.OWLS, ModuleState.ENABLED);
            }
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void twoCandidates(GameTestHelper helper) {
        requireOwls(helper);
        ServerPlayer first = candidate(helper, "FirstCandidate");
        ServerPlayer second = candidate(helper, "SecondCandidate");
        try {
            desk(helper);
            check(helper, ExamSittings.begin(first, Qualification.OWL, true) == null, () -> "first did not open");
            check(helper, ExamSittings.begin(second, Qualification.OWL, true) == null, () -> "second did not open");
            CareerWork.record(first, CareerWork.Kind.CHARMWORK);
            check(helper, sitting(first).orElseThrow().practicals().contains(OWLSubject.CHARMS),
                    () -> "the first candidate's charm was not credited");
            check(helper, sitting(second).orElseThrow().practicals().isEmpty(),
                    () -> "one candidate's charm credited another's sitting");
            ExamSittings.finish(first);
            check(helper, sitting(second).isPresent() && record(second).sittings() == 0,
                    () -> "marking one candidate closed the other's paper");
            helper.succeed();
        } finally {
            retire(helper, first);
            retire(helper, second);
        }
    }

    private static void survivesRelog(GameTestHelper helper) {
        requireOwls(helper);
        UUID id = UUID.randomUUID();
        ServerPlayer before = candidate(helper, "RelogCandidate", id);
        desk(helper);
        check(helper, ExamSittings.begin(before, Qualification.OWL, true) == null, () -> "the sitting did not open");
        CareerWork.record(before, CareerWork.Kind.DUELLING);
        retire(helper, before);

        ServerPlayer after = WizardTestSupport.placeMockPlayer(helper, "RelogCandidate", id, GameType.SURVIVAL);
        try {
            ExamSitting loaded = sitting(after).orElse(null);
            check(helper, loaded != null, () -> "the sitting was lost over a relog");
            check(helper, loaded.practicals().contains(OWLSubject.DEFENCE_AGAINST_DARK_ARTS),
                    () -> "the practical done before the relog was lost");
            helper.succeed();
        } finally {
            retire(helper, after);
        }
    }

    private static void lessonsCount(GameTestHelper helper) {
        requireOwls(helper);
        ServerPlayer player = candidate(helper, "LessonCandidate");
        try {
            PlayerEducationData data = PlayerEducationData.EMPTY;
            for (int day = 0; day < 5; day++) data = data.withLesson(OWLSubject.POTIONS, day);
            player.setData(ModAttachments.EDUCATION_DATA.get(), data);
            OWLGrade potions = OWLGradeCalculator.calculateAll(player).get(OWLSubject.POTIONS);
            check(helper, potions == OWLGrade.A, () -> "five lessons marked Potions " + potions);
            OWLGrade newt = OWLGradeCalculator.calculateAll(player, Qualification.NEWT).get(OWLSubject.POTIONS);
            check(helper, newt == OWLGrade.T, () -> "lessons counted at N.E.W.T.: " + newt);
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }
}
