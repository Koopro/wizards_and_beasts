package at.koopro.wizardsandbeasts.education;

import at.koopro.wizardsandbeasts.education.data.ExamSitting;
import at.koopro.wizardsandbeasts.education.data.PlayerEducationData;
import at.koopro.wizardsandbeasts.heritage.data.PlayerProfessionData;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.network.education.EducationViewS2CPayload;
import at.koopro.wizardsandbeasts.owl.OWLExaminationHandler;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLGradeCalculator;
import at.koopro.wizardsandbeasts.owl.OWLGradeRules;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import at.koopro.wizardsandbeasts.owl.Profession;
import at.koopro.wizardsandbeasts.owl.ProfessionEligibility;
import at.koopro.wizardsandbeasts.owl.Qualification;
import at.koopro.wizardsandbeasts.owl.data.PlayerOWLData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds the {@link EducationView} and sends it. The only place the client's picture of a player's schooling is made.
 */
@NullMarked
public final class EducationService {

    /** "What next" lines at most: a nudge, not a to-do list. */
    public static final int MAX_NEXT = 4;

    private EducationService() {}

    public static void sync(ServerPlayer player) {
        EducationViewS2CPayload.send(player, view(player));
    }

    public static EducationView view(ServerPlayer player) {
        boolean enabled = ModuleManager.isEnabled(Module.OWLS);
        PlayerOWLData record = player.getData(ModAttachments.OWL_DATA.get());
        PlayerEducationData education = player.getData(ModAttachments.EDUCATION_DATA.get());
        PlayerProfessionData career = player.getData(ModAttachments.PROFESSION_DATA.get());
        OWLGradeRules.Evidence evidence = OWLGradeCalculator.evidence(player);
        long now = player.level().getGameTime();

        List<EducationView.SubjectLine> subjects = new ArrayList<>();
        for (OWLSubject subject : OWLSubject.values()) {
            OWLGradeRules.Ladder owl = OWLGradeRules.ladder(subject);
            if (owl == null) continue;
            OWLGradeRules.Ladder newt = OWLGradeRules.newtLadder(subject);
            int score = OWLGradeRules.score(Qualification.OWL, subject, evidence);
            int newtScore = newt == null ? 0 : OWLGradeRules.score(Qualification.NEWT, subject, evidence);
            subjects.add(new EducationView.SubjectLine(subject, score, ladder(owl), OWLGradeRules.grade(score, owl),
                    record.grades().get(subject), ExamRules.newtSeat(subject, record.grades()), newtScore, ladder(newt),
                    newt == null ? null : OWLGradeRules.grade(newtScore, newt), record.newtGrades().get(subject),
                    education.lessonsIn(subject), ExamRules.practical(subject).translationKey()));
        }

        EducationView.SittingView sitting = education.sitting().map(EducationService::sittingView).orElse(null);
        List<EducationView.ResultView> results = education.lastResults().stream()
                .map(r -> new EducationView.ResultView(r.level(), r.subject(), r.coursework(), r.result(), r.lifted()))
                .toList();

        Map<OWLSubject, OWLGrade> projected = OWLGradeRules.gradeAll(Qualification.OWL, evidence);
        List<Component> next = enabled
                ? nextSteps(record, projected, career.profession(), ProfessionEligibility.isGoblin(player),
                education.sitting().orElse(null), now, OWLExaminationHandler.careerOpen(record, career))
                : List.of();

        return new EducationView(enabled, record.sittings(), record.newtSittings(),
                ExamRules.nextSittingTick(Qualification.OWL, record), ExamRules.nextSittingTick(Qualification.NEWT, record),
                enabled && education.sitting().isEmpty() && ExamRules.maySit(Qualification.OWL, record, now),
                enabled && education.sitting().isEmpty() && ExamRules.maySit(Qualification.NEWT, record, now),
                OWLExaminationHandler.careerOpen(record, career), subjects, sitting, results, next);
    }

    private static int[] ladder(OWLGradeRules.@Nullable Ladder ladder) {
        return ladder == null ? new int[] {0, 0, 0} : new int[] {ladder.a(), ladder.e(), ladder.o()};
    }

    private static EducationView.SittingView sittingView(ExamSitting sitting) {
        List<EducationView.PaperLine> papers = new ArrayList<>();
        for (OWLSubject subject : sitting.papers()) {
            boolean written = ExamRules.practical(subject) == ExamRules.Practical.WRITTEN;
            boolean done = written ? sitting.answered() : sitting.practicals().contains(subject);
            papers.add(new EducationView.PaperLine(subject, ExamRules.practical(subject).translationKey(), written, done));
        }
        List<EducationView.QuestionLine> questions = sitting.questions().stream()
                .map(q -> new EducationView.QuestionLine(q.subject(),
                        q.arg().isEmpty() ? Component.translatable(q.question())
                                : Component.translatable(q.question(), Component.translatable(q.arg())),
                        q.options().stream().map(o -> (Component) Component.translatable(o)).toList()))
                .toList();
        return new EducationView.SittingView(sitting.level(), sitting.deadlineTick(), papers, questions, sitting.answered());
    }

    /**
     * What a candidate could do next, most pressing first. Pure over its arguments.
     *
     * @param projected the O.W.L. grades the coursework earns now
     */
    public static List<Component> nextSteps(PlayerOWLData record, Map<OWLSubject, OWLGrade> projected,
                                            @Nullable Profession career, boolean goblin, @Nullable ExamSitting sitting,
                                            long now, boolean careerOpen) {
        List<Component> out = new ArrayList<>();
        if (sitting != null) {
            out.add(Component.translatable("education.next.sitting", Component.translatable(sitting.level().translationKey())));
        }
        if (!record.examTaken()) {
            out.add(Component.translatable("education.next.first_owl"));
        } else {
            int better = 0;
            for (Map.Entry<OWLSubject, OWLGrade> e : projected.entrySet()) {
                OWLGrade held = record.grades().get(e.getKey());
                if (held == null || e.getValue().value > held.value) better++;
            }
            if (better > 0) {
                out.add(ExamRules.maySit(Qualification.OWL, record, now)
                        ? Component.translatable("education.next.resit_owl", better)
                        : Component.translatable("education.next.resit_owl_later", better));
            }
            List<OWLSubject> seats = ExamRules.papers(Qualification.NEWT, record);
            if (!seats.isEmpty() && ExamRules.maySit(Qualification.NEWT, record, now)) {
                out.add(Component.translatable(record.newtSittings() == 0 ? "education.next.first_newt"
                        : "education.next.resit_newt", seats.size()));
            }
            for (OWLSubject subject : OWLSubject.values()) {
                OWLGrade held = record.grades().get(subject);
                if (OWLGradeRules.newtLadder(subject) != null && held != null && held.passing
                        && held.value < ExamRules.NEWT_SEAT.value) {
                    out.add(Component.translatable("education.next.seat", Component.translatable(subject.translationKey()),
                            Component.translatable(ExamRules.NEWT_SEAT.translationKey())));
                    break;
                }
            }
            if (careerOpen) {
                out.add(Component.translatable("education.next.choose_career"));
            }
        }
        ProfessionEligibility.Requirement target = null;
        Profession targetCareer = null;
        int fewest = Integer.MAX_VALUE;
        for (Profession profession : Profession.values()) {
            if (!profession.offered() || profession == career || profession == Profession.PROFESSOR) continue;
            List<ProfessionEligibility.Requirement> unmet =
                    ProfessionEligibility.unmet(profession, record.grades(), record.newtGrades(), goblin);
            // Prefer the career nearest to hand, and among equals one that asks for N.E.W.T.s (the next qualification).
            int weight = unmet.size() * 2 - (ProfessionEligibility.asksForNewts(profession, goblin) ? 1 : 0);
            if (!unmet.isEmpty() && weight < fewest) {
                fewest = weight;
                target = unmet.getFirst();
                targetCareer = profession;
            }
        }
        if (target != null && record.examTaken()) {
            out.add(Component.translatable("education.next.career", Component.translatable(targetCareer.translationKey()),
                    Component.translatable(target.level().translationKey()),
                    Component.translatable(target.subject().translationKey()),
                    Component.translatable(target.minGrade().translationKey())));
        }
        return out.size() > MAX_NEXT ? List.copyOf(out.subList(0, MAX_NEXT)) : List.copyOf(out);
    }
}
