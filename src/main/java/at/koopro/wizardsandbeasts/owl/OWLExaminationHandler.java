package at.koopro.wizardsandbeasts.owl;

import at.koopro.wizardsandbeasts.education.ExamRules;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.heritage.data.PlayerProfessionData;
import at.koopro.wizardsandbeasts.network.owl.OWLDataSyncPayload;
import at.koopro.wizardsandbeasts.network.owl.ProfessionSyncPayload;
import at.koopro.wizardsandbeasts.owl.career.CareerPapers;
import at.koopro.wizardsandbeasts.owl.data.PlayerOWLData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NonNull;

import java.util.Map;

/**
 * Marking O.W.L.s outside a sitting, choosing a career, and keeping the client's sheet in step.
 *
 * <p>Candidates sit at the examination desk ({@code education.ExamSittings}): coursework plus a timed practical.
 * {@link #conductExam} is the coursework-only marking an operator's command uses.
 *
 * <p><b>Resits.</b> The exam used to be one sitting for life: a wizard who sat it in their first hour
 * kept a sheet of Trolls forever, and the career it opened (or failed to) was final. Canon has resits
 * too — the N.E.W.T. years exist because the O.W.L. is not the end of an education. A candidate may now
 * sit again once {@link #RESIT_INTERVAL_TICKS} have passed; every subject keeps the better of the two
 * papers ({@link OWLGradeRules#best}), so a resit never costs a grade, and the career choice reopens
 * after each sitting, O.W.L. or N.E.W.T.
 */
public final class OWLExaminationHandler {

    /** Seven in-game days between sittings: long enough that a resit marks a week of real progress. */
    public static final long RESIT_INTERVAL_TICKS = ExamRules.RESIT_INTERVAL_TICKS;

    private OWLExaminationHandler() {}

    /** Game time from which this record may sit the O.W.L. again; 0 when it may sit now. */
    public static long nextSittingTick(@NonNull PlayerOWLData data) {
        return ExamRules.nextSittingTick(Qualification.OWL, data);
    }

    public static boolean maySit(@NonNull PlayerOWLData data, long now) {
        return ExamRules.maySit(Qualification.OWL, data, now);
    }

    /** Every sitting at either level: a N.E.W.T. reopens the career choice as an O.W.L. resit does. */
    public static int totalSittings(@NonNull PlayerOWLData owl) {
        return owl.sittings() + owl.newtSittings();
    }

    /** Whether the career choice is open: none chosen, or a sitting since the last choice. */
    public static boolean careerOpen(@NonNull PlayerOWLData owl, @NonNull PlayerProfessionData career) {
        return owl.examTaken() && (career.profession() == null || career.chosenAtSitting() < totalSittings(owl));
    }

    /** Marks the O.W.L. on coursework alone, as a sitting with no practicals would. */
    public static @NonNull OWLExamResult conductExam(@NonNull ServerPlayer player) {
        PlayerOWLData previous = player.getData(ModAttachments.OWL_DATA.get());
        Map<OWLSubject, OWLGrade> merged = OWLGradeRules.best(previous.grades(),
                OWLGradeCalculator.calculateAll(player));
        player.setData(ModAttachments.OWL_DATA.get(),
                previous.afterSitting(merged, player.level().getGameTime()));
        syncToPlayer(player);
        CareerPapers.issue(player, merged, player.getData(ModAttachments.PROFESSION_DATA.get()).profession());
        return new OWLExamResult(merged, ProfessionEligibility.getEligible(player, merged));
    }

    public static void chooseProfession(@NonNull ServerPlayer player, @NonNull Profession profession) {
        // The packet carries only a choice; the module is checked here, where the choice is honoured.
        if (!at.koopro.wizardsandbeasts.module.ModuleManager.isEnabled(at.koopro.wizardsandbeasts.module.Module.OWLS)) return;
        PlayerOWLData owlData = player.getData(ModAttachments.OWL_DATA.get());
        PlayerProfessionData existing = player.getData(ModAttachments.PROFESSION_DATA.get());
        if (!careerOpen(owlData, existing)) return;
        if (!ProfessionEligibility.isEligible(player, profession, owlData.grades())) return;

        // The pay record survives a change of career, so switching jobs cannot draw two wages in one day.
        player.setData(ModAttachments.PROFESSION_DATA.get(),
                new PlayerProfessionData(profession, totalSittings(owlData), existing.lastPaidDay()));
        syncToPlayer(player);
        PlayerFeedback.toast(player, NoticeKind.UNLOCK,
                Component.translatable("owls.career.chosen.title", Component.translatable(profession.translationKey())),
                Component.translatable("owls.career.chosen.body",
                        Component.translatable(at.koopro.wizardsandbeasts.owl.career.CareerProfile.of(profession).workKey())));
        CareerPapers.issue(player, owlData.grades(), profession);
        at.koopro.wizardsandbeasts.owl.career.track.CareerProgression.onCareerChosen(player);
        at.koopro.wizardsandbeasts.education.EducationService.sync(player);
    }

    public static void resetExam(@NonNull ServerPlayer player) {
        player.setData(ModAttachments.OWL_DATA.get(), PlayerOWLData.DEFAULT);
        player.setData(ModAttachments.PROFESSION_DATA.get(), PlayerProfessionData.DEFAULT);
        player.setData(ModAttachments.EDUCATION_DATA.get(), player.getData(ModAttachments.EDUCATION_DATA.get())
                .marked(java.util.List.of()));
        syncToPlayer(player);
        at.koopro.wizardsandbeasts.owl.career.track.CareerProgression.sync(player);
        at.koopro.wizardsandbeasts.education.EducationService.sync(player);
    }

    public static void syncToPlayer(@NonNull ServerPlayer player) {
        PlayerOWLData owlData = player.getData(ModAttachments.OWL_DATA.get());
        PlayerProfessionData profData = player.getData(ModAttachments.PROFESSION_DATA.get());
        PacketDistributor.sendToPlayer(player, new OWLDataSyncPayload(owlData.grades(), owlData.examTaken(),
                nextSittingTick(owlData), careerOpen(owlData, profData), owlData.newtGrades(),
                ProfessionEligibility.isGoblin(player)));
        PacketDistributor.sendToPlayer(player, new ProfessionSyncPayload(profData.profession()));
    }
}
